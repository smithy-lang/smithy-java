/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

public class TypedDocumentMemberTest {
    // There's special handling for equality to account for the schema and normalized value.
    @Test
    public void equalityAndHashTest() {
        SerializableShape serializableShape = encoder -> {
            encoder.writeStruct(PreludeSchemas.DOCUMENT, s -> {
                var schema = PreludeSchemas.getSchemaForType(ShapeType.STRING);
                var member = schema.documentMember("foo");
                s.member(member, c -> c.writeString(PreludeSchemas.STRING, "Hi"));
            });
        };

        var document = Document.ofStruct(serializableShape);
        var documentCopy = Document.ofStruct(serializableShape);

        assertThat(document.getMember("foo"), equalTo(document.getMember("foo")));
        assertThat(document.getMember("foo"), equalTo(document.getMember("foo")));
        assertThat(document.getMember("foo"), equalTo(documentCopy.getMember("foo")));

        var fooMember = document.getMember("foo");
        assertThat(fooMember, equalTo(fooMember));

        assertThat(document.hashCode(), equalTo(documentCopy.hashCode()));
    }

    @Test
    public void inEqualityAndHashTest() {
        var document1 = Document.ofStruct(encoder -> {
            encoder.writeStruct(PreludeSchemas.DOCUMENT, s -> {
                var schema = PreludeSchemas.getSchemaForType(ShapeType.STRING);
                var member = schema.documentMember("foo", PreludeSchemas.STRING);
                s.member(member, c -> c.writeString(PreludeSchemas.STRING, "Hi"));
            });
        });

        var document2 = Document.ofStruct(encoder -> {
            encoder.writeStruct(PreludeSchemas.DOCUMENT, s -> {
                var schema = PreludeSchemas.getSchemaForType(ShapeType.STRING);
                var member = schema.documentMember("foo", PreludeSchemas.INTEGER);
                s.member(member, c -> c.writeInteger(PreludeSchemas.INTEGER, 1));
            });
        });

        assertThat(document1.getMember("foo"), not(equalTo(null)));
        assertThat(document1.getMember("foo"), not(equalTo(document2.getMember("foo"))));
    }

    @ParameterizedTest
    @MethodSource("convertsMemberProvider")
    public void convertsMember(
        ShapeType type,
        Object value,
        Consumer<ShapeSerializer> writer,
        Function<Document, Object> extractor
    ) {
        SerializableShape serializableShape = encoder -> {
            encoder.writeStruct(PreludeSchemas.DOCUMENT, s -> {
                var schema = PreludeSchemas.getSchemaForType(type);
                var aMember = SdkSchema.memberBuilder("a", schema).id(PreludeSchemas.DOCUMENT.id()).build();
                s.member(aMember, writer);
            });
        };
        var document = Document.ofStruct(serializableShape);

        assertThat(extractor.apply(document.getMember("a")), equalTo(value));
    }

