/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.java.runtime.core.testmodels.Person;
import software.amazon.smithy.model.shapes.ShapeType;

public class DocumentTest {
    @Test
    public void getAsNumber() {
        assertThat(Document.createByte((byte) 1).asNumber(), equalTo(Byte.valueOf((byte) 1)));
        assertThat(Document.createShort((short) 1).asNumber(), equalTo(Short.valueOf((short) 1)));
        assertThat(Document.createInteger(1).asNumber(), equalTo(Integer.valueOf(1)));
        assertThat(Document.createLong(1L).asNumber(), equalTo(Long.valueOf(1L)));
        assertThat(Document.createFloat(1f).asNumber(), equalTo(Float.valueOf(1f)));
        assertThat(Document.createDouble(1.0).asNumber(), equalTo(Double.valueOf(1.0)));
        assertThat(Document.createBigDecimal(new BigDecimal(1)).asNumber(), equalTo(new BigDecimal(1)));
        assertThat(Document.createBigInteger(BigInteger.valueOf(1)).asNumber(), equalTo(BigInteger.valueOf(1)));
    }

    @ParameterizedTest
    @MethodSource("defaultSerializationProvider")
    public void defaultSerialization(Document value) {
        var expected = ToStringSerializer.serialize(value);
        var toString = new ToStringSerializer();
        value.serialize(toString);

        assertThat(expected, equalTo(toString.toString()));
    }

    public static List<Arguments> defaultSerializationProvider() {
        return List.of(
            Arguments.of(Document.createByte((byte) 1)),
            Arguments.of(Document.createShort((short) 1)),
            Arguments.of(Document.createInteger(1)),
            Arguments.of(Document.createLong(1L)),
            Arguments.of(Document.createFloat(1f)),
            Arguments.of(Document.createDouble(1.0)),
            Arguments.of(Document.createBigInteger(BigInteger.valueOf(1))),
            Arguments.of(Document.createBigDecimal(new BigDecimal(1))),
            Arguments.of(Document.createString("a")),
            Arguments.of(Document.createBlob("a".getBytes(StandardCharsets.UTF_8))),
            Arguments.of(Document.createBoolean(true)),
            Arguments.of(Document.createTimestamp(Instant.EPOCH)),
            Arguments.of(Document.createList(List.of(Document.createInteger(1), Document.createString("a"))))
        );
    }

    @ParameterizedTest
    @MethodSource("onesProvider")
    public void castNumbersToAllTypes(Document value) {
        assertThat(value.asByte(), is((byte) 1));
        assertThat(value.asShort(), is((short) 1));
        assertThat(value.asInteger(), is(1));
        assertThat(value.asLong(), is(1L));
        assertThat(value.asFloat(), is(1f));
        assertThat(value.asDouble(), is(1.0));
        assertThat(value.asBigInteger(), equalTo(BigInteger.valueOf(1)));
        assertThat(value.asBigDecimal(), equalTo(BigDecimal.valueOf(1)));
    }

    public static List<Arguments> onesProvider() {
        return List.of(
            Arguments.of(Document.createByte((byte) 1)),
            Arguments.of(Document.createShort((short) 1)),
            Arguments.of(Document.createInteger(1)),
            Arguments.of(Document.createLong(1L)),
            Arguments.of(Document.createFloat(1f)),
            Arguments.of(Document.createDouble(1.0)),
            Arguments.of(Document.createBigInteger(BigInteger.valueOf(1))),
            Arguments.of(Document.createBigDecimal(new BigDecimal(1)))
        );
    }

    @ParameterizedTest
    @MethodSource("invalidConversionSupplier")
    public void throwsOnInvalidConversion(Document value, Consumer<Document> call) {
        Assertions.assertThrows(SerializationException.class, () -> call.accept(value));
    }

