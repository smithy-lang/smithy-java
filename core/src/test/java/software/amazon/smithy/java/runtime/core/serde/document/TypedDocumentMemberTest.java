/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import static java.nio.ByteBuffer.wrap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

public class TypedDocumentMemberTest {
    // There's special handling for equality to account for the schema and normalized value.
    @Test
    public void equalityAndHashTest() {
        var structSchema = Schema.structureBuilder(ShapeId.from("smithy.example#Struct1"))
            .putMember("foo", PreludeSchemas.STRING)
            .build();

        SerializableShape serializableShape = encoder -> {
            encoder.writeStruct(
                structSchema,
                SerializableStruct.create(structSchema, (schema, s) -> s.writeString(schema.member("foo"), "Hi"))
            );
        };

        var document = Document.createTyped(serializableShape);
        var documentCopy = Document.createTyped(serializableShape);

        assertThat(document.getMember("foo"), equalTo(document.getMember("foo"))); // map value not cached
        assertThat(document.getMember("foo"), equalTo(document.getMember("foo"))); // cached at this point
        assertThat(document.getMember("foo"), equalTo(documentCopy.getMember("foo")));

        var fooMember = document.getMember("foo");
        assertThat(fooMember, equalTo(fooMember));

        assertThat(document.hashCode(), equalTo(documentCopy.hashCode()));
    }

    @Test
    public void inequalityAndHashTest() {
        var structSchema1 = Schema.structureBuilder(ShapeId.from("smithy.example#Struct1"))
            .putMember("foo", PreludeSchemas.STRING)
            .build();
        var structSchema2 = Schema.structureBuilder(ShapeId.from("smithy.example#Struct2"))
            .putMember("foo", PreludeSchemas.INTEGER)
            .build();

        var document1 = Document.createTyped(encoder -> {
            encoder.writeStruct(
                structSchema1,
                SerializableStruct.create(structSchema1, (schema, s) -> s.writeString(schema.member("foo"), "Hi"))
            );
        });

        var document2 = Document.createTyped(encoder -> {
            encoder.writeStruct(
                structSchema2,
                SerializableStruct.create(structSchema2, (schema, s) -> s.writeInteger(schema.member("foo"), 1))
            );
        });

        assertThat(document1.getMember("foo"), not(equalTo(null)));
        assertThat(document1.getMember("foo"), not(equalTo(document2.getMember("foo"))));
    }

    // Exercises the "as" methods of a document.
    @ParameterizedTest
    @MethodSource("convertsMemberProvider")
    public void convertsMember(
        Object targetTypeOrSchema,
        Object value,
        BiConsumer<Schema, ShapeSerializer> writer,
        Function<Document, Object> extractor
    ) {
        Schema targetSchema;
        if (targetTypeOrSchema instanceof ShapeType t) {
            targetSchema = PreludeSchemas.getSchemaForType(t);
        } else if (targetTypeOrSchema instanceof Schema s) {
            targetSchema = s;
        } else {
            throw new IllegalArgumentException(
                "Expected targetTypeOrSchema to be ShapeType or SdkSchema: "
                    + targetTypeOrSchema
            );
        }

        var structSchema = Schema.structureBuilder(ShapeId.from("smithy.example#Foo"))
            .putMember("a", targetSchema)
            .build();
        var document = Document.createTyped(encoder -> {
            encoder.writeStruct(structSchema, SerializableStruct.create(structSchema, (schema, serializer) -> {
                writer.accept(schema.member("a"), serializer);
            }));
        });

        assertThat(extractor.apply(document.getMember("a")), equalTo(value));
    }

