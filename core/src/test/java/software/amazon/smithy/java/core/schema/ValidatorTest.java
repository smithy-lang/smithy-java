/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import static java.nio.ByteBuffer.wrap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.data.Percentage.withPercentage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.testmodels.Person;
import software.amazon.smithy.java.core.testmodels.PojoWithValidatedCollection;
import software.amazon.smithy.java.core.testmodels.UnvalidatedPojo;
import software.amazon.smithy.java.core.testmodels.ValidatedPojo;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

public class ValidatorTest {

    @Test
    public void storesErrors() {
        Schema schema = Schema.createString(
                ShapeId.from("smithy.example#Str"),
                LengthTrait.builder().max(1L).build(),
                new PatternTrait("[0-9]+"));
        var struct = getOuterStruct(schema);
        Validator validator = Validator.builder().maxDepth(1).maxAllowedErrors(2).build();
        var errors = validator.validate(createStruct(ser -> ser.writeString(getMember(struct, 0), "Hiii"), struct));

        assertThat(errors).hasSize(2);
    }

    private static Schema getMember(Schema schema) {
        return getMember(schema, 0);
    }

    private static Schema getMember(Schema schema, int index) {
        return schema.member("member" + index);
    }

    @Test
    public void stopsWhenTooManyErrors() {
        Schema schema = Schema.createString(
                ShapeId.from("smithy.example#Str"),
                LengthTrait.builder().max(1L).build(),
                new PatternTrait("[0-9]+"));
        var struct = getOuterStruct(schema);
        Validator validator = Validator.builder().maxDepth(1).maxAllowedErrors(1).build();

        var errors = validator.validate(createStruct(ser -> ser.writeString(getMember(struct, 0), "Hiii"), schema));

        assertThat(errors)
                .hasSize(1); // stops validating after the first error.
    }

    @Test
    public void stopsValidatingWhenMaxErrorsReached() {
        Validator validator = Validator.builder().maxAllowedErrors(2).build();
        Schema schema = Schema.createByte(
                ShapeId.from("smithy.example#E"),
                RangeTrait.builder().min(BigDecimal.valueOf(2)).build());
        var struct = getOuterStruct(schema, schema, schema);

        var errors = validator.validate(createStruct(encoder -> {
            encoder.writeByte(getMember(struct, 0), (byte) 1);
            encoder.writeByte(getMember(struct, 1), (byte) 1);
            encoder.writeByte(getMember(struct, 2), (byte) 1);
        }, struct));
        assertThat(errors)
                .hasSize(2);

        for (int i = 0; i < errors.size(); i++) {
            var error = errors.get(i);
            assertThat(error.path()).isEqualTo("/member" + i);
            assertThat(error.message()).isEqualTo("Value must be greater than or equal to 2");
        }
    }