    public static List<Arguments> invalidConversionSupplier() {
        return List.of(
            Arguments.of(Document.createString("a"), (Consumer<Document>) Document::asNumber),
            Arguments.of(Document.createInteger(1), (Consumer<Document>) Document::asString),
            Arguments.of(Document.createString("a"), (Consumer<Document>) Document::asBoolean),
            Arguments.of(Document.createString("a"), (Consumer<Document>) Document::asTimestamp),
            Arguments.of(Document.createString("a"), (Consumer<Document>) Document::asByte),
            Arguments.of(Document.createString("a"), (Consumer<Document>) Document::asShort),
            Arguments.of(Document.createString("a"), (Consumer<Document>) Document::asInteger),
            Arguments.of(Document.createString("a"), (Consumer<Document>) Document::asLong),
            Arguments.of(Document.createString("a"), (Consumer<Document>) Document::asFloat),
            Arguments.of(Document.createString("a"), (Consumer<Document>) Document::asDouble),
            Arguments.of(Document.createString("a"), (Consumer<Document>) Document::asBigDecimal),
            Arguments.of(Document.createString("a"), (Consumer<Document>) Document::asBigInteger),
            Arguments.of(Document.createString("a"), (Consumer<Document>) Document::asList),
            Arguments.of(Document.createString("a"), (Consumer<Document>) Document::asStringMap),
            Arguments.of(Document.createInteger(1), (Consumer<Document>) Document::asBlob),
            Arguments.of(Document.createInteger(1), (Consumer<Document>) d -> d.getMember("a"))
        );
    }

    @ParameterizedTest
    @MethodSource("ofValueProvider")
    public void documentFromValue(Document value, Object expected, Function<Document, Object> extractor) {
        var document = Document.createTyped(value);
        var extracted = extractor.apply(document);
        assertThat(extracted, equalTo(expected));
    }

    public static List<Arguments> ofValueProvider() {
        return List.of(
            Arguments.of(Document.createString("a"), "a", (Function<Document, Object>) Document::asString),
            Arguments.of(Document.createByte((byte) 1), (byte) 1, (Function<Document, Object>) Document::asByte),
            Arguments.of(Document.createShort((short) 1), (short) 1, (Function<Document, Object>) Document::asShort),
            Arguments.of(Document.createInteger(1), 1, (Function<Document, Object>) Document::asInteger),
            Arguments.of(Document.createLong(1L), 1L, (Function<Document, Object>) Document::asLong),
            Arguments.of(Document.createFloat(1f), 1f, (Function<Document, Object>) Document::asFloat),
            Arguments.of(Document.createDouble(1.0), 1.0, (Function<Document, Object>) Document::asDouble),
            Arguments.of(
                Document.createBigInteger(BigInteger.valueOf(1)),
                BigInteger.valueOf(1),
                (Function<Document, Object>) Document::asBigInteger
            ),
            Arguments.of(
                Document.createBigDecimal(new BigDecimal(1)),
                new BigDecimal(1),
                (Function<Document, Object>) Document::asBigDecimal
            ),
            Arguments.of(Document.createBoolean(true), true, (Function<Document, Object>) Document::asBoolean),
            Arguments.of(
                Document.createBlob("a".getBytes(StandardCharsets.UTF_8)),
                "a".getBytes(StandardCharsets.UTF_8),
                (Function<Document, Object>) Document::asBlob
            ),
            Arguments.of(
                Document.createTimestamp(Instant.EPOCH),
                Instant.EPOCH,
                (Function<Document, Object>) Document::asTimestamp
            ),
            Arguments.of(
                Document.createList(List.of(Document.createInteger(1), Document.createInteger(2))),
                List.of(Document.createInteger(1), Document.createInteger(2)),
                (Function<Document, Object>) Document::asList
            ),
            Arguments.of(
                Document.createStringMap(Map.of("A", Document.createInteger(1))),
                Map.of("A", Document.createInteger(1)),
                (Function<Document, Object>) Document::asStringMap
            )
        );
    }

    @Test
    public void createDocumentFromStruct() {
        var person = Person.builder()
            .name("A")
            .age(1)
            .binary("hi".getBytes(StandardCharsets.UTF_8))
            .birthday(Instant.EPOCH)
            .build();
        var doc = Document.createTyped(person);

        assertThat(doc.getMember("__type").asString(), equalTo(Person.ID.toString()));
        assertThat(doc.getMember("name").asString(), equalTo("A"));
        assertThat(doc.getMember("age").asInteger(), equalTo(1));
        assertThat(doc.getMember("binary").asBlob(), equalTo("hi".getBytes(StandardCharsets.UTF_8)));
        assertThat(doc.getMember("birthday").asTimestamp(), equalTo(Instant.EPOCH));
    }