    public static List<Arguments> convertsMemberProvider() {
        return List.of(
            Arguments.of(
                ShapeType.BOOLEAN,
                true,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeBoolean(schema, true),
                (Function<Document, Object>) Document::asBoolean
            ),
            Arguments.of(
                ShapeType.STRING,
                "a",
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeString(schema, "a"),
                (Function<Document, Object>) Document::asString
            ),
            Arguments.of(
                ShapeType.BLOB,
                wrap("a".getBytes(StandardCharsets.UTF_8)),
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeBlob(
                    schema,
                    "a".getBytes(StandardCharsets.UTF_8)
                ),
                (Function<Document, Object>) Document::asBlob
            ),
            Arguments.of(
                ShapeType.TIMESTAMP,
                Instant.EPOCH,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeTimestamp(schema, Instant.EPOCH),
                (Function<Document, Object>) Document::asTimestamp
            ),

            // Test each combination of numbers and casting to other numbers.
            Arguments.of(
                ShapeType.BYTE,
                (byte) 1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeByte(schema, (byte) 1),
                (Function<Document, Object>) Document::asByte
            ),
            Arguments.of(
                ShapeType.BYTE,
                (short) 1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeByte(schema, (byte) 1),
                (Function<Document, Object>) Document::asShort
            ),
            Arguments.of(
                ShapeType.BYTE,
                1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeByte(schema, (byte) 1),
                (Function<Document, Object>) Document::asInteger
            ),
            Arguments.of(
                ShapeType.BYTE,
                1L,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeByte(schema, (byte) 1),
                (Function<Document, Object>) Document::asLong
            ),
            Arguments.of(
                ShapeType.BYTE,
                1f,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeByte(schema, (byte) 1),
                (Function<Document, Object>) Document::asFloat
            ),
            Arguments.of(
                ShapeType.BYTE,
                1.0,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeByte(schema, (byte) 1),
                (Function<Document, Object>) Document::asDouble
            ),
            Arguments.of(
                ShapeType.BYTE,
                BigInteger.ONE,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeByte(schema, (byte) 1),
                (Function<Document, Object>) Document::asBigInteger
            ),
            Arguments.of(
                ShapeType.BYTE,
                BigDecimal.ONE,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeByte(schema, (byte) 1),
                (Function<Document, Object>) Document::asBigDecimal
            ),

            Arguments.of(
                ShapeType.SHORT,
                (byte) 1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeShort(schema, (short) 1),
                (Function<Document, Object>) Document::asByte
            ),
            Arguments.of(
                ShapeType.SHORT,
                (short) 1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeShort(schema, (short) 1),
                (Function<Document, Object>) Document::asShort
            ),
            Arguments.of(
                ShapeType.SHORT,
                1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeShort(schema, (short) 1),
                (Function<Document, Object>) Document::asInteger
            ),
            Arguments.of(
                ShapeType.SHORT,
                1L,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeShort(schema, (short) 1),
                (Function<Document, Object>) Document::asLong
            ),
            Arguments.of(
                ShapeType.SHORT,
                1f,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeShort(schema, (short) 1),
                (Function<Document, Object>) Document::asFloat
            ),
            Arguments.of(
                ShapeType.SHORT,
                1.0,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeShort(schema, (short) 1),
                (Function<Document, Object>) Document::asDouble
            ),
            Arguments.of(
                ShapeType.SHORT,
                BigInteger.ONE,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeShort(schema, (short) 1),
                (Function<Document, Object>) Document::asBigInteger
            ),
            Arguments.of(
                ShapeType.SHORT,
                BigDecimal.ONE,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeShort(schema, (short) 1),
                (Function<Document, Object>) Document::asBigDecimal
            ),

            Arguments.of(
                ShapeType.INTEGER,
                (byte) 1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeInteger(schema, 1),
                (Function<Document, Object>) Document::asByte
            ),
            Arguments.of(
                ShapeType.INTEGER,
                (short) 1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeInteger(schema, 1),
                (Function<Document, Object>) Document::asShort
            ),
            Arguments.of(
                ShapeType.INTEGER,
                1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeInteger(schema, 1),
                (Function<Document, Object>) Document::asInteger
            ),
            Arguments.of(
                ShapeType.INTEGER,
                1L,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeInteger(schema, 1),
                (Function<Document, Object>) Document::asLong
            ),
            Arguments.of(
                ShapeType.INTEGER,
                1f,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeInteger(schema, 1),
                (Function<Document, Object>) Document::asFloat
            ),
            Arguments.of(
                ShapeType.INTEGER,
                1.0,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeInteger(schema, 1),
                (Function<Document, Object>) Document::asDouble
            ),
            Arguments.of(
                ShapeType.INTEGER,
                BigInteger.ONE,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeInteger(schema, 1),
                (Function<Document, Object>) Document::asBigInteger
            ),
            Arguments.of(
                ShapeType.INTEGER,
                BigDecimal.ONE,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeInteger(schema, 1),
                (Function<Document, Object>) Document::asBigDecimal
            ),

            Arguments.of(
                ShapeType.LONG,
                (byte) 1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeLong(schema, 1),
                (Function<Document, Object>) Document::asByte
            ),
            Arguments.of(
                ShapeType.LONG,
                (short) 1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeLong(schema, 1),
                (Function<Document, Object>) Document::asShort
            ),
            Arguments.of(
                ShapeType.LONG,
                1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeLong(schema, 1),
                (Function<Document, Object>) Document::asInteger
            ),
            Arguments.of(
                ShapeType.LONG,
                1L,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeLong(schema, 1),
                (Function<Document, Object>) Document::asLong
            ),
            Arguments.of(
                ShapeType.LONG,
                1f,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeLong(schema, 1),
                (Function<Document, Object>) Document::asFloat
            ),
            Arguments.of(
                ShapeType.LONG,
                1.0,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeLong(schema, 1),
                (Function<Document, Object>) Document::asDouble
            ),
            Arguments.of(
                ShapeType.LONG,
                BigInteger.ONE,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeLong(schema, 1),
                (Function<Document, Object>) Document::asBigInteger
            ),
            Arguments.of(
                ShapeType.LONG,
                BigDecimal.ONE,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeLong(schema, 1),
                (Function<Document, Object>) Document::asBigDecimal
            ),

            Arguments.of(
                ShapeType.FLOAT,
                (byte) 1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeFloat(schema, 1),
                (Function<Document, Object>) Document::asByte
            ),
            Arguments.of(
                ShapeType.FLOAT,
                (short) 1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeFloat(schema, 1),
                (Function<Document, Object>) Document::asShort
            ),
            Arguments.of(
                ShapeType.FLOAT,
                1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeFloat(schema, 1),
                (Function<Document, Object>) Document::asInteger
            ),
            Arguments.of(
                ShapeType.FLOAT,
                1L,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeFloat(schema, 1),
                (Function<Document, Object>) Document::asLong
            ),
            Arguments.of(
                ShapeType.FLOAT,
                1f,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeFloat(schema, 1),
                (Function<Document, Object>) Document::asFloat
            ),
            Arguments.of(
                ShapeType.FLOAT,
                1.0,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeFloat(schema, 1),
                (Function<Document, Object>) Document::asDouble
            ),
            Arguments.of(
                ShapeType.FLOAT,
                BigInteger.ONE,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeFloat(schema, 1),
                (Function<Document, Object>) Document::asBigInteger
            ),
            Arguments.of(
                ShapeType.FLOAT,
                BigDecimal.ONE,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeFloat(schema, 1),
                (Function<Document, Object>) Document::asBigDecimal
            ),

            Arguments.of(
                ShapeType.DOUBLE,
                (byte) 1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeDouble(schema, 1),
                (Function<Document, Object>) Document::asByte
            ),
            Arguments.of(
                ShapeType.DOUBLE,
                (short) 1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeDouble(schema, 1),
                (Function<Document, Object>) Document::asShort
            ),
            Arguments.of(
                ShapeType.DOUBLE,
                1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeDouble(schema, 1),
                (Function<Document, Object>) Document::asInteger
            ),
            Arguments.of(
                ShapeType.DOUBLE,
                1L,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeDouble(schema, 1),
                (Function<Document, Object>) Document::asLong
            ),
            Arguments.of(
                ShapeType.DOUBLE,
                1f,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeDouble(schema, 1),
                (Function<Document, Object>) Document::asFloat
            ),
            Arguments.of(
                ShapeType.DOUBLE,
                1.0,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeDouble(schema, 1),
                (Function<Document, Object>) Document::asDouble
            ),
            Arguments.of(
                ShapeType.DOUBLE,
                BigInteger.ONE,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeDouble(schema, 1),
                (Function<Document, Object>) Document::asBigInteger
            ),
            Arguments.of(
                ShapeType.DOUBLE,
                BigDecimal.ONE,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeDouble(schema, 1),
                (Function<Document, Object>) Document::asBigDecimal
            ),

            Arguments.of(
                ShapeType.BIG_INTEGER,
                (byte) 1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeBigInteger(schema, BigInteger.ONE),
                (Function<Document, Object>) Document::asByte
            ),
            Arguments.of(
                ShapeType.BIG_INTEGER,
                (short) 1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeBigInteger(schema, BigInteger.ONE),
                (Function<Document, Object>) Document::asShort
            ),
            Arguments.of(
                ShapeType.BIG_INTEGER,
                1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeBigInteger(schema, BigInteger.ONE),
                (Function<Document, Object>) Document::asInteger
            ),
            Arguments.of(
                ShapeType.BIG_INTEGER,
                1L,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeBigInteger(schema, BigInteger.ONE),
                (Function<Document, Object>) Document::asLong
            ),
            Arguments.of(
                ShapeType.BIG_INTEGER,
                1f,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeBigInteger(schema, BigInteger.ONE),
                (Function<Document, Object>) Document::asFloat
            ),
            Arguments.of(
                ShapeType.BIG_INTEGER,
                1.0,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeBigInteger(schema, BigInteger.ONE),
                (Function<Document, Object>) Document::asDouble
            ),
            Arguments.of(
                ShapeType.BIG_INTEGER,
                BigInteger.ONE,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeBigInteger(schema, BigInteger.ONE),
                (Function<Document, Object>) Document::asBigInteger
            ),
            Arguments.of(
                ShapeType.BIG_INTEGER,
                BigDecimal.ONE,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeBigInteger(schema, BigInteger.ONE),
                (Function<Document, Object>) Document::asBigDecimal
            ),

            Arguments.of(
                ShapeType.BIG_DECIMAL,
                (byte) 1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeBigDecimal(schema, BigDecimal.ONE),
                (Function<Document, Object>) Document::asByte
            ),
            Arguments.of(
                ShapeType.BIG_DECIMAL,
                (short) 1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeBigDecimal(schema, BigDecimal.ONE),
                (Function<Document, Object>) Document::asShort
            ),
            Arguments.of(
                ShapeType.BIG_DECIMAL,
                1,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeBigDecimal(schema, BigDecimal.ONE),
                (Function<Document, Object>) Document::asInteger
            ),
            Arguments.of(
                ShapeType.BIG_DECIMAL,
                1L,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeBigDecimal(schema, BigDecimal.ONE),
                (Function<Document, Object>) Document::asLong
            ),
            Arguments.of(
                ShapeType.BIG_DECIMAL,
                1f,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeBigDecimal(schema, BigDecimal.ONE),
                (Function<Document, Object>) Document::asFloat
            ),
            Arguments.of(
                ShapeType.BIG_DECIMAL,
                1.0,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeBigDecimal(schema, BigDecimal.ONE),
                (Function<Document, Object>) Document::asDouble
            ),
            Arguments.of(
                ShapeType.BIG_DECIMAL,
                BigInteger.ONE,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeBigDecimal(schema, BigDecimal.ONE),
                (Function<Document, Object>) Document::asBigInteger
            ),
            Arguments.of(
                ShapeType.BIG_DECIMAL,
                BigDecimal.ONE,
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeBigDecimal(schema, BigDecimal.ONE),
                (Function<Document, Object>) Document::asBigDecimal
            ),

            // Get the document as a list.
            Arguments.of(
                ShapeType.DOCUMENT,
                List.of(Document.createInteger(1)),
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeList(
                    schema,
                    null,
                    (v, c) -> c.writeInteger(PreludeSchemas.INTEGER, 1)
                ),
                (Function<Document, Object>) d -> Document.createTyped(Document.createList(d.asList())).asList()
            ),

            // Get the document as a string map.
            Arguments.of(
                Documents.STR_MAP_SCHEMA,
                Map.of("a", Document.createInteger(1)),
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeMap(
                    schema,
                    null,
                    (mapValue, mapSerializer) -> mapSerializer.writeEntry(
                        schema.member("key"),
                        "a",
                        null,
                        (v, c) -> c.writeInteger(PreludeSchemas.INTEGER, 1)
                    )
                ),
                (Function<Document, Object>) Document::asStringMap
            ),

            // Get a member from a map by name.
            Arguments.of(
                Documents.STR_MAP_SCHEMA,
                Document.createInteger(1),
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> s.writeMap(
                    schema,
                    null,
                    (mapState, m) -> m.writeEntry(
                        schema.member("key"),
                        "a",
                        null,
                        (v, c) -> c.writeInteger(PreludeSchemas.INTEGER, 1)
                    )
                ),
                (Function<Document, Object>) d -> d.getMember("a")
            ),

            // Get a member from a struct by name.
            Arguments.of(
                Schema.structureBuilder(ShapeId.from("smithy.example#Foo"))
                    .putMember("foo", PreludeSchemas.STRING)
                    .putMember("bar", PreludeSchemas.STRING)
                    .build(),
                "b",
                (BiConsumer<Schema, ShapeSerializer>) (schema, s) -> {
                    s.writeStruct(schema, SerializableStruct.create(schema, (passedSchema, ser) -> {
                        ser.writeString(passedSchema.member("foo"), "a");
                        ser.writeString(passedSchema.member("bar"), "b");
                    }));
                },
                (Function<Document, Object>) d -> d.getMember("bar").asString()
            )
        );
    }
}