    public static List<Arguments> convertsMemberProvider() {
        return List.of(
            Arguments.of(
                ShapeType.BOOLEAN,
                true,
                (Consumer<ShapeSerializer>) s -> s.writeBoolean(PreludeSchemas.BOOLEAN, true),
                (Function<Document, Object>) Document::asBoolean
            ),
            Arguments.of(
                ShapeType.STRING,
                "a",
                (Consumer<ShapeSerializer>) s -> s.writeString(PreludeSchemas.STRING, "a"),
                (Function<Document, Object>) Document::asString
            ),
            Arguments.of(
                ShapeType.STRING,
                "a".getBytes(StandardCharsets.UTF_8),
                (Consumer<ShapeSerializer>) s -> s.writeString(PreludeSchemas.STRING, "a"),
                (Function<Document, Object>) Document::asBlob
            ),
            Arguments.of(
                ShapeType.BLOB,
                "a",
                (Consumer<ShapeSerializer>) s -> s.writeBlob(PreludeSchemas.BLOB, "a".getBytes(StandardCharsets.UTF_8)),
                (Function<Document, Object>) Document::asString
            ),
            Arguments.of(
                ShapeType.BLOB,
                "a".getBytes(StandardCharsets.UTF_8),
                (Consumer<ShapeSerializer>) s -> s.writeBlob(PreludeSchemas.BLOB, "a".getBytes(StandardCharsets.UTF_8)),
                (Function<Document, Object>) Document::asBlob
            ),
            Arguments.of(
                ShapeType.TIMESTAMP,
                Instant.EPOCH,
                (Consumer<ShapeSerializer>) s -> s.writeTimestamp(PreludeSchemas.TIMESTAMP, Instant.EPOCH),
                (Function<Document, Object>) Document::asTimestamp
            ),

            // Test each combination of numbers and casting to other numbers.
            Arguments.of(
                ShapeType.BYTE,
                (byte) 1,
                (Consumer<ShapeSerializer>) s -> s.writeByte(PreludeSchemas.BYTE, (byte) 1),
                (Function<Document, Object>) Document::asByte
            ),
            Arguments.of(
                ShapeType.BYTE,
                (short) 1,
                (Consumer<ShapeSerializer>) s -> s.writeByte(PreludeSchemas.BYTE, (byte) 1),
                (Function<Document, Object>) Document::asShort
            ),
            Arguments.of(
                ShapeType.BYTE,
                1,
                (Consumer<ShapeSerializer>) s -> s.writeByte(PreludeSchemas.BYTE, (byte) 1),
                (Function<Document, Object>) Document::asInteger
            ),
            Arguments.of(
                ShapeType.BYTE,
                1L,
                (Consumer<ShapeSerializer>) s -> s.writeByte(PreludeSchemas.BYTE, (byte) 1),
                (Function<Document, Object>) Document::asLong
            ),
            Arguments.of(
                ShapeType.BYTE,
                1f,
                (Consumer<ShapeSerializer>) s -> s.writeByte(PreludeSchemas.BYTE, (byte) 1),
                (Function<Document, Object>) Document::asFloat
            ),
            Arguments.of(
                ShapeType.BYTE,
                1.0,
                (Consumer<ShapeSerializer>) s -> s.writeByte(PreludeSchemas.BYTE, (byte) 1),
                (Function<Document, Object>) Document::asDouble
            ),
            Arguments.of(
                ShapeType.BYTE,
                BigInteger.ONE,
                (Consumer<ShapeSerializer>) s -> s.writeByte(PreludeSchemas.BYTE, (byte) 1),
                (Function<Document, Object>) Document::asBigInteger
            ),
            Arguments.of(
                ShapeType.BYTE,
                BigDecimal.ONE,
                (Consumer<ShapeSerializer>) s -> s.writeByte(PreludeSchemas.BYTE, (byte) 1),
                (Function<Document, Object>) Document::asBigDecimal
            ),

            Arguments.of(
                ShapeType.SHORT,
                (byte) 1,
                (Consumer<ShapeSerializer>) s -> s.writeShort(PreludeSchemas.SHORT, (short) 1),
                (Function<Document, Object>) Document::asByte
            ),
            Arguments.of(
                ShapeType.SHORT,
                (short) 1,
                (Consumer<ShapeSerializer>) s -> s.writeShort(PreludeSchemas.SHORT, (short) 1),
                (Function<Document, Object>) Document::asShort
            ),
            Arguments.of(
                ShapeType.SHORT,
                1,
                (Consumer<ShapeSerializer>) s -> s.writeShort(PreludeSchemas.SHORT, (short) 1),
                (Function<Document, Object>) Document::asInteger
            ),
            Arguments.of(
                ShapeType.SHORT,
                1L,
                (Consumer<ShapeSerializer>) s -> s.writeShort(PreludeSchemas.SHORT, (short) 1),
                (Function<Document, Object>) Document::asLong
            ),
            Arguments.of(
                ShapeType.SHORT,
                1f,
                (Consumer<ShapeSerializer>) s -> s.writeShort(PreludeSchemas.SHORT, (short) 1),
                (Function<Document, Object>) Document::asFloat
            ),
            Arguments.of(
                ShapeType.SHORT,
                1.0,
                (Consumer<ShapeSerializer>) s -> s.writeShort(PreludeSchemas.SHORT, (short) 1),
                (Function<Document, Object>) Document::asDouble
            ),
            Arguments.of(
                ShapeType.SHORT,
                BigInteger.ONE,
                (Consumer<ShapeSerializer>) s -> s.writeShort(PreludeSchemas.SHORT, (short) 1),
                (Function<Document, Object>) Document::asBigInteger
            ),
            Arguments.of(
                ShapeType.SHORT,
                BigDecimal.ONE,
                (Consumer<ShapeSerializer>) s -> s.writeShort(PreludeSchemas.SHORT, (short) 1),
                (Function<Document, Object>) Document::asBigDecimal
            ),

            Arguments.of(
                ShapeType.INTEGER,
                (byte) 1,
                (Consumer<ShapeSerializer>) s -> s.writeInteger(PreludeSchemas.INTEGER, 1),
                (Function<Document, Object>) Document::asByte
            ),
            Arguments.of(
                ShapeType.INTEGER,
                (short) 1,
                (Consumer<ShapeSerializer>) s -> s.writeInteger(PreludeSchemas.INTEGER, 1),
                (Function<Document, Object>) Document::asShort
            ),
            Arguments.of(
                ShapeType.INTEGER,
                1,
                (Consumer<ShapeSerializer>) s -> s.writeInteger(PreludeSchemas.INTEGER, 1),
                (Function<Document, Object>) Document::asInteger
            ),
            Arguments.of(
                ShapeType.INTEGER,
                1L,
                (Consumer<ShapeSerializer>) s -> s.writeInteger(PreludeSchemas.INTEGER, 1),
                (Function<Document, Object>) Document::asLong
            ),
            Arguments.of(
                ShapeType.INTEGER,
                1f,
                (Consumer<ShapeSerializer>) s -> s.writeInteger(PreludeSchemas.INTEGER, 1),
                (Function<Document, Object>) Document::asFloat
            ),
            Arguments.of(
                ShapeType.INTEGER,
                1.0,
                (Consumer<ShapeSerializer>) s -> s.writeInteger(PreludeSchemas.INTEGER, 1),
                (Function<Document, Object>) Document::asDouble
            ),
            Arguments.of(
                ShapeType.INTEGER,
                BigInteger.ONE,
                (Consumer<ShapeSerializer>) s -> s.writeInteger(PreludeSchemas.INTEGER, 1),
                (Function<Document, Object>) Document::asBigInteger
            ),
            Arguments.of(
                ShapeType.INTEGER,
                BigDecimal.ONE,
                (Consumer<ShapeSerializer>) s -> s.writeInteger(PreludeSchemas.INTEGER, 1),
                (Function<Document, Object>) Document::asBigDecimal
            ),

            Arguments.of(
                ShapeType.LONG,
                (byte) 1,
                (Consumer<ShapeSerializer>) s -> s.writeLong(PreludeSchemas.LONG, 1),
                (Function<Document, Object>) Document::asByte
            ),
            Arguments.of(
                ShapeType.LONG,
                (short) 1,
                (Consumer<ShapeSerializer>) s -> s.writeLong(PreludeSchemas.LONG, 1),
                (Function<Document, Object>) Document::asShort
            ),
            Arguments.of(
                ShapeType.LONG,
                1,
                (Consumer<ShapeSerializer>) s -> s.writeLong(PreludeSchemas.LONG, 1),
                (Function<Document, Object>) Document::asInteger
            ),
            Arguments.of(
                ShapeType.LONG,
                1L,
                (Consumer<ShapeSerializer>) s -> s.writeLong(PreludeSchemas.LONG, 1),
                (Function<Document, Object>) Document::asLong
            ),
            Arguments.of(
                ShapeType.LONG,
                1f,
                (Consumer<ShapeSerializer>) s -> s.writeLong(PreludeSchemas.LONG, 1),
                (Function<Document, Object>) Document::asFloat
            ),
            Arguments.of(
                ShapeType.LONG,
                1.0,
                (Consumer<ShapeSerializer>) s -> s.writeLong(PreludeSchemas.LONG, 1),
                (Function<Document, Object>) Document::asDouble
            ),
            Arguments.of(
                ShapeType.LONG,
                BigInteger.ONE,
                (Consumer<ShapeSerializer>) s -> s.writeLong(PreludeSchemas.LONG, 1),
                (Function<Document, Object>) Document::asBigInteger
            ),
            Arguments.of(
                ShapeType.LONG,
                BigDecimal.ONE,
                (Consumer<ShapeSerializer>) s -> s.writeLong(PreludeSchemas.LONG, 1),
                (Function<Document, Object>) Document::asBigDecimal
            ),

            Arguments.of(
                ShapeType.FLOAT,
                (byte) 1,
                (Consumer<ShapeSerializer>) s -> s.writeFloat(PreludeSchemas.LONG, 1),
                (Function<Document, Object>) Document::asByte
            ),
            Arguments.of(
                ShapeType.FLOAT,
                (short) 1,
                (Consumer<ShapeSerializer>) s -> s.writeFloat(PreludeSchemas.LONG, 1),
                (Function<Document, Object>) Document::asShort
            ),
            Arguments.of(
                ShapeType.FLOAT,
                1,
                (Consumer<ShapeSerializer>) s -> s.writeFloat(PreludeSchemas.LONG, 1),
                (Function<Document, Object>) Document::asInteger
            ),
            Arguments.of(
                ShapeType.FLOAT,
                1L,
                (Consumer<ShapeSerializer>) s -> s.writeFloat(PreludeSchemas.LONG, 1),
                (Function<Document, Object>) Document::asLong
            ),
            Arguments.of(
                ShapeType.FLOAT,
                1f,
                (Consumer<ShapeSerializer>) s -> s.writeFloat(PreludeSchemas.LONG, 1),
                (Function<Document, Object>) Document::asFloat
            ),
            Arguments.of(
                ShapeType.FLOAT,
                1.0,
                (Consumer<ShapeSerializer>) s -> s.writeFloat(PreludeSchemas.LONG, 1),
                (Function<Document, Object>) Document::asDouble
            ),
            Arguments.of(
                ShapeType.FLOAT,
                BigInteger.ONE,
                (Consumer<ShapeSerializer>) s -> s.writeFloat(PreludeSchemas.LONG, 1),
                (Function<Document, Object>) Document::asBigInteger
            ),
            Arguments.of(
                ShapeType.FLOAT,
                BigDecimal.ONE,
                (Consumer<ShapeSerializer>) s -> s.writeFloat(PreludeSchemas.LONG, 1),
                (Function<Document, Object>) Document::asBigDecimal
            ),

            Arguments.of(
                ShapeType.DOUBLE,
                (byte) 1,
                (Consumer<ShapeSerializer>) s -> s.writeDouble(PreludeSchemas.DOUBLE, 1),
                (Function<Document, Object>) Document::asByte
            ),
            Arguments.of(
                ShapeType.DOUBLE,
                (short) 1,
                (Consumer<ShapeSerializer>) s -> s.writeDouble(PreludeSchemas.DOUBLE, 1),
                (Function<Document, Object>) Document::asShort
            ),
            Arguments.of(
                ShapeType.DOUBLE,
                1,
                (Consumer<ShapeSerializer>) s -> s.writeDouble(PreludeSchemas.DOUBLE, 1),
                (Function<Document, Object>) Document::asInteger
            ),
            Arguments.of(
                ShapeType.DOUBLE,
                1L,
                (Consumer<ShapeSerializer>) s -> s.writeDouble(PreludeSchemas.DOUBLE, 1),
                (Function<Document, Object>) Document::asLong
            ),
            Arguments.of(
                ShapeType.DOUBLE,
                1f,
                (Consumer<ShapeSerializer>) s -> s.writeDouble(PreludeSchemas.DOUBLE, 1),
                (Function<Document, Object>) Document::asFloat
            ),
            Arguments.of(
                ShapeType.DOUBLE,
                1.0,
                (Consumer<ShapeSerializer>) s -> s.writeDouble(PreludeSchemas.DOUBLE, 1),
                (Function<Document, Object>) Document::asDouble
            ),
            Arguments.of(
                ShapeType.DOUBLE,
                BigInteger.ONE,
                (Consumer<ShapeSerializer>) s -> s.writeDouble(PreludeSchemas.DOUBLE, 1),
                (Function<Document, Object>) Document::asBigInteger
            ),
            Arguments.of(
                ShapeType.DOUBLE,
                BigDecimal.ONE,
                (Consumer<ShapeSerializer>) s -> s.writeDouble(PreludeSchemas.DOUBLE, 1),
                (Function<Document, Object>) Document::asBigDecimal
            ),

            Arguments.of(
                ShapeType.BIG_INTEGER,
                (byte) 1,
                (Consumer<ShapeSerializer>) s -> s.writeBigInteger(PreludeSchemas.BIG_INTEGER, BigInteger.ONE),
                (Function<Document, Object>) Document::asByte
            ),
            Arguments.of(
                ShapeType.BIG_INTEGER,
                (short) 1,
                (Consumer<ShapeSerializer>) s -> s.writeBigInteger(PreludeSchemas.BIG_INTEGER, BigInteger.ONE),
                (Function<Document, Object>) Document::asShort
            ),
            Arguments.of(
                ShapeType.BIG_INTEGER,
                1,
                (Consumer<ShapeSerializer>) s -> s.writeBigInteger(PreludeSchemas.BIG_INTEGER, BigInteger.ONE),
                (Function<Document, Object>) Document::asInteger
            ),
            Arguments.of(
                ShapeType.BIG_INTEGER,
                1L,
                (Consumer<ShapeSerializer>) s -> s.writeBigInteger(PreludeSchemas.BIG_INTEGER, BigInteger.ONE),
                (Function<Document, Object>) Document::asLong
            ),
            Arguments.of(
                ShapeType.BIG_INTEGER,
                1f,
                (Consumer<ShapeSerializer>) s -> s.writeBigInteger(PreludeSchemas.BIG_INTEGER, BigInteger.ONE),
                (Function<Document, Object>) Document::asFloat
            ),
            Arguments.of(
                ShapeType.BIG_INTEGER,
                1.0,
                (Consumer<ShapeSerializer>) s -> s.writeBigInteger(PreludeSchemas.BIG_INTEGER, BigInteger.ONE),
                (Function<Document, Object>) Document::asDouble
            ),
            Arguments.of(
                ShapeType.BIG_INTEGER,
                BigInteger.ONE,
                (Consumer<ShapeSerializer>) s -> s.writeBigInteger(PreludeSchemas.BIG_INTEGER, BigInteger.ONE),
                (Function<Document, Object>) Document::asBigInteger
            ),
            Arguments.of(
                ShapeType.BIG_INTEGER,
                BigDecimal.ONE,
                (Consumer<ShapeSerializer>) s -> s.writeBigInteger(PreludeSchemas.BIG_INTEGER, BigInteger.ONE),
                (Function<Document, Object>) Document::asBigDecimal
            ),

            Arguments.of(
                ShapeType.BIG_DECIMAL,
                (byte) 1,
                (Consumer<ShapeSerializer>) s -> s.writeBigDecimal(PreludeSchemas.BIG_DECIMAL, BigDecimal.ONE),
                (Function<Document, Object>) Document::asByte
            ),
            Arguments.of(
                ShapeType.BIG_DECIMAL,
                (short) 1,
                (Consumer<ShapeSerializer>) s -> s.writeBigDecimal(PreludeSchemas.BIG_DECIMAL, BigDecimal.ONE),
                (Function<Document, Object>) Document::asShort
            ),
            Arguments.of(
                ShapeType.BIG_DECIMAL,
                1,
                (Consumer<ShapeSerializer>) s -> s.writeBigDecimal(PreludeSchemas.BIG_DECIMAL, BigDecimal.ONE),
                (Function<Document, Object>) Document::asInteger
            ),
            Arguments.of(
                ShapeType.BIG_DECIMAL,
                1L,
                (Consumer<ShapeSerializer>) s -> s.writeBigDecimal(PreludeSchemas.BIG_DECIMAL, BigDecimal.ONE),
                (Function<Document, Object>) Document::asLong
            ),
            Arguments.of(
                ShapeType.BIG_DECIMAL,
                1f,
                (Consumer<ShapeSerializer>) s -> s.writeBigDecimal(PreludeSchemas.BIG_DECIMAL, BigDecimal.ONE),
                (Function<Document, Object>) Document::asFloat
            ),
            Arguments.of(
                ShapeType.BIG_DECIMAL,
                1.0,
                (Consumer<ShapeSerializer>) s -> s.writeBigDecimal(PreludeSchemas.BIG_DECIMAL, BigDecimal.ONE),
                (Function<Document, Object>) Document::asDouble
            ),
            Arguments.of(
                ShapeType.BIG_DECIMAL,
                BigInteger.ONE,
                (Consumer<ShapeSerializer>) s -> s.writeBigDecimal(PreludeSchemas.BIG_DECIMAL, BigDecimal.ONE),
                (Function<Document, Object>) Document::asBigInteger
            ),
            Arguments.of(
                ShapeType.BIG_DECIMAL,
                BigDecimal.ONE,
                (Consumer<ShapeSerializer>) s -> s.writeBigDecimal(PreludeSchemas.BIG_DECIMAL, BigDecimal.ONE),
                (Function<Document, Object>) Document::asBigDecimal
            ),

            // Get the document as a list. Typed equality needs normalization through Document.ofValue().
            Arguments.of(
                ShapeType.DOCUMENT,
                List.of(Document.of(1)),
                (Consumer<ShapeSerializer>) s -> s.writeList(
                    PreludeSchemas.DOCUMENT,
                    c -> c.writeInteger(PreludeSchemas.INTEGER, 1)
                ),
                (Function<Document, Object>) d -> Document.ofValue(Document.of(d.asList())).asList()
            ),

            // Get the document as a string map. Typed equality needs normalization through Document.ofValue().
            Arguments.of(
                ShapeType.DOCUMENT,
                Map.of(Document.of("a"), Document.of(1)),
                (Consumer<ShapeSerializer>) s -> s.writeMap(
                    PreludeSchemas.DOCUMENT,
                    m -> m.entry("a", c -> c.writeInteger(PreludeSchemas.INTEGER, 1))
                ),
                (Function<Document, Object>) d -> Document.ofValue(
                    Document.ofMap(d.asMap())
                ).asMap()
            ),

            // Get the document as an integer map. Typed equality needs normalization through Document.ofValue().
            Arguments.of(
                ShapeType.DOCUMENT,
                Map.of(Document.of(1), Document.of(1)),
                (Consumer<ShapeSerializer>) s -> s.writeMap(
                    PreludeSchemas.DOCUMENT,
                    m -> m.entry(1, c -> c.writeInteger(PreludeSchemas.INTEGER, 1))
                ),
                (Function<Document, Object>) d -> Document.ofValue(
                    Document.ofMap(d.asMap())
                ).asMap()
            ),

            // Get the document as a long map. Typed equality needs normalization through Document.ofValue().
            Arguments.of(
                ShapeType.DOCUMENT,
                Map.of(Document.of(1L), Document.of(1)),
                (Consumer<ShapeSerializer>) s -> s.writeMap(
                    PreludeSchemas.DOCUMENT,
                    m -> m.entry(1L, c -> c.writeInteger(PreludeSchemas.INTEGER, 1))
                ),
                (Function<Document, Object>) d -> Document.ofValue(
                    Document.ofMap(d.asMap())
                ).asMap()
            ),

            // Get a member from a map by name. Typed equality needs normalization through Document.ofValue().
            Arguments.of(
                ShapeType.DOCUMENT,
                Document.of(1),
                (Consumer<ShapeSerializer>) s -> s.writeMap(
                    PreludeSchemas.DOCUMENT,
                    m -> m.entry("a", c -> c.writeInteger(PreludeSchemas.INTEGER, 1))
                ),
                (Function<Document, Object>) d -> Document.ofValue(d.getMember("a"))
            ),

            // Get a member from a struct by name. Typed equality needs normalization through Document.ofValue().
            Arguments.of(
                ShapeType.DOCUMENT,
                Document.of("b"),
                (Consumer<ShapeSerializer>) s -> {
                    s.writeStruct(PreludeSchemas.DOCUMENT, ser -> {
                        ser.member(
                            PreludeSchemas.DOCUMENT.documentMember("foo"),
                            c -> c.writeString(PreludeSchemas.STRING, "a")
                        );
                        ser.member(
                            PreludeSchemas.DOCUMENT.documentMember("bar"),
                            c -> c.writeString(PreludeSchemas.STRING, "b")
                        );
                    });
                },
                (Function<Document, Object>) d -> Document.ofValue(d.getMember("bar"))
            )
        );
    }
}