    @ParameterizedTest
    @MethodSource("deserializesDocumentProvider")
    public void deserializesDocument(
        Document value,
        Object expected,
        Function<DocumentDeserializer, Object> extractor
    ) {
        DocumentDeserializer deserializer = new DocumentDeserializer(value);
        assertThat(extractor.apply(deserializer), equalTo(expected));
    }

    public static List<Arguments> deserializesDocumentProvider() {
        return List.of(
            Arguments.of(
                Document.createString("a"),
                "a",
                (Function<ShapeDeserializer, Object>) s -> s.readString(PreludeSchemas.STRING)
            ),
            Arguments.of(
                Document.createBlob("a".getBytes(StandardCharsets.UTF_8)),
                "a".getBytes(StandardCharsets.UTF_8),
                (Function<ShapeDeserializer, Object>) s -> s.readBlob(PreludeSchemas.BLOB)
            ),
            Arguments.of(
                Document.createBoolean(true),
                true,
                (Function<ShapeDeserializer, Object>) s -> s.readBoolean(PreludeSchemas.BOOLEAN)
            ),
            Arguments.of(
                Document.createByte((byte) 1),
                (byte) 1,
                (Function<ShapeDeserializer, Object>) s -> s.readByte(PreludeSchemas.BYTE)
            ),
            Arguments.of(
                Document.createShort((short) 1),
                (short) 1,
                (Function<ShapeDeserializer, Object>) s -> s.readShort(PreludeSchemas.SHORT)
            ),
            Arguments.of(
                Document.createInteger(1),
                1,
                (Function<ShapeDeserializer, Object>) s -> s.readInteger(PreludeSchemas.INTEGER)
            ),
            Arguments.of(
                Document.createLong(1L),
                1L,
                (Function<ShapeDeserializer, Object>) s -> s.readLong(PreludeSchemas.LONG)
            ),
            Arguments.of(
                Document.createFloat(1f),
                1f,
                (Function<ShapeDeserializer, Object>) s -> s.readFloat(PreludeSchemas.FLOAT)
            ),
            Arguments.of(
                Document.createDouble(1.0),
                1.0,
                (Function<ShapeDeserializer, Object>) s -> s.readDouble(PreludeSchemas.DOUBLE)
            ),
            Arguments.of(
                Document.createBigInteger(BigInteger.ONE),
                BigInteger.ONE,
                (Function<ShapeDeserializer, Object>) s -> s.readBigInteger(PreludeSchemas.BIG_INTEGER)
            ),
            Arguments.of(
                Document.createBigDecimal(BigDecimal.ONE),
                BigDecimal.ONE,
                (Function<ShapeDeserializer, Object>) s -> s.readBigDecimal(PreludeSchemas.BIG_DECIMAL)
            ),
            Arguments.of(
                Document.createString("a"),
                Document.createString("a"),
                (Function<ShapeDeserializer, Object>) ShapeDeserializer::readDocument
            ),
            Arguments.of(
                Document.createTimestamp(Instant.EPOCH),
                Instant.EPOCH,
                (Function<ShapeDeserializer, Object>) s -> s.readTimestamp(PreludeSchemas.TIMESTAMP)
            )
        );
    }

    @Test
    public void deserializesListDocuments() {
        Document value = Document.createList(List.of(Document.createString("a"), Document.createString("b")));
        DocumentDeserializer deserializer = new DocumentDeserializer(value);
        List<String> result = new ArrayList<>();

        deserializer.readList(PreludeSchemas.DOCUMENT, result, (listResult, c) -> {
            listResult.add(c.readString(PreludeSchemas.STRING));
        });

        assertThat(result, contains("a", "b"));
    }

    @Test
    public void deserializesStringMap() {
        Document value = Document.createStringMap(Map.of("a", Document.createString("v")));
        DocumentDeserializer deserializer = new DocumentDeserializer(value);
        Map<String, String> result = new HashMap<>();

        deserializer.readStringMap(
            PreludeSchemas.DOCUMENT,
            result,
            (resultMap, k, v) -> resultMap.put(k, v.readString(PreludeSchemas.STRING))
        );

        assertThat(result, equalTo(Map.of("a", "v")));
    }

    @Test
    public void throwsWhenDocumentWritesNothing() {
        var e = Assertions.assertThrows(SerializationException.class, () -> {
            var document = Document.createTyped(encoder -> {});
            // Trigger the lazy document to create the underlying document.
            document.getMember("hello!");
        });

        assertThat(
            e.getMessage(),
            containsString("Unable to create a document from ShapeSerializer that serialized nothing")
        );
    }