    @Test
    public void detectsTooDeepRecursion() {
        var schemas = createListSchemas(4);
        Validator validator = Validator.builder().maxDepth(3).build();
        var struct = getOuterStruct(schemas.get(0));

        var errors = validator.validate(createStruct(s1 -> {
            s1.writeList(getMember(struct, 0), null, 1, (v2, s2) -> {
                s2.writeList(schemas.get(1), null, 1, (v3, s3) -> {
                    s3.writeList(schemas.get(2), null, 1, (v4, s4) -> {
                        s4.writeList(schemas.get(3), null, 1, (v5, s5) -> {
                            s5.writeString(PreludeSchemas.STRING, "Hi");
                        });
                    });
                });
            });
        }, struct));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).path()).isEqualTo("/member0/0/0");
        assertThat(errors.get(0).message()).isEqualTo("Value is too deeply nested");
    }

    private List<Schema> createListSchemas(int depth) {
        return createListSchemas(depth, null);
    }

    private List<Schema> createListSchemas(int depth, Schema finalSchema) {
        List<Schema> schemas = new ArrayList<>(depth);
        for (int i = depth; i > 0; i--) {
            if (i == depth) {
                var schema = finalSchema != null ? finalSchema : PreludeSchemas.STRING;
                schemas.add(
                        Schema.listBuilder(ShapeId.from("s#L" + depth))
                                .putMember("member", schema)
                                .build());
            } else {
                schemas.add(
                        Schema.listBuilder(ShapeId.from("s#L3"))
                                .putMember("member", schemas.get(schemas.size() - 1))
                                .build());
            }
        }
        return schemas;
    }

    @Test
    public void resizesPathArray() {
        var schemas = createListSchemas(
                10,
                Schema.createString(
                        ShapeId.from("smithy.example#Str"),
                        LengthTrait.builder().max(1L).build()));
        Validator validator = Validator.builder().maxDepth(25).build();
        var struct = getOuterStruct(schemas.get(0));

        var errors = validator.validate(createStruct(s1 -> {
            s1.writeList(getMember(struct, 0), null, 1, (v2, s2) -> {
                s2.writeList(schemas.get(1), null, 1, (v3, s3) -> {
                    s3.writeList(schemas.get(2), null, 1, (v4, s4) -> {
                        s4.writeList(schemas.get(3), null, 1, (v5, s5) -> {
                            s5.writeList(schemas.get(4), null, 1, (v6, s6) -> {
                                s6.writeList(schemas.get(5), null, 1, (v7, s7) -> {
                                    s7.writeList(schemas.get(6), null, 1, (v8, s8) -> {
                                        s8.writeList(schemas.get(7), null, 1, (v9, s9) -> {
                                            s9.writeList(schemas.get(8), null, 1, (v10, s10) -> {
                                                s10.writeList(schemas.get(9), null, 1, (v11, s11) -> {
                                                    s10.writeString(schemas.get(9).member("member"), "Hi");
                                                });
                                            });
                                        });
                                    });
                                });
                            });
                        });
                    });
                });
            });
        }, struct));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).path()).isEqualTo("/member0/0/0/0/0/0/0/0/0/0/0");
    }

    @Test
    public void validatesStringPattern() {
        Validator validator = Validator.builder().build();
        Schema schema = Schema.createString(ShapeId.from("smithy.example#Foo"), new PatternTrait("^[a-z]+$"));
        var struct = getOuterStruct(schema);
        var errors = validator.validate(createStruct(encoder -> {
            encoder.writeString(getMember(struct, 0), "abc123");
        }, struct));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).path()).isEqualTo("/member0");
        assertThat(errors.get(0).message())
                .isEqualTo("Value must satisfy regular expression pattern: ^[a-z]+$");
    }

    // Required member validation

    @Test
    public void validatesRequiredMembersMissingMultiple() {
        var string = PreludeSchemas.STRING;
        Schema struct = Schema.structureBuilder(ShapeId.from("smithy.example#Foo"))
                .putMember("a", string, new RequiredTrait())
                .putMember("b", string, new RequiredTrait())
                .putMember("c", string, new RequiredTrait())
                .putMember("d", string, new RequiredTrait(), new DefaultTrait(Node.from("default")))
                .putMember("e", string, new DefaultTrait(Node.from("default")))
                .putMember("f", string)
                .build();

        Validator validator = Validator.builder().build();

        var errors = validator.validate(createStruct(encoder -> {
            encoder.writeString(struct.member("a"), "hi");;
        }, struct));

        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).path()).isEqualTo("/");
        assertThat(errors.get(0).message()).isEqualTo("Value missing required member: b");
        assertThat(errors.get(1).path()).isEqualTo("/");
        assertThat(errors.get(1).message()).isEqualTo("Value missing required member: c");
    }

    @Test
    public void validatesRequiredMembersMissingSingle() {
        Schema struct = Schema.structureBuilder(ShapeId.from("smithy.example#Foo"))
                .putMember("a", PreludeSchemas.STRING, new RequiredTrait())
                .build();

        Validator validator = Validator.builder().build();
        var errors = validator.validate(createStruct(encoder -> {}, struct));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).path()).isEqualTo("/");
        assertThat(errors.get(0).message()).isEqualTo("Value missing required member: a");
    }

    // Enum and intEnum validation

    @Test
    public void validatesStringEnums() {
        Validator validator = Validator.builder().build();
        Schema enumSchema = Schema.createEnum(ShapeId.from("smithy.example#E"), Set.of("a", "b", "c"));

        var outerStruct = getOuterStruct(enumSchema);

        // Write a value "d" that is not in the allowed set {"a", "b", "c"}.
        var errors = validator.validate(createStruct(ser -> {
            ser.writeString(getMember(outerStruct, 0), "d");
        }, outerStruct));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).path()).isEqualTo("/member0");
        assertThat(errors.get(0).message()).isEqualTo("Value is not an allowed enum string");

        // Write a valid enum value "a".
        errors = validator.validate(createStruct(ser -> {
            ser.writeString(getMember(outerStruct, 0), "a");
        }, outerStruct));
        assertThat(errors).isEmpty();
    }

    @Test
    public void validatesIntEnums() {
        Validator validator = Validator.builder().build();
        Schema intEnumSchema = Schema.createIntEnum(ShapeId.from("smithy.example#E"), Set.of(1, 2, 3));

        var outerStruct = getOuterStruct(intEnumSchema);

        // Valid integer enum: 1
        var errors = validator.validate(createStruct(ser -> {
            ser.writeInteger(getMember(outerStruct, 0), 1);
        }, outerStruct));
        assertThat(errors).isEmpty();

        // Wrong type: writing a long for an intEnum.
        errors = validator.validate(createStruct(ser -> {
            ser.writeLong(getMember(outerStruct, 0), 2L);
        }, outerStruct));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).startsWith("Value must be intEnum, but found ");

        // Out of range: -1
        errors = validator.validate(createStruct(ser -> {
            ser.writeInteger(getMember(outerStruct, 0), -1);
        }, outerStruct));
        assertThat(errors).hasSize(1);

        // Out of range: 4
        errors = validator.validate(createStruct(ser -> {
            ser.writeInteger(getMember(outerStruct, 0), 4);
        }, outerStruct));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).isEqualTo("Value is not an allowed integer enum number");
    }

    // Nested collection validation

    @Test
    public void validatesLists() {
        Validator validator = Validator.builder().build();

        // The list's member must have length >= 3.
        var memberTarget = Schema.createString(ShapeId.from("s#S"), LengthTrait.builder().min(3L).build());
        var listSchema = Schema.listBuilder(ShapeId.from("s#L"))
                .putMember("member", memberTarget)
                .build();

        var outerStruct = getOuterStruct(listSchema);

        var errors = validator.validate(createStruct(ser -> {
            // Write a list at member0 with 3 elements, two of which are invalid.
            var memberSchema = listSchema.member("member");
            ser.writeList(getMember(outerStruct, 0), new ArrayList<>(), 3, (member, ls) -> {
                ls.writeString(memberSchema, "n"); // too short => length 1
                ls.writeString(memberSchema, "no"); // too short => length 2
                ls.writeString(memberSchema, "good"); // length 4 => OK
            });
        }, outerStruct));

        assertThat(errors).hasSize(2);

        // Now each path is offset by "/member0"
        assertThat(errors.get(0).path()).isEqualTo("/member0/0");
        assertThat(errors.get(0).message())
                .isEqualTo("Value with length 1 must have length greater than or equal to 3");
        assertThat(errors.get(1).path()).isEqualTo("/member0/1");
        assertThat(errors.get(1).message())
                .isEqualTo("Value with length 2 must have length greater than or equal to 3");
    }

    @Test
    public void validatesMapKeysAndValues() {
        Validator validator = Validator.builder().build();

        // Key must have length >= 3
        var keySchema = Schema.createString(ShapeId.from("s#K"), LengthTrait.builder().min(3L).build());
        // Value must have length >= 2
        var valueSchema = Schema.createString(ShapeId.from("s#Val"), LengthTrait.builder().min(2L).build());
        var mapSchema = Schema.mapBuilder(ShapeId.from("s#M"))
                .putMember("key", keySchema)
                .putMember("value", valueSchema)
                .build();

        var outerStruct = getOuterStruct(mapSchema);

        var errors = validator.validate(createStruct(ser -> {
            // Write a map at member0 with 4 entries
            ser.writeMap(getMember(outerStruct, 0), mapSchema, 4, (mapState, ms) -> {
                // Good key, good value
                ms.writeEntry(mapSchema.member("key"),
                        "fine",
                        mapSchema,
                        (v, vs) -> vs.writeString(valueSchema, "ok"));

                // Key "a" => too short
                ms.writeEntry(mapSchema.member("key"),
                        "a",
                        mapSchema,
                        (v, vs) -> vs.writeString(valueSchema, "too few"));

                // Key "b" => too short
                ms.writeEntry(mapSchema.member("key"),
                        "b",
                        mapSchema,
                        (v, vs) -> vs.writeString(valueSchema, "too few"));

                // Good key "good-key-bad-value", but value "!"
                ms.writeEntry(mapSchema.member("key"),
                        "good-key-bad-value",
                        mapSchema,
                        (v, vs) -> vs.writeString(valueSchema, "!"));
            });
        }, outerStruct));

        assertThat(errors).hasSize(3);
        // Because it's nested in outerStruct, each path is prefixed by "/member0"
        assertThat(errors.get(0).path()).isEqualTo("/member0/key/a");
        assertThat(errors.get(0).message())
                .isEqualTo("Value with length 1 must have length greater than or equal to 3");

        assertThat(errors.get(1).path()).isEqualTo("/member0/key/b");
        assertThat(errors.get(1).message())
                .isEqualTo("Value with length 1 must have length greater than or equal to 3");

        assertThat(errors.get(2).path()).isEqualTo("/member0/good-key-bad-value");
        assertThat(errors.get(2).message())
                .isEqualTo("Value with length 1 must have length greater than or equal to 2");
    }

    // Validation of mocked up types.

    @Test
    public void validatesSimplePojo() {
        var pojo = ValidatedPojo.builder()
                .string("hi")
                .integer(1)
                .boxedInteger(2)
                .build();

        Validator validator = Validator.builder().build();
        var errors = validator.validate(pojo);

        assertThat(errors).isEmpty();
    }

    @Test
    public void validatesUnvalidatedPojo() {
        var pojo = UnvalidatedPojo.builder()
                .string("hi")
                .integer(1)
                .boxedInteger(2)
                .build();

        Validator validator = Validator.builder().build();
        var errors = validator.validate(pojo);

        assertThat(errors).isEmpty();
    }

    @Test
    public void validatesPerson() {
        var person = Person.builder()
                .name("Luka")
                .age(77)
                .birthday(Instant.now())
                .build();

        Validator validator = Validator.builder().build();
        var errors = validator.validate(person);

        assertThat(errors).isEmpty();
    }

    @Test
    public void validatesPojoWithValidatedCollection() {
        var pojoWithValidatedCollection = PojoWithValidatedCollection.builder()
                .list(
                        List.of(
                                ValidatedPojo.builder().string("abc").integer(100).boxedInteger(1).build(),
                                ValidatedPojo.builder().string("123").integer(5).boxedInteger(0).build(),
                                ValidatedPojo.builder().string("1").integer(2).boxedInteger(3).build()))
                .map(
                        Map.of(
                                "a",
                                ValidatedPojo.builder().string("abc").integer(100).boxedInteger(1).build(),
                                "b",
                                ValidatedPojo.builder().string("123").integer(5).boxedInteger(0).build(),
                                "c",
                                ValidatedPojo.builder().string("1").integer(2).boxedInteger(3).build()))
                .build();

        Validator validator = Validator.builder().build();
        var errors = validator.validate(pojoWithValidatedCollection);

        assertThat(errors).isEmpty();
    }

    // Union validation

    private Schema getTestUnionSchema() {
        return Schema.unionBuilder(ShapeId.from("smithy.example#U"))
                .putMember("a", PreludeSchemas.STRING, LengthTrait.builder().max(3L).build())
                .putMember("b", PreludeSchemas.STRING)
                .putMember("c", PreludeSchemas.STRING)
                .build();
    }

    @Test
    public void validatesUnionSetMember() {
        Validator validator = Validator.builder().build();
        var unionSchema = getTestUnionSchema();
        var outerStruct = getOuterStruct(unionSchema);
        var errors = validator.validate(
                createStruct(s -> s.writeStruct(getMember(outerStruct, 0), createStruct((w -> {}), unionSchema)),
                        outerStruct));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).path()).isEqualTo("/member0");
        assertThat(errors.get(0).message()).isEqualTo("No member is set in the union");
    }

    @Test
    public void validatesUnionSetOnlyOneMember() {
        Validator validator = Validator.builder().build();
        var unionSchema = getTestUnionSchema();
        var outerStruct = getOuterStruct(unionSchema);
        var errors = validator.validate(createStruct(s -> {
            s.writeStruct(getMember(outerStruct, 0), createStruct(serializer -> {
                serializer.writeString(unionSchema.member("a"), "hi");
                serializer.writeString(unionSchema.member("b"), "byte");
            }, unionSchema));
        }, outerStruct));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).path()).isEqualTo("/member0/b");
        assertThat(errors.get(0).message()).isEqualTo("Union member conflicts with 'a'");
    }

    @Test
    public void allowsValidUnion() {
        Validator validator = Validator.builder().build();
        var unionSchema = getTestUnionSchema();
        var outerStruct = getOuterStruct(unionSchema);
        var errors = validator.validate(createStruct(s -> {
            s.writeStruct(getMember(outerStruct, 0), createStruct(serializer -> {
                serializer.writeString(unionSchema.member("a"), "ok!");
            }, unionSchema));
        }, outerStruct));

        assertThat(errors).isEmpty();
    }

    @Test
    public void validatesTheContentsOfSetUnionMember() {
        Validator validator = Validator.builder().build();
        var unionSchema = getTestUnionSchema();
        var outerStruct = getOuterStruct(unionSchema);
        var errors = validator.validate(createStruct(s -> {
            s.writeStruct(getMember(outerStruct, 0), createStruct(serializer -> {
                serializer.writeString(unionSchema.member("a"), "this is too long!");
            }, unionSchema));
        }, outerStruct));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).path()).isEqualTo("/member0/a");
        assertThat(
                errors.get(0).message()).isEqualTo("Value with length 17 must have length less than or equal to 3");
    }

    @Test
    public void ignoresNullValuesInUnion() {
        Validator validator = Validator.builder().build();
        var unionSchema = getTestUnionSchema();
        var outerStruct = getOuterStruct(unionSchema);
        var errors = validator.validate(createStruct(s -> {
            s.writeStruct(getMember(outerStruct, 0), createStruct(serializer -> {
                serializer.writeString(unionSchema.member("a"), null); // ignore it
                serializer.writeNull(unionSchema.member("b")); // ignore it
                serializer.writeString(unionSchema.member("c"), "ok"); // it's set, it's the only non-null value.
            }, unionSchema));
        }, outerStruct));

        assertThat(errors).isEmpty();
    }

    // Null value tests.

    // Writing a null value independent of a container shape is simply ignored. The validator doesn't have
    // enough context to know if this is allowed or not.
    @Test
    public void allowsStandaloneNullValue() {
        Validator validator = Validator.builder().build();

        // Outer struct that just holds a single STRING member.
        Schema memberSchema = PreludeSchemas.STRING;
        Schema outerStruct = getOuterStruct(memberSchema);

        var errors = validator.validate(createStruct(s -> {
            s.writeNull(getMember(outerStruct, 0));
        }, outerStruct));

        assertThat(errors).isEmpty();
    }

    @Test
    public void allowsNullInSparseList() {
        Validator validator = Validator.builder().build();

        // Sparse list with a single STRING member.
        Schema listSchema = Schema.listBuilder(ShapeId.from("smithy.api#Test"), new SparseTrait())
                .putMember("member", PreludeSchemas.STRING)
                .build();
        Schema outerStruct = getOuterStruct(listSchema);

        // Write a list of size 2: one valid STRING and one null.
        var errors = validator.validate(createStruct(encoder -> {
            encoder.writeList(getMember(outerStruct, 0), new ArrayList<>(), 2, (member, listEncoder) -> {
                listEncoder.writeString(listSchema.member("member"), "this is fine");
                listEncoder.writeNull(listSchema.member("member")); // Allowed because it's sparse.
            });
        }, outerStruct));

        assertThat(errors).isEmpty();
    }

    @Test
    public void allowsNullInStructures() {
        Validator validator = Validator.builder().build();

        // Create an inner struct with a single STRING member "foo".
        Schema innerStruct = Schema.structureBuilder(ShapeId.from("smithy.example#Test"))
                .putMember("foo", PreludeSchemas.STRING)
                .build();
        // The outer struct references the inner struct in a single member.
        Schema outerStruct = getOuterStruct(innerStruct);

        // Write null to the "foo" member inside the inner struct.
        var errors = validator.validate(createStruct(encoder -> {
            encoder.writeStruct(getMember(outerStruct, 0), createStruct(structEncoder -> {
                structEncoder.writeNull(innerStruct.member("foo"));
            }, innerStruct));
        }, outerStruct));

        assertThat(errors).isEmpty();
    }

    // To write a null in a list, it has to have the sparse trait.
    @Test
    public void doesNotAllowNullValuesInListByDefault() {
        Validator validator = Validator.builder().build();

        // Non-sparse list with a single STRING member.
        Schema listSchema = Schema.listBuilder(ShapeId.from("smithy.api#Test"))
                .putMember("member", PreludeSchemas.STRING)
                .build();
        Schema outerStruct = getOuterStruct(listSchema);

        // Attempt to write a null value in a non-sparse list => should fail.
        var errors = validator.validate(createStruct(encoder -> {
            encoder.writeList(getMember(outerStruct, 0), new ArrayList<>(), 2, (member, listEncoder) -> {
                listEncoder.writeString(listSchema.member("member"), "this is fine");
                listEncoder.writeNull(listSchema.member("member")); // Not allowed
            });
        }, outerStruct));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).path()).isEqualTo("/member0/1");
        assertThat(errors.get(0).message()).isEqualTo("Value is in a list that does not allow null values");
    }

    //
    //    // To write a null in a map, it has to have the sparse trait.
    @Test
    public void doesNotAllowNullValuesInMapsByDefault() {
        Validator validator = Validator.builder().build();

        // Non-sparse map with "key" STRING and "value" STRING.
        Schema mapSchema = Schema.mapBuilder(ShapeId.from("smithy.api#Test"))
                .putMember("key", PreludeSchemas.STRING)
                .putMember("value", PreludeSchemas.STRING)
                .build();
        Schema outerStruct = getOuterStruct(mapSchema);

        // Attempt to write a null value in a non-sparse map => should fail.
        var errors = validator.validate(createStruct(encoder -> {
            encoder.writeMap(getMember(outerStruct, 0), mapSchema, 2, (mapState, mapEncoder) -> {
                // First entry is fine.
                mapEncoder.writeEntry(
                        mapSchema.member("key"),
                        "hi",
                        mapSchema,
                        (entrySchema, entryEncoder) -> entryEncoder.writeString(mapSchema.member("value"), "ok"));
                // Second entry writes null => error.
                mapEncoder.writeEntry(
                        mapSchema.member("key"),
                        "oops",
                        mapSchema,
                        (entrySchema, entryEncoder) -> entryEncoder.writeNull(mapSchema.member("value")));
            });
        }, outerStruct));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).path()).isEqualTo("/member0/oops");
        assertThat(errors.get(0).message()).isEqualTo("Value is in a map that does not allow null values");
    }

    @Test
    public void detectsDuplicateSimpleItems() {
        Validator validator = Validator.builder().build();

        Schema listSchema = Schema.listBuilder(ShapeId.from("smithy.api#Test"), new UniqueItemsTrait())
                .putMember("member", PreludeSchemas.STRING)
                .build();

        Schema outerStruct = getOuterStruct(listSchema);

        List<String> values = List.of("a", "b", "b", "c", "d", "a");

        var errors = validator.validate(createStruct(encoder -> {
            encoder.writeList(
                    getMember(outerStruct, 0),
                    values,
                    values.size(),
                    (ignored, listEncoder) -> {
                        var member = listSchema.member("member");
                        for (String v : values) {
                            listEncoder.writeString(member, v);
                        }
                    });
        }, outerStruct));

        assertThat(errors).hasSize(2);

        assertThat(errors.get(0).getClass())
                .isEqualTo(ValidationError.UniqueItemConflict.class);
        assertThat(errors.get(0).path()).isEqualTo("/member0/2");
        assertThat(errors.get(0).message())
                .isEqualTo("Conflicting list item found at position 2");

        assertThat(errors.get(1).getClass())
                .isEqualTo(ValidationError.UniqueItemConflict.class);
        assertThat(errors.get(1).path()).isEqualTo("/member0/5");
        assertThat(errors.get(1).message())
                .isEqualTo("Conflicting list item found at position 5");
    }

    @Test
    @Disabled("To be removed. This won't pass because the serializer lambdas don't implement equals/hashcode")
    public void detectsDuplicateComplexItems() {
        Validator validator = Validator.builder().build();

        // 1. Create a union schema that we'll store in a list.
        Schema unionSchema = getTestUnionSchema();

        // 2. Create a list schema with the UniqueItemsTrait, referencing our union schema.
        Schema listSchema = Schema.listBuilder(
                ShapeId.from("smithy.api#Test"),
                new UniqueItemsTrait())
                .putMember("member", unionSchema)
                .build();

        // 3. Create an "outer struct" that holds this list in a single member (e.g., /member0).
        Schema outerStruct = getOuterStruct(listSchema);

        // 4. Write four union items to the list. The second and third items are duplicates
        //    (both have `{ b: "hi" }`), which should trigger a unique-items conflict at index 2.
        var errors = validator.validate(createStruct(encoder -> {
            // Write the list into /member0 of the outer struct.
            encoder.writeList(
                    getMember(outerStruct, 0),
                    new ArrayList<>(),
                    4,
                    (ignored, listEncoder) -> {
                        var unionMember = listSchema.member("member");
                        // Item 0: { a: "hi" }
                        listEncoder.writeStruct(unionMember, createStruct(unionEncoder -> {
                            unionEncoder.writeString(unionSchema.member("a"), "hi");
                        }, unionSchema));

                        listEncoder.writeStruct(unionMember, createStruct(unionEncoder -> {
                            unionEncoder.writeString(unionSchema.member("b"), "hi");
                        }, unionSchema));

                        listEncoder.writeStruct(unionMember, createStruct(unionEncoder -> {
                            unionEncoder.writeString(unionSchema.member("c"), "hi");
                        }, unionSchema));
                    });
        }, outerStruct));

        // 5. We expect exactly 1 error (the duplicate at index 2).
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).path()).isEqualTo("/2");
        assertThat(errors.get(0).message())
                .isEqualTo("Conflicting list item found at position 2");
    }

    @ParameterizedTest
    @MethodSource("detectsIncorrectTypeSupplier")
    public void detectsIncorrectType(ShapeType type, Consumer<ShapeSerializer> encoder) {
        Validator validator = Validator.builder().build();
        var outerStruct = getOuterStruct(PreludeSchemas.STRING);
        var errors = validator.validate(createStruct(encoder, outerStruct));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).path()).isEqualTo("/member0");
        assertThat(errors.get(0).message()).startsWith("Value must be ");
        assertThat(errors.get(0).message()).endsWith(", but found " + type);
    }

    public static List<Arguments> detectsIncorrectTypeSupplier() {
        var string = getMember(getOuterStruct(PreludeSchemas.STRING));
        var integer = getMember(getOuterStruct(PreludeSchemas.INTEGER));
        return List.of(
                Arguments.of(
                        ShapeType.BLOB,
                        (Consumer<ShapeSerializer>) serializer -> serializer.writeBlob(
                                integer,
                                wrap("a".getBytes(StandardCharsets.UTF_8)))),
                Arguments.of(
                        ShapeType.STRING,
                        (Consumer<ShapeSerializer>) serializer -> serializer.writeString(integer, "a")),
                Arguments.of(
                        ShapeType.TIMESTAMP,
                        (Consumer<ShapeSerializer>) serializer -> serializer.writeTimestamp(
                                string,
                                Instant.EPOCH)),
                Arguments.of(
                        ShapeType.BOOLEAN,
                        (Consumer<ShapeSerializer>) serializer -> serializer.writeBoolean(string, true)),
                Arguments.of(
                        ShapeType.DOCUMENT,
                        (Consumer<ShapeSerializer>) serializer -> serializer.writeDocument(
                                string,
                                Document.of("hi"))),
                Arguments.of(
                        ShapeType.BYTE,
                        (Consumer<ShapeSerializer>) serializer -> serializer.writeByte(string, (byte) 1)),
                Arguments.of(
                        ShapeType.SHORT,
                        (Consumer<ShapeSerializer>) serializer -> serializer.writeShort(string, (short) 1)),
                Arguments.of(
                        ShapeType.INTEGER,
                        (Consumer<ShapeSerializer>) serializer -> serializer.writeInteger(string, 1)),
                Arguments.of(
                        ShapeType.LONG,
                        (Consumer<ShapeSerializer>) serializer -> serializer.writeLong(string, 1L)),
                Arguments.of(
                        ShapeType.FLOAT,
                        (Consumer<ShapeSerializer>) serializer -> serializer.writeFloat(string, 1f)),
                Arguments.of(
                        ShapeType.DOUBLE,
                        (Consumer<ShapeSerializer>) serializer -> serializer.writeDouble(string, 1.0)),
                Arguments.of(
                        ShapeType.BIG_INTEGER,
                        (Consumer<ShapeSerializer>) serializer -> serializer.writeBigInteger(
                                string,
                                BigInteger.ONE)),
                Arguments.of(
                        ShapeType.BIG_DECIMAL,
                        (Consumer<ShapeSerializer>) serializer -> serializer.writeBigDecimal(
                                string,
                                BigDecimal.ONE)),
                Arguments.of(
                        ShapeType.STRUCTURE,
                        (Consumer<ShapeSerializer>) serializer -> serializer.writeStruct(
                                string,
                                createStruct(s -> {}, PreludeSchemas.STRING))),
                Arguments.of(
                        ShapeType.LIST,
                        (Consumer<ShapeSerializer>) serializer -> serializer.writeList(
                                string,
                                null,
                                0,
                                (v, s) -> {})),
                Arguments.of(
                        ShapeType.MAP,
                        (Consumer<ShapeSerializer>) serializer -> serializer.writeMap(
                                string,
                                null,
                                0,
                                (v, s) -> {})));
    }

    @ParameterizedTest
    @MethodSource("validatesRangeAndLengthSupplier")
    public void validatesRanges(
            Class<? extends ValidationError> error,
            BiFunction<ShapeId, Trait[], Schema> creator,
            BiConsumer<Schema, ShapeSerializer> consumer
    ) {
        var traits = new Trait[] {
                RangeTrait.builder().min(BigDecimal.ONE).max(BigDecimal.TEN).build(),
                LengthTrait
                        .builder()
                        .min(1L)
                        .max(10L)
                        .build()
        };
        Schema schema = creator.apply(ShapeId.from("smithy.example#Number"), traits);

        Validator validator = Validator.builder().build();
        var outerStruct = getOuterStruct(schema);
        var errors = validator.validate(createStruct(e -> consumer.accept(getMember(outerStruct), e), outerStruct));

        if (error == null) {
            assertThat(errors).isEmpty();
        } else if (error.equals(ValidationError.RangeValidationFailure.class)) {
            assertThat(errors.get(0).getClass()).isEqualTo(error);
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).path()).isEqualTo("/member0");
            assertThat(errors.get(0).message()).isEqualTo("Value must be between 1 and 10, inclusive");
        } else if (error.equals(ValidationError.LengthValidationFailure.class)) {
            assertThat(errors.get(0).getClass()).isEqualTo(error);
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).path()).isEqualTo("/member0");
            assertThat(errors.get(0).message()).startsWith("Value with length ");
            assertThat(
                    errors.get(0).message()).endsWith("must have length between 1 and 10, inclusive");
        } else {
            fail("Invalid error type");
        }
    }

    public static List<Arguments> validatesRangeAndLengthSupplier() {
        return List.of(
                Arguments.of(
                        null,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createByte,
                        (BiConsumer<Schema,
                                ShapeSerializer>) (schema, serializer) -> serializer.writeByte(schema, (byte) 1)),
                Arguments.of(
                        null,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createShort,
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeShort(
                                schema,
                                (short) 1)),
                Arguments.of(
                        null,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createInteger,
                        (BiConsumer<Schema,
                                ShapeSerializer>) (schema, serializer) -> serializer.writeInteger(schema, 1)),
                Arguments.of(
                        null,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createLong,
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeLong(schema, 1L)),
                Arguments.of(
                        null,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createFloat,
                        (BiConsumer<Schema,
                                ShapeSerializer>) (schema, serializer) -> serializer.writeFloat(schema, 1f)),
                Arguments.of(
                        null,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createDouble,
                        (BiConsumer<Schema,
                                ShapeSerializer>) (schema, serializer) -> serializer.writeDouble(schema, 1.0)),
                Arguments.of(
                        null,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createBigInteger,
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeBigInteger(
                                schema,
                                BigInteger.ONE)),
                Arguments.of(
                        null,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createBigDecimal,
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeBigDecimal(
                                schema,
                                BigDecimal.ONE)),

                Arguments.of(
                        ValidationError.RangeValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createByte,
                        (BiConsumer<Schema,
                                ShapeSerializer>) (schema, serializer) -> serializer.writeByte(schema, (byte) 0)),
                Arguments.of(
                        ValidationError.RangeValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createShort,
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeShort(
                                schema,
                                (short) 0)),
                Arguments.of(
                        ValidationError.RangeValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createInteger,
                        (BiConsumer<Schema,
                                ShapeSerializer>) (schema, serializer) -> serializer.writeInteger(schema, 0)),
                Arguments.of(
                        ValidationError.RangeValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createLong,
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeLong(schema, 0L)),
                Arguments.of(
                        ValidationError.RangeValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createFloat,
                        (BiConsumer<Schema,
                                ShapeSerializer>) (schema, serializer) -> serializer.writeFloat(schema, 0f)),
                Arguments.of(
                        ValidationError.RangeValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createDouble,
                        (BiConsumer<Schema,
                                ShapeSerializer>) (schema, serializer) -> serializer.writeDouble(schema, 0.0)),
                Arguments.of(
                        ValidationError.RangeValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createBigInteger,
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeBigInteger(
                                schema,
                                BigInteger.ZERO)),
                Arguments.of(
                        ValidationError.RangeValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createBigDecimal,
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeBigDecimal(
                                schema,
                                BigDecimal.ZERO)),

                Arguments.of(
                        ValidationError.RangeValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createByte,
                        (BiConsumer<Schema,
                                ShapeSerializer>) (schema, serializer) -> serializer.writeByte(schema, (byte) 11)),
                Arguments.of(
                        ValidationError.RangeValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createShort,
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeShort(
                                schema,
                                (short) 11)),
                Arguments.of(
                        ValidationError.RangeValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createInteger,
                        (BiConsumer<Schema,
                                ShapeSerializer>) (schema, serializer) -> serializer.writeInteger(schema, 11)),
                Arguments.of(
                        ValidationError.RangeValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createLong,
                        (BiConsumer<Schema,
                                ShapeSerializer>) (schema, serializer) -> serializer.writeLong(schema, 11L)),
                Arguments.of(
                        ValidationError.RangeValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createFloat,
                        (BiConsumer<Schema,
                                ShapeSerializer>) (schema, serializer) -> serializer.writeFloat(schema, 11f)),
                Arguments.of(
                        ValidationError.RangeValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createDouble,
                        (BiConsumer<Schema,
                                ShapeSerializer>) (schema, serializer) -> serializer.writeDouble(schema, 11.0)),
                Arguments.of(
                        ValidationError.RangeValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createBigInteger,
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeBigInteger(
                                schema,
                                BigInteger.valueOf(11))),
                Arguments.of(
                        ValidationError.RangeValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createBigDecimal,
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeBigDecimal(
                                schema,
                                BigDecimal.valueOf(11))),

                Arguments.of(
                        null,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createBlob,
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeBlob(
                                schema,
                                wrap("a".getBytes(StandardCharsets.UTF_8)))),
                Arguments.of(
                        null,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createString,
                        (BiConsumer<Schema,
                                ShapeSerializer>) (schema, serializer) -> serializer.writeString(schema, "a")),
                Arguments.of(
                        null,
                        (BiFunction<ShapeId, Trait[], Schema>) (id, traits) -> {
                            return Schema.listBuilder(id, traits)
                                    .putMember("member", PreludeSchemas.STRING)
                                    .build();
                        },
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeList(
                                schema,
                                null,
                                1,
                                (v, ls) -> ls.writeString(PreludeSchemas.STRING, "a"))),
                Arguments.of(
                        null,
                        (BiFunction<ShapeId, Trait[], Schema>) (id, traits) -> {
                            return Schema.mapBuilder(id, traits)
                                    .putMember("key", PreludeSchemas.STRING)
                                    .putMember("value", PreludeSchemas.STRING)
                                    .build();
                        },
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeMap(
                                schema,
                                null,
                                1,
                                (mapStateValue, mapSerializer) -> mapSerializer.writeEntry(
                                        PreludeSchemas.STRING,
                                        "a",
                                        null,
                                        (mapValueState, mapValueSerializer) -> {
                                            mapValueSerializer.writeString(PreludeSchemas.STRING, "a");
                                        }))),

                Arguments.of(
                        ValidationError.LengthValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createBlob,
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeBlob(
                                schema,
                                wrap("".getBytes(StandardCharsets.UTF_8)))),
                Arguments.of(
                        ValidationError.LengthValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createString,
                        (BiConsumer<Schema,
                                ShapeSerializer>) (schema, serializer) -> serializer.writeString(schema, "")),
                Arguments.of(
                        ValidationError.LengthValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) (id, traits) -> {
                            return Schema.listBuilder(id, traits)
                                    .putMember("member", PreludeSchemas.STRING)
                                    .build();
                        },
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeList(
                                schema,
                                null,
                                0,
                                (v, ls) -> {})),
                Arguments.of(
                        ValidationError.LengthValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) (id, traits) -> {
                            return Schema.mapBuilder(id, traits)
                                    .putMember("key", PreludeSchemas.STRING)
                                    .putMember("value", PreludeSchemas.STRING)
                                    .build();
                        },
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> {
                            serializer.writeMap(schema, null, 0, (mapStateValue, mapSerializer) -> {});
                        }),

                Arguments.of(
                        ValidationError.LengthValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createBlob,
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeBlob(
                                schema,
                                wrap("abcdefghijklmnop".getBytes(StandardCharsets.UTF_8)))),
                Arguments.of(
                        ValidationError.LengthValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) Schema::createString,
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeString(
                                schema,
                                "abcdefghijklmnop")),
                Arguments.of(
                        ValidationError.LengthValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) (id, traits) -> {
                            return Schema.listBuilder(id, traits)
                                    .putMember("member", PreludeSchemas.STRING)
                                    .build();
                        },
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeList(
                                schema,
                                null,
                                11,
                                (v, ls) -> {
                                    for (int i = 0; i < 11; i++) {
                                        ls.writeString(PreludeSchemas.STRING, "a");
                                    }
                                })),
                Arguments.of(
                        ValidationError.LengthValidationFailure.class,
                        (BiFunction<ShapeId, Trait[], Schema>) (id, traits) -> {
                            return Schema.mapBuilder(id, traits)
                                    .putMember("key", PreludeSchemas.STRING)
                                    .putMember("value", PreludeSchemas.STRING)
                                    .build();
                        },
                        (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> {
                            serializer.writeMap(schema, null, 11, (mapState, mapSerializer) -> {
                                for (int i = 0; i < 11; i++) {
                                    mapSerializer.writeEntry(
                                            PreludeSchemas.STRING,
                                            "a" + i,
                                            null,
                                            (mapValueState, mapValueSerializer) -> {
                                                mapValueSerializer.writeString(PreludeSchemas.STRING, "a");
                                            });
                                }
                            });
                        }));
    }

    @Test
    public void rangeErrorTooSmall() {
        var schema = Schema.createFloat(
                ShapeId.from("smithy.example#Number"),
                RangeTrait.builder().min(new BigDecimal("1.2")).build());
        var validator = Validator.builder().build();
        var outerStruct = getOuterStruct(schema);
        var errors = validator.validate(createStruct(e -> e.writeFloat(schema, 1.0f), outerStruct));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).isInstanceOf(ValidationError.RangeValidationFailure.class);
        assertThat(errors.get(0).message()).isEqualTo("Value must be greater than or equal to 1.2");
    }

    @Test
    public void rangeErrorTooBig() {
        var schema = Schema.createFloat(
                ShapeId.from("smithy.example#Number"),
                RangeTrait.builder().max(new BigDecimal("1.2")).build());
        var outerStruct = getOuterStruct(schema);
        var validator = Validator.builder().build();
        var errors = validator.validate(createStruct(
                e -> e.writeFloat(getMember(outerStruct, 0), 1.3f),
                outerStruct));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).isInstanceOf(ValidationError.RangeValidationFailure.class);
        assertThat(errors.get(0).message()).isEqualTo("Value must be less than or equal to 1.2");
    }

    @Test
    public void rangeErrorNotBetween() {
        var schema = Schema.createFloat(
                ShapeId.from("smithy.example#Number"),
                RangeTrait.builder().min(new BigDecimal("1.1")).max(new BigDecimal("1.2")).build());
        var validator = Validator.builder().build();
        var outerStruct = getOuterStruct(schema);
        var errors = validator.validate(createStruct(e -> e.writeFloat(getMember(outerStruct, 0), 1.3f), outerStruct));

        assertThat(errors).hasSize(1);
        var first = errors.get(0);
        assertThat(errors.get(0)).isInstanceOf(ValidationError.RangeValidationFailure.class);

        var error = (ValidationError.RangeValidationFailure) first;
        assertThat(error.value().doubleValue()).isCloseTo(1.3, withPercentage(1));
        assertThat(error.schema().getTrait(TraitKey.get(RangeTrait.class)).getMin()).contains(new BigDecimal("1.1"));
        assertThat(error.schema().getTrait(TraitKey.get(RangeTrait.class)).getMax()).contains(new BigDecimal("1.2"));
        assertThat(error.path()).isEqualTo("/member0");
        assertThat(error.message()).isEqualTo("Value must be between 1.1 and 1.2, inclusive");
    }

    @Test
    public void lengthTooShort() {
        var schema = Schema.listBuilder(ShapeId.from("smithy.api#Test"), LengthTrait.builder().min(2L).build())
                .putMember("member", PreludeSchemas.STRING)
                .build();
        var validator = Validator.builder().build();
        var outerStruct = getOuterStruct(schema);
        var errors = validator.validate(
                createStruct(e -> e.writeList(getMember(outerStruct, 0), null, 0, (v, ser) -> {}), outerStruct));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).isInstanceOf(ValidationError.LengthValidationFailure.class);
        assertThat(
                errors.get(0).message()).isEqualTo("Value with length 0 must have length greater than or equal to 2");
    }

    @Test
    public void lengthTooLong() {
        var schema = Schema.listBuilder(ShapeId.from("smithy.api#Test"), LengthTrait.builder().max(1L).build())
                .putMember("member", PreludeSchemas.STRING)
                .build();
        var validator = Validator.builder().build();
        var outerStruct = getOuterStruct(schema);
        var errors = validator.validate(createStruct(
                e -> e.writeList(getMember(outerStruct, 0), new ArrayList<>(), 2, (list, ser) -> {
                    var member = schema.member("member");
                    ser.writeString(member, "a");
                    ser.writeString(member, "b");
                }),
                outerStruct));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).isInstanceOf(ValidationError.LengthValidationFailure.class);
        assertThat(
                errors.get(0).message()).isEqualTo("Value with length 2 must have length less than or equal to 1");
    }

    @Test
    public void lengthNotBetween() {
        var schema = Schema.listBuilder(ShapeId.from("smithy.api#Test"), LengthTrait.builder().min(1L).max(2L).build())
                .putMember("member", PreludeSchemas.STRING)
                .build();
        var validator = Validator.builder().build();
        var outerStruct = getOuterStruct(schema);
        var errors = validator.validate(createStruct(
                e -> e.writeList(getMember(outerStruct, 0), new ArrayList<String>(), 3, (list, ser) -> {
                    var member = schema.member("member");
                    ser.writeString(member, "a");
                    ser.writeString(member, "b");
                    ser.writeString(member, "c");
                }),
                outerStruct));

        assertThat(errors).hasSize(1);
        var first = errors.get(0);
        assertThat(errors.get(0)).isInstanceOf(ValidationError.LengthValidationFailure.class);

        var error = (ValidationError.LengthValidationFailure) first;
        assertThat(error.length()).isEqualTo(3L);
        assertThat(error.schema().getTrait(TraitKey.get(LengthTrait.class)).getMin()).contains(1L);
        assertThat(error.schema().getTrait(TraitKey.get(LengthTrait.class)).getMax()).contains(2L);
        assertThat(error.path()).isEqualTo("/member0");
        assertThat(error.message()).isEqualTo("Value with length 3 must have length between 1 and 2, inclusive");
    }

    @ParameterizedTest
    @MethodSource("validatesRequiredMembersOfBigStructsProvider")
    public void validatesRequiredMembersOfBigStructs(
            int totalMembers,
            int requiredCount,
            int defaultedCount,
            int failures
    ) {
        var struct = createBigRequiredSchema(totalMembers, requiredCount, defaultedCount);
        Validator validator = Validator.builder().build();
        var outerStruct = getOuterStruct(struct);
        var errors = validator.validate(createStruct(encoder -> {
            encoder.writeStruct(getMember(outerStruct, 0), createStruct(writer -> {}, struct));
        }, outerStruct));

        assertThat(errors).hasSize(failures);

        for (var e : errors) {
            assertThat(e).isInstanceOf(ValidationError.RequiredValidationFailure.class);
        }
    }

    //
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 63, 64, 65, 128})
    public void presenceTrackerType(int requiredFields) {
        Class<?> expected;
        if (requiredFields == 0) {
            expected = PresenceTracker.NoOpPresenceTracker.class;
        } else if (requiredFields <= 64) {
            expected = PresenceTracker.RequiredMemberPresenceTracker.class;
        } else {
            expected = PresenceTracker.BigRequiredMemberPresenceTracker.class;
        }

        var schema = createBigRequiredSchema(requiredFields, requiredFields, 0);
        var tracker = PresenceTracker.of(schema);
        assertEquals(expected, tracker.getClass());

        if (requiredFields > 0) {
            tracker.setMember(schema.members().get(requiredFields - 1));
            assertEquals(requiredFields == 1, tracker.allSet());
            for (int i = 0; i < requiredFields - 1; i++) {
                assertFalse(tracker.checkMember(schema.members().get(i)));
            }

            assertTrue(tracker.checkMember(schema.members().get(requiredFields - 1)));
        }
    }

    static List<Arguments> validatesRequiredMembersOfBigStructsProvider() {
        return Arrays.asList(
                // int totalMembers, int requiredCount, int defaultedCount, int failures
                Arguments.of(100, 100, 0, 100),
                Arguments.of(100, 80, 0, 80),
                Arguments.of(100, 80, 20, 60),
                Arguments.of(100, 100, 100, 0),
                Arguments.of(1000, 10, 0, 10),
                Arguments.of(0, 0, 0, 0),
                Arguments.of(63, 63, 0, 63),
                Arguments.of(64, 64, 0, 64),
                Arguments.of(65, 65, 0, 65));
    }

    static Schema createBigRequiredSchema(int totalMembers, int requiredCount, int defaultedCount) {
        var builder = Schema.structureBuilder(ShapeId.from("smithy.example#Foo"));
        for (var i = 0; i < totalMembers; i++) {
            String name = "member" + i;
            List<Trait> traits = new ArrayList<>();
            if (i < requiredCount) {
                traits.add(new RequiredTrait());
            }
            if (i < defaultedCount) {
                traits.add(new DefaultTrait(Node.from("")));
            }
            builder.putMember(name, PreludeSchemas.STRING, traits.toArray(new Trait[0]));
        }
        return builder.build();
    }

    private static Schema getOuterStruct(Schema... memberSchemas) {
        var builder = Schema.structureBuilder(ShapeId.from("smithy.example#OuterStructure"));
        for (var i = 0; i < memberSchemas.length; i++) {
            builder.putMember("member" + i, memberSchemas[i]);
        }
        return builder.build();
    }

    private static SerializableStruct createStruct(Consumer<ShapeSerializer> consumer, Schema schema) {

        return new SerializableStruct() {
            @Override
            public Schema schema() {
                return schema;
            }

            @Override
            public void serializeMembers(ShapeSerializer serializer) {
                consumer.accept(serializer);
            }

            @Override
            public <T> T getMemberValue(Schema member) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
