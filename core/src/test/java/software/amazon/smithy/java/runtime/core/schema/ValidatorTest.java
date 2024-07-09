/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import static java.nio.ByteBuffer.wrap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.core.testmodels.Person;
import software.amazon.smithy.java.runtime.core.testmodels.PojoWithValidatedCollection;
import software.amazon.smithy.java.runtime.core.testmodels.UnvalidatedPojo;
import software.amazon.smithy.java.runtime.core.testmodels.ValidatedPojo;
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

public class ValidatorTest {

    @Test
    public void storesErrors() {
        Schema schema = Schema.createString(
            ShapeId.from("smithy.example#Str"),
            LengthTrait.builder().max(1L).build(),
            new PatternTrait("[0-9]+")
        );
        Validator validator = Validator.builder().maxDepth(1).maxAllowedErrors(2).build();
        var errors = validator.validate(ser -> ser.writeString(schema, "Hiii"));

        assertThat(errors, hasSize(2));
    }

    @Test
    public void stopsWhenTooManyErrors() {
        Schema schema = Schema.createString(
            ShapeId.from("smithy.example#Str"),
            LengthTrait.builder().max(1L).build(),
            new PatternTrait("[0-9]+")
        );
        Validator validator = Validator.builder().maxDepth(1).maxAllowedErrors(1).build();

        var errors = validator.validate(ser -> ser.writeString(schema, "Hiii"));

        assertThat(errors, hasSize(1)); // stops validating after the first error.
    }

    @Test
    public void stopsValidatingWhenMaxErrorsReached() {
        Validator validator = Validator.builder().maxAllowedErrors(2).build();
        Schema schema = Schema.createByte(
            ShapeId.from("smithy.example#E"),
            RangeTrait.builder().min(BigDecimal.valueOf(2)).build()
        );

        var errors = validator.validate(encoder -> {
            encoder.writeByte(schema, (byte) 1);
            encoder.writeByte(schema, (byte) 1);
            encoder.writeByte(schema, (byte) 1);
        });
        assertThat(errors, hasSize(2));

        for (ValidationError error : errors) {
            assertThat(error.path(), equalTo("/"));
            assertThat(error.message(), equalTo("Value must be greater than or equal to 2"));
        }
    }

    @Test
    public void detectsTooDeepRecursion() {
        var schemas = createListSchemas(4);
        Validator validator = Validator.builder().maxDepth(3).build();

        var errors = validator.validate(s1 -> {
            s1.writeList(schemas.get(0), null, (v2, s2) -> {
                s2.writeList(schemas.get(1), null, (v3, s3) -> {
                    s3.writeList(schemas.get(2), null, (v4, s4) -> {
                        s4.writeList(schemas.get(3), null, (v5, s5) -> {
                            s5.writeString(PreludeSchemas.STRING, "Hi");
                        });
                    });
                });
            });
        });

        assertThat(errors, hasSize(1));
        assertThat(errors.get(0).path(), equalTo("/0/0/0"));
        assertThat(errors.get(0).message(), equalTo("Value is too deeply nested"));

        // Now ensure that the path is back to normal.
        Schema schema = Schema.createIntEnum(ShapeId.from("smithy.example#E"), Set.of(1, 2, 3));

        errors = validator.validate(encoder -> encoder.writeByte(schema, (byte) 4));
        assertThat(errors, hasSize(1));
        assertThat(errors.get(0).path(), equalTo("/"));
    }

    private List<Schema> createListSchemas(int depth) {
        List<Schema> schemas = new ArrayList<>(depth);
        for (int i = depth; i > 0; i--) {
            if (i == depth) {
                schemas.add(
                    Schema.listBuilder(ShapeId.from("s#L" + depth))
                        .putMember("member", PreludeSchemas.STRING)
                        .build()
                );
            } else {
                schemas.add(
                    Schema.listBuilder(ShapeId.from("s#L3"))
                        .putMember("member", schemas.get(schemas.size() - 1))
                        .build()
                );
            }
        }
        return schemas;
    }