    @ParameterizedTest
    @MethodSource("normalizedEqualsTestProvider")
    public void normalizedEqualsTest(Document value) {
        var other = new DifferentDocument(value);

        assertThat(other, not(equalTo(value)));
        assertThat(Document.equals(other, value), is(true));
    }

    static List<Arguments> normalizedEqualsTestProvider() {
        return List.of(
            Arguments.of(Document.createString("hi")),
            Arguments.of(Document.createBlob("hi".getBytes(StandardCharsets.UTF_8))),
            Arguments.of(Document.createByte((byte) 1)),
            Arguments.of(Document.createShort((short) 1)),
            Arguments.of(Document.createInteger(1)),
            Arguments.of(Document.createLong(1L)),
            Arguments.of(Document.createFloat(1f)),
            Arguments.of(Document.createDouble(1.0)),
            Arguments.of(Document.createBigInteger(BigInteger.ONE)),
            Arguments.of(Document.createBigDecimal(BigDecimal.ONE)),
            Arguments.of(Document.createBoolean(true)),
            Arguments.of(Document.createStringMap(Map.of("hi", Document.createString("there")))),
            Arguments.of(Document.createList(List.of(Document.createString("hi")))),
            Arguments.of(Document.createTimestamp(Instant.EPOCH))
        );
    }

    @ParameterizedTest
    @MethodSource("inEqualDocumentsTestProvider")
    public void inEqualDocumentsTest(Document a, Document b) {
        assertThat(Document.equals(a, b), is(false));
    }

    public static List<Arguments> inEqualDocumentsTestProvider() {
        return List.of(
            Arguments.of(Document.createString("a"), Document.createInteger(1)),
            Arguments.of(Document.createList(List.of(Document.createInteger(1))), Document.createList(List.of())),
            Arguments.of(
                Document.createList(List.of(Document.createInteger(1))),
                Document.createList(List.of(Document.createInteger(2)))
            ),
            Arguments.of(
                Document.createStringMap(Map.of("a", Document.createString("a"))),
                Document.createStringMap(Map.of())
            ),
            Arguments.of(
                Document.createStringMap(Map.of("a", Document.createString("a"))),
                Document.createStringMap(Map.of("a", Document.createString("b")))
            )
        );
    }

    static final class DifferentDocument implements Document {

        private final Document delegate;

        DifferentDocument(Document delegate) {
            this.delegate = delegate;
        }

        private Document getDocument() {
            return delegate;
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            delegate.serialize(serializer);
        }

        @Override
        public ShapeType type() {
            return getDocument().type();
        }

        @Override
        public boolean asBoolean() {
            return getDocument().asBoolean();
        }

        @Override
        public byte asByte() {
            return getDocument().asByte();
        }

        @Override
        public short asShort() {
            return getDocument().asShort();
        }

        @Override
        public int asInteger() {
            return getDocument().asInteger();
        }

        @Override
        public long asLong() {
            return getDocument().asLong();
        }

        @Override
        public float asFloat() {
            return getDocument().asFloat();
        }

        @Override
        public double asDouble() {
            return getDocument().asDouble();
        }

        @Override
        public BigInteger asBigInteger() {
            return getDocument().asBigInteger();
        }

        @Override
        public BigDecimal asBigDecimal() {
            return getDocument().asBigDecimal();
        }

        @Override
        public Number asNumber() {
            return getDocument().asNumber();
        }

        @Override
        public String asString() {
            return getDocument().asString();
        }

        @Override
        public byte[] asBlob() {
            return getDocument().asBlob();
        }

        @Override
        public Instant asTimestamp() {
            return getDocument().asTimestamp();
        }

        @Override
        public List<Document> asList() {
            return getDocument().asList();
        }

        @Override
        public Map<String, Document> asStringMap() {
            return getDocument().asStringMap();
        }

        @Override
        public Document getMember(String memberName) {
            return getDocument().getMember(memberName);
        }

        @Override
        public String toString() {
            return getDocument().toString();
        }

        @Override
        public boolean equals(Object obj) {
            // Just for test purposes.
            return false;
        }

        @Override
        public int hashCode() {
            return getDocument().hashCode();
        }
    }
}