    @Test
    public void resizesPathArray() {
        var schemas = createListSchemas(10);
        Validator validator = Validator.builder().maxDepth(25).build();

        var errors = validator.validate(s1 -> {
            s1.writeList(schemas.get(0), null, (v2, s2) -> {
                s2.writeList(schemas.get(1), null, (v3, s3) -> {
                    s3.writeList(schemas.get(2), null, (v4, s4) -> {
                        s4.writeList(schemas.get(3), null, (v5, s5) -> {
                            s5.writeList(schemas.get(4), null, (v6, s6) -> {
                                s6.writeList(schemas.get(5), null, (v7, s7) -> {
                                    s7.writeList(schemas.get(6), null, (v8, s8) -> {
                                        s8.writeList(schemas.get(7), null, (v9, s9) -> {
                                            s9.writeList(schemas.get(8), null, (v10, s10) -> {
                                                s10.writeList(schemas.get(9), null, (v11, s11) -> {
                                                    s10.writeString(PreludeSchemas.STRING, "Hi");
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
        });

        assertThat(errors, empty());
    }

    @Test
    public void validatesStringPattern() {
        Validator validator = Validator.builder().build();
        var errors = validator.validate(encoder -> {
            encoder.writeString(
                Schema.createString(ShapeId.from("smithy.example#Foo"), new PatternTrait("^[a-z]+$")),
                "abc123"
            );
        });

        assertThat(errors, hasSize(1));
        assertThat(errors.get(0).path(), equalTo("/"));
        assertThat(
            errors.get(0).message(),
            equalTo("Value must satisfy regular expression pattern: ^[a-z]+$")
        );
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

        var errors = validator.validate(encoder -> {
            encoder.writeStruct(struct, SerializableStruct.create(struct, (schema, serializer) -> {
                // write the first required member but leave out the rest.
                serializer.writeString(schema.member("a"), "hi");
            }));
        });

        assertThat(errors, hasSize(2));
        assertThat(errors.get(0).path(), equalTo("/"));
        assertThat(errors.get(0).message(), equalTo("Value missing required member: b"));
        assertThat(errors.get(1).path(), equalTo("/"));
        assertThat(errors.get(1).message(), equalTo("Value missing required member: c"));
    }

    @Test
    public void validatesRequiredMembersMissingSingle() {
        Schema struct = Schema.structureBuilder(ShapeId.from("smithy.example#Foo"))
            .putMember("a", PreludeSchemas.STRING, new RequiredTrait())
            .build();

        Validator validator = Validator.builder().build();
        var errors = validator.validate(encoder -> {
            encoder.writeStruct(struct, SerializableStruct.create(struct, (schema, serializer) -> {}));
        });

        assertThat(errors, hasSize(1));
        assertThat(errors.get(0).path(), equalTo("/"));
        assertThat(errors.get(0).message(), equalTo("Value missing required member: a"));
    }

    // Enum and intEnum validation

    @Test
    public void validatesStringEnums() {
        Validator validator = Validator.builder().build();
        Schema schema = Schema.createEnum(ShapeId.from("smithy.example#E"), Set.of("a", "b", "c"));

        var errors = validator.validate(encoder -> encoder.writeString(schema, "d"));
        assertThat(errors, hasSize(1));
        assertThat(errors.get(0).path(), equalTo("/"));
        assertThat(errors.get(0).message(), equalTo("Value is not an allowed enum string"));

        // This is fine.
        assertThat(validator.validate(encoder -> encoder.writeString(schema, "a")), hasSize(0));
    }

    @Test
    public void validatesIntEnums() {
        Validator validator = Validator.builder().build();
        Schema schema = Schema.createIntEnum(ShapeId.from("smithy.example#E"), Set.of(1, 2, 3));

        // This is good.
        var errors = validator.validate(encoder -> encoder.writeInteger(schema, 1));
        assertThat(errors, empty());

        // The wrong types.
        errors = validator.validate(encoder -> encoder.writeLong(schema, 2));
        assertThat(errors, hasSize(1));
        assertThat(errors.get(0).message(), startsWith("Value must be intEnum, but found "));

        // Out of range.
        errors = validator.validate(encoder -> encoder.writeInteger(schema, -1));
        assertThat(errors, hasSize(1));

        // Out of range.
        errors = validator.validate(encoder -> encoder.writeInteger(schema, 4));
        assertThat(errors, hasSize(1));
        assertThat(errors.get(0).message(), equalTo("Value is not an allowed integer enum number"));
    }

    // Nested collection validation

    @Test
    public void validatesLists() {
        Validator validator = Validator.builder().build();

        var memberTarget = Schema.createString(ShapeId.from("s#S"), LengthTrait.builder().min(3L).build());
        var list = Schema.listBuilder(ShapeId.from("s#L"))
            .putMember("member", memberTarget)
            .build();

        var errors = validator.validate(s -> {
            s.writeList(list, list.member("member"), (member, ls) -> {
                ls.writeString(member, "n");
                ls.writeString(member, "no");
                ls.writeString(member, "good");
            });
        });

        assertThat(errors, hasSize(2));
        assertThat(errors.get(0).path(), equalTo("/0"));
        assertThat(
            errors.get(0).message(),
            equalTo("Value with length 1 must have length greater than or equal to 3")
        );
        assertThat(errors.get(1).path(), equalTo("/1"));
        assertThat(
            errors.get(1).message(),
            equalTo("Value with length 2 must have length greater than or equal to 3")
        );
    }

    @Test
    public void validatesMapKeysAndValues() {
        Validator validator = Validator.builder().build();

        var keySchema = Schema.createString(ShapeId.from("s#K"), LengthTrait.builder().min(3L).build());
        var map = Schema.mapBuilder(ShapeId.from("s#M"))
            .putMember("key", keySchema)
            .putMember("value", PreludeSchemas.STRING, LengthTrait.builder().min(2L).build())
            .build();

        var errors = validator.validate(s -> {
            s.writeMap(map, map.member("key"), (mapKey, ms) -> {
                ms.writeEntry(mapKey, "fine", null, (v, vs) -> vs.writeString(PreludeSchemas.STRING, "ok"));
                ms.writeEntry(mapKey, "a", null, (v, vs) -> vs.writeString(PreludeSchemas.STRING, "too few"));
                ms.writeEntry(mapKey, "b", null, (v, vs) -> vs.writeString(PreludeSchemas.STRING, "too few"));
                ms.writeEntry(mapKey, "good-key-bad-value", null, (v, vs) -> vs.writeString(map.member("value"), "!"));
            });
        });

        assertThat(errors, hasSize(3));
        assertThat(errors.get(0).path(), equalTo("/a/key"));
        assertThat(
            errors.get(0).message(),
            equalTo("Value with length 1 must have length greater than or equal to 3")
        );
        assertThat(errors.get(1).path(), equalTo("/b/key"));
        assertThat(
            errors.get(1).message(),
            equalTo("Value with length 1 must have length greater than or equal to 3")
        );
        assertThat(errors.get(2).path(), equalTo("/good-key-bad-value/value"));
        assertThat(
            errors.get(2).message(),
            equalTo("Value with length 1 must have length greater than or equal to 2")
        );
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

        assertThat(errors, empty());
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

        assertThat(errors, empty());
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

        assertThat(errors, empty());
    }

    @Test
    public void validatesPojoWithValidatedCollection() {
        var pojoWithValidatedCollection = PojoWithValidatedCollection.builder()
            .list(
                List.of(
                    ValidatedPojo.builder().string("abc").integer(100).boxedInteger(1).build(),
                    ValidatedPojo.builder().string("123").integer(5).boxedInteger(0).build(),
                    ValidatedPojo.builder().string("1").integer(2).boxedInteger(3).build()
                )
            )
            .map(
                Map.of(
                    "a",
                    ValidatedPojo.builder().string("abc").integer(100).boxedInteger(1).build(),
                    "b",
                    ValidatedPojo.builder().string("123").integer(5).boxedInteger(0).build(),
                    "c",
                    ValidatedPojo.builder().string("1").integer(2).boxedInteger(3).build()
                )
            )
            .build();

        Validator validator = Validator.builder().build();
        var errors = validator.validate(pojoWithValidatedCollection);

        assertThat(errors, empty());
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
        var errors = validator.validate(s -> {
            s.writeStruct(unionSchema, SerializableStruct.create(unionSchema, (schema, writer) -> {}));
        });

        assertThat(errors, hasSize(1));
        assertThat(errors.get(0).path(), equalTo("/"));
        assertThat(errors.get(0).message(), equalTo("No member is set in the union"));
    }

    @Test
    public void validatesUnionSetOnlyOneMember() {
        Validator validator = Validator.builder().build();
        var unionSchema = getTestUnionSchema();

        var errors = validator.validate(s -> {
            s.writeStruct(unionSchema, SerializableStruct.create(unionSchema, (schema, serializer) -> {
                serializer.writeString(schema.member("a"), "hi");
                serializer.writeString(schema.member("b"), "byte");
            }));
        });

        assertThat(errors, hasSize(1));
        assertThat(errors.get(0).path(), equalTo("/b"));
        assertThat(errors.get(0).message(), equalTo("Union member conflicts with 'a'"));
    }

    @Test
    public void allowsValidUnion() {
        Validator validator = Validator.builder().build();
        var unionSchema = getTestUnionSchema();

        var errors = validator.validate(s -> {
            s.writeStruct(unionSchema, SerializableStruct.create(unionSchema, (schema, serializer) -> {
                serializer.writeString(schema.member("a"), "ok!");
            }));
        });

        assertThat(errors, empty());
    }

    @Test
    public void validatesTheContentsOfSetUnionMember() {
        Validator validator = Validator.builder().build();
        var unionSchema = getTestUnionSchema();

        var errors = validator.validate(s -> {
            s.writeStruct(unionSchema, SerializableStruct.create(unionSchema, (schema, serializer) -> {
                serializer.writeString(schema.member("a"), "this is too long!");
            }));
        });

        assertThat(errors, hasSize(1));
        assertThat(errors.get(0).path(), equalTo("/a"));
        assertThat(
            errors.get(0).message(),
            equalTo("Value with length 17 must have length less than or equal to 3")
        );
    }

    @Test
    public void ignoresNullValuesInUnion() {
        Validator validator = Validator.builder().build();
        var unionSchema = getTestUnionSchema();

        var errors = validator.validate(s -> {
            s.writeStruct(unionSchema, SerializableStruct.create(unionSchema, (schema, serializer) -> {
                serializer.writeString(schema.member("a"), null); // ignore it
                serializer.writeNull(schema.member("b"));         // ignore it
                serializer.writeString(schema.member("c"), "ok"); // it's set, it's the only non-null value.
            }));
        });

        assertThat(errors, empty());
    }

    // Null value tests.

    // Writing a null value independent of a container shape is simply ignored. The validator doesn't have
    // enough context to know if this is allowed or not.
    @Test
    public void allowsStandaloneNullValue() {
        Validator validator = Validator.builder().build();

        var errors = validator.validate(s -> s.writeNull(PreludeSchemas.STRING));

        assertThat(errors, empty());
    }

    @Test
    public void allowsNullInSparseList() {
        Validator validator = Validator.builder().build();
        var listSchema = Schema.listBuilder(ShapeId.from("smithy.api#Test"), new SparseTrait())
            .putMember("member", PreludeSchemas.STRING)
            .build();

        var errors = validator.validate(s -> {
            s.writeList(listSchema, listSchema.member("member"), (member, ls) -> {
                ls.writeString(member, "this is fine"); // fine
                ls.writeNull(member); // fine
            });
        });

        assertThat(errors, empty());
    }

    @Test
    public void allowsNullInStructures() {
        // Required structure member is validated elsewhere.
        Validator validator = Validator.builder().build();
        Schema schema = Schema.structureBuilder(ShapeId.from("smithy.example#Test"))
            .putMember("foo", PreludeSchemas.STRING)
            .build();

        var errors = validator.validate(s -> {
            s.writeStruct(schema, SerializableStruct.create(schema, (passedSchema, serializer) -> {
                serializer.writeNull(passedSchema.member("foo")); // fine
            }));
        });

        assertThat(errors, empty());
    }

    // To write a null in a list, it has to have the sparse trait.
    @Test
    public void doesNotAllowNullValuesInListByDefault() {
        Validator validator = Validator.builder().build();
        var listSchema = Schema.listBuilder(ShapeId.from("smithy.api#Test"))
            .putMember("member", PreludeSchemas.STRING)
            .build();

        var errors = validator.validate(s -> {
            s.writeList(listSchema, listSchema.member("member"), (member, ls) -> {
                ls.writeString(member, "this is fine"); // fine
                ls.writeNull(member); // bad
            });
        });

        assertThat(errors, hasSize(1));
        assertThat(errors.get(0).path(), equalTo("/1"));
        assertThat(
            errors.get(0).message(),
            equalTo("Value is in a list that does not allow null values")
        );
    }

    // To write a null in a map, it has to have the sparse trait.
    @Test
    public void doesNotAllowNullValuesInMapsByDefault() {
        Validator validator = Validator.builder().build();
        var mapSchema = Schema.mapBuilder(ShapeId.from("smithy.api#Test"))
            .putMember("key", PreludeSchemas.STRING)
            .putMember("value", PreludeSchemas.STRING)
            .build();

        var errors = validator.validate(s -> {
            s.writeMap(mapSchema, mapSchema, (schema, ms) -> {
                ms.writeEntry(
                    schema.member("key"),
                    "hi",
                    schema,
                    (mapSchema2, msvs) -> msvs.writeString(mapSchema2.member("value"), "ok")
                );
                ms.writeEntry(
                    schema.member("key"),
                    "oops",
                    schema,
                    (mapSchema2, msvs) -> msvs.writeNull(mapSchema2.member("value"))
                );
            });
        });

        assertThat(errors, hasSize(1));
        assertThat(errors.get(0).path(), equalTo("/oops/value"));
        assertThat(
            errors.get(0).message(),
            equalTo("Value is in a map that does not allow null values")
        );
    }

    @ParameterizedTest
    @MethodSource("detectsIncorrectTypeSupplier")
    public void detectsIncorrectType(ShapeType type, Consumer<ShapeSerializer> encoder) {
        Validator validator = Validator.builder().build();
        var errors = validator.validate(encoder::accept);

        assertThat(errors, hasSize(1));
        assertThat(errors.get(0).path(), equalTo("/"));
        assertThat(errors.get(0).message(), startsWith("Value must be "));
        assertThat(errors.get(0).message(), endsWith(", but found " + type));
    }

    public static List<Arguments> detectsIncorrectTypeSupplier() {
        return List.of(
            Arguments.of(
                ShapeType.BLOB,
                (Consumer<ShapeSerializer>) serializer -> serializer.writeBlob(
                    PreludeSchemas.INTEGER,
                    wrap("a".getBytes(StandardCharsets.UTF_8))
                )
            ),
            Arguments.of(
                ShapeType.STRING,
                (Consumer<ShapeSerializer>) serializer -> serializer.writeString(PreludeSchemas.INTEGER, "a")
            ),
            Arguments.of(
                ShapeType.TIMESTAMP,
                (Consumer<ShapeSerializer>) serializer -> serializer.writeTimestamp(
                    PreludeSchemas.STRING,
                    Instant.EPOCH
                )
            ),
            Arguments.of(
                ShapeType.BOOLEAN,
                (Consumer<ShapeSerializer>) serializer -> serializer.writeBoolean(PreludeSchemas.STRING, true)
            ),
            Arguments.of(
                ShapeType.DOCUMENT,
                (Consumer<ShapeSerializer>) serializer -> serializer.writeDocument(
                    PreludeSchemas.STRING,
                    Document.createString("hi")
                )
            ),
            Arguments.of(
                ShapeType.BYTE,
                (Consumer<ShapeSerializer>) serializer -> serializer.writeByte(PreludeSchemas.STRING, (byte) 1)
            ),
            Arguments.of(
                ShapeType.SHORT,
                (Consumer<ShapeSerializer>) serializer -> serializer.writeShort(PreludeSchemas.STRING, (short) 1)
            ),
            Arguments.of(
                ShapeType.INTEGER,
                (Consumer<ShapeSerializer>) serializer -> serializer.writeInteger(PreludeSchemas.STRING, 1)
            ),
            Arguments.of(
                ShapeType.LONG,
                (Consumer<ShapeSerializer>) serializer -> serializer.writeLong(PreludeSchemas.STRING, 1L)
            ),
            Arguments.of(
                ShapeType.FLOAT,
                (Consumer<ShapeSerializer>) serializer -> serializer.writeFloat(PreludeSchemas.STRING, 1f)
            ),
            Arguments.of(
                ShapeType.DOUBLE,
                (Consumer<ShapeSerializer>) serializer -> serializer.writeDouble(PreludeSchemas.STRING, 1.0)
            ),
            Arguments.of(
                ShapeType.BIG_INTEGER,
                (Consumer<ShapeSerializer>) serializer -> serializer.writeBigInteger(
                    PreludeSchemas.STRING,
                    BigInteger.ONE
                )
            ),
            Arguments.of(
                ShapeType.BIG_DECIMAL,
                (Consumer<ShapeSerializer>) serializer -> serializer.writeBigDecimal(
                    PreludeSchemas.STRING,
                    BigDecimal.ONE
                )
            ),
            Arguments.of(
                ShapeType.STRUCTURE,
                (Consumer<ShapeSerializer>) serializer -> serializer.writeStruct(
                    PreludeSchemas.STRING,
                    SerializableStruct.create(PreludeSchemas.STRING, (a, b) -> {})
                )
            ),
            Arguments.of(
                ShapeType.LIST,
                (Consumer<ShapeSerializer>) serializer -> serializer.writeList(
                    PreludeSchemas.STRING,
                    null,
                    (v, s) -> {}
                )
            ),
            Arguments.of(
                ShapeType.MAP,
                (Consumer<ShapeSerializer>) serializer -> serializer.writeMap(PreludeSchemas.STRING, null, (v, s) -> {})
            )
        );
    }

    @ParameterizedTest
    @MethodSource("validatesRangeAndLengthSupplier")
    public void validatesRanges(
        Class<? extends ValidationError> error,
        BiFunction<ShapeId, Trait[], Schema> creator,
        BiConsumer<Schema, ShapeSerializer> consumer
    ) {
        var traits = new Trait[]{RangeTrait.builder().min(BigDecimal.ONE).max(BigDecimal.TEN).build(), LengthTrait
            .builder()
            .min(1L)
            .max(10L)
            .build()
        };
        Schema schema = creator.apply(ShapeId.from("smithy.example#Number"), traits);

        Validator validator = Validator.builder().build();
        var errors = validator.validate(e -> consumer.accept(schema, e));

        if (error == null) {
            assertThat(errors, empty());
        } else if (error.equals(ValidationError.RangeValidationFailure.class)) {
            assertThat(errors.get(0).getClass(), is(error));
            assertThat(errors, hasSize(1));
            assertThat(errors.get(0).path(), equalTo("/"));
            assertThat(errors.get(0).message(), equalTo("Value must be between 1 and 10, inclusive"));
        } else if (error.equals(ValidationError.LengthValidationFailure.class)) {
            assertThat(errors.get(0).getClass(), is(error));
            assertThat(errors, hasSize(1));
            assertThat(errors.get(0).path(), equalTo("/"));
            assertThat(errors.get(0).message(), startsWith("Value with length "));
            assertThat(
                errors.get(0).message(),
                endsWith("must have length between 1 and 10, inclusive")
            );
        } else {
            throw new RuntimeException("Invalid error type");
        }
    }

    public static List<Arguments> validatesRangeAndLengthSupplier() {
        return List.of(
            Arguments.of(
                null,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createByte,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeByte(schema, (byte) 1)
            ),
            Arguments.of(
                null,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createShort,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeShort(
                    schema,
                    (short) 1
                )
            ),
            Arguments.of(
                null,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createInteger,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeInteger(schema, 1)
            ),
            Arguments.of(
                null,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createLong,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeLong(schema, 1L)
            ),
            Arguments.of(
                null,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createFloat,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeFloat(schema, 1f)
            ),
            Arguments.of(
                null,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createDouble,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeDouble(schema, 1.0)
            ),
            Arguments.of(
                null,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createBigInteger,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeBigInteger(
                    schema,
                    BigInteger.ONE
                )
            ),
            Arguments.of(
                null,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createBigDecimal,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeBigDecimal(
                    schema,
                    BigDecimal.ONE
                )
            ),

            Arguments.of(
                ValidationError.RangeValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createByte,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeByte(schema, (byte) 0)
            ),
            Arguments.of(
                ValidationError.RangeValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createShort,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeShort(
                    schema,
                    (short) 0
                )
            ),
            Arguments.of(
                ValidationError.RangeValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createInteger,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeInteger(schema, 0)
            ),
            Arguments.of(
                ValidationError.RangeValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createLong,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeLong(schema, 0L)
            ),
            Arguments.of(
                ValidationError.RangeValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createFloat,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeFloat(schema, 0f)
            ),
            Arguments.of(
                ValidationError.RangeValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createDouble,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeDouble(schema, 0.0)
            ),
            Arguments.of(
                ValidationError.RangeValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createBigInteger,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeBigInteger(
                    schema,
                    BigInteger.ZERO
                )
            ),
            Arguments.of(
                ValidationError.RangeValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createBigDecimal,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeBigDecimal(
                    schema,
                    BigDecimal.ZERO
                )
            ),

            Arguments.of(
                ValidationError.RangeValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createByte,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeByte(schema, (byte) 11)
            ),
            Arguments.of(
                ValidationError.RangeValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createShort,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeShort(
                    schema,
                    (short) 11
                )
            ),
            Arguments.of(
                ValidationError.RangeValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createInteger,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeInteger(schema, 11)
            ),
            Arguments.of(
                ValidationError.RangeValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createLong,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeLong(schema, 11L)
            ),
            Arguments.of(
                ValidationError.RangeValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createFloat,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeFloat(schema, 11f)
            ),
            Arguments.of(
                ValidationError.RangeValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createDouble,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeDouble(schema, 11.0)
            ),
            Arguments.of(
                ValidationError.RangeValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createBigInteger,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeBigInteger(
                    schema,
                    BigInteger.valueOf(11)
                )
            ),
            Arguments.of(
                ValidationError.RangeValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createBigDecimal,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeBigDecimal(
                    schema,
                    BigDecimal.valueOf(11)
                )
            ),

            Arguments.of(
                null,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createBlob,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeBlob(
                    schema,
                    wrap("a".getBytes(StandardCharsets.UTF_8))
                )
            ),
            Arguments.of(
                null,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createString,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeString(schema, "a")
            ),
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
                    (v, ls) -> ls.writeString(PreludeSchemas.STRING, "a")
                )
            ),
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
                    (mapStateValue, mapSerializer) -> mapSerializer.writeEntry(
                        PreludeSchemas.STRING,
                        "a",
                        null,
                        (mapValueState, mapValueSerializer) -> {
                            mapValueSerializer.writeString(PreludeSchemas.STRING, "a");
                        }
                    )
                )
            ),

            Arguments.of(
                ValidationError.LengthValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createBlob,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeBlob(
                    schema,
                    wrap("".getBytes(StandardCharsets.UTF_8))
                )
            ),
            Arguments.of(
                ValidationError.LengthValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createString,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeString(schema, "")
            ),
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
                    (v, ls) -> {}
                )
            ),
            Arguments.of(
                ValidationError.LengthValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) (id, traits) -> {
                    return Schema.mapBuilder(id, traits)
                        .putMember("key", PreludeSchemas.STRING)
                        .putMember("value", PreludeSchemas.STRING)
                        .build();
                },
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> {
                    serializer.writeMap(schema, null, (mapStateValue, mapSerializer) -> {});
                }
            ),

            Arguments.of(
                ValidationError.LengthValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createBlob,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeBlob(
                    schema,
                    wrap("abcdefghijklmnop".getBytes(StandardCharsets.UTF_8))
                )
            ),
            Arguments.of(
                ValidationError.LengthValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) Schema::createString,
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> serializer.writeString(
                    schema,
                    "abcdefghijklmnop"
                )
            ),
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
                    (v, ls) -> {
                        for (int i = 0; i < 11; i++) {
                            ls.writeString(PreludeSchemas.STRING, "a");
                        }
                    }
                )
            ),
            Arguments.of(
                ValidationError.LengthValidationFailure.class,
                (BiFunction<ShapeId, Trait[], Schema>) (id, traits) -> {
                    return Schema.mapBuilder(id, traits)
                        .putMember("key", PreludeSchemas.STRING)
                        .putMember("value", PreludeSchemas.STRING)
                        .build();
                },
                (BiConsumer<Schema, ShapeSerializer>) (schema, serializer) -> {
                    serializer.writeMap(schema, null, (mapState, mapSerializer) -> {
                        for (int i = 0; i < 11; i++) {
                            mapSerializer.writeEntry(
                                PreludeSchemas.STRING,
                                "a" + i,
                                null,
                                (mapValueState, mapValueSerializer) -> {
                                    mapValueSerializer.writeString(PreludeSchemas.STRING, "a");
                                }
                            );
                        }
                    });
                }
            )
        );
    }

    @Test
    public void rangeErrorTooSmall() {
        var schema = Schema.createFloat(
            ShapeId.from("smithy.example#Number"),
            RangeTrait.builder().min(new BigDecimal("1.2")).build()
        );
        var validator = Validator.builder().build();
        var errors = validator.validate(e -> e.writeFloat(schema, 1.0f));

        assertThat(errors, hasSize(1));
        assertThat(errors.get(0), instanceOf(ValidationError.RangeValidationFailure.class));
        assertThat(errors.get(0).message(), equalTo("Value must be greater than or equal to 1.2"));
    }

    @Test
    public void rangeErrorTooBig() {
        var schema = Schema.createFloat(
            ShapeId.from("smithy.example#Number"),
            RangeTrait.builder().max(new BigDecimal("1.2")).build()
        );
        var validator = Validator.builder().build();
        var errors = validator.validate(e -> e.writeFloat(schema, 1.3f));

        assertThat(errors, hasSize(1));
        assertThat(errors.get(0), instanceOf(ValidationError.RangeValidationFailure.class));
        assertThat(errors.get(0).message(), equalTo("Value must be less than or equal to 1.2"));
    }

    @Test
    public void rangeErrorNotBetween() {
        var schema = Schema.createFloat(
            ShapeId.from("smithy.example#Number"),
            RangeTrait.builder().min(new BigDecimal("1.1")).max(new BigDecimal("1.2")).build()
        );
        var validator = Validator.builder().build();
        var errors = validator.validate(e -> e.writeFloat(schema, 1.3f));

        assertThat(errors, hasSize(1));
        var first = errors.get(0);
        assertThat(errors.get(0), instanceOf(ValidationError.RangeValidationFailure.class));

        var error = (ValidationError.RangeValidationFailure) first;
        assertThat(error.value().doubleValue(), closeTo(1.3f, 0.01f));
        assertThat(error.schema().getTrait(RangeTrait.class).getMin().get(), is(new BigDecimal("1.1")));
        assertThat(error.schema().getTrait(RangeTrait.class).getMax().get(), is(new BigDecimal("1.2")));
        assertThat(error.path(), is("/"));
        assertThat(error.message(), is("Value must be between 1.1 and 1.2, inclusive"));
    }

    @Test
    public void lengthTooShort() {
        var schema = Schema.listBuilder(ShapeId.from("smithy.api#Test"), LengthTrait.builder().min(2L).build())
            .putMember("member", PreludeSchemas.STRING)
            .build();
        var validator = Validator.builder().build();
        var errors = validator.validate(e -> e.writeList(schema, null, (v, ser) -> {}));

        assertThat(errors, hasSize(1));
        assertThat(errors.get(0), instanceOf(ValidationError.LengthValidationFailure.class));
        assertThat(
            errors.get(0).message(),
            equalTo("Value with length 0 must have length greater than or equal to 2")
        );
    }

    @Test
    public void lengthTooLong() {
        var schema = Schema.listBuilder(ShapeId.from("smithy.api#Test"), LengthTrait.builder().max(1L).build())
            .putMember("member", PreludeSchemas.STRING)
            .build();
        var validator = Validator.builder().build();
        var errors = validator.validate(e -> e.writeList(schema, schema.member("member"), (member, ser) -> {
            ser.writeString(member, "a");
            ser.writeString(member, "b");
        }));

        assertThat(errors, hasSize(1));
        assertThat(errors.get(0), instanceOf(ValidationError.LengthValidationFailure.class));
        assertThat(
            errors.get(0).message(),
            equalTo("Value with length 2 must have length less than or equal to 1")
        );
    }

    @Test
    public void lengthNotBetween() {
        var schema = Schema.listBuilder(ShapeId.from("smithy.api#Test"), LengthTrait.builder().min(1L).max(2L).build())
            .putMember("member", PreludeSchemas.STRING)
            .build();
        var validator = Validator.builder().build();
        var errors = validator.validate(e -> e.writeList(schema, schema.member("member"), (member, ser) -> {
            ser.writeString(member, "a");
            ser.writeString(member, "b");
            ser.writeString(member, "c");
        }));

        assertThat(errors, hasSize(1));
        var first = errors.get(0);
        assertThat(errors.get(0), instanceOf(ValidationError.LengthValidationFailure.class));

        var error = (ValidationError.LengthValidationFailure) first;
        assertThat(error.length(), is(3L));
        assertThat(error.schema().getTrait(LengthTrait.class).getMin().get(), is(1L));
        assertThat(error.schema().getTrait(LengthTrait.class).getMax().get(), is(2L));
        assertThat(error.path(), is("/"));
        assertThat(error.message(), is("Value with length 3 must have length between 1 and 2, inclusive"));
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

        var errors = validator.validate(encoder -> {
            encoder.writeStruct(struct, SerializableStruct.create(struct, (schema, writer) -> {}));
        });

        assertThat(errors, hasSize(failures));

        for (var e : errors) {
            assertThat(e, instanceOf(ValidationError.RequiredValidationFailure.class));
        }
    }

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
            Arguments.of(65, 65, 0, 65)
        );
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
}
