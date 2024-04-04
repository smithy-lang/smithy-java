/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

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
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.java.runtime.core.testmodels.Person;

public class DocumentTest {
    @Test
    public void getAsNumber() {
        assertThat(Document.of((byte) 1).asNumber(), equalTo(Byte.valueOf((byte) 1)));
        assertThat(Document.of((short) 1).asNumber(), equalTo(Short.valueOf((short) 1)));
        assertThat(Document.of(1).asNumber(), equalTo(Integer.valueOf(1)));
        assertThat(Document.of(1L).asNumber(), equalTo(Long.valueOf(1L)));
        assertThat(Document.of(1f).asNumber(), equalTo(Float.valueOf(1f)));
        assertThat(Document.of(1.0).asNumber(), equalTo(Double.valueOf(1.0)));
        assertThat(Document.of(new BigDecimal(1)).asNumber(), equalTo(new BigDecimal(1)));
        assertThat(Document.of(BigInteger.valueOf(1)).asNumber(), equalTo(BigInteger.valueOf(1)));
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
            Arguments.of(Document.of((byte) 1)),
            Arguments.of(Document.of((short) 1)),
            Arguments.of(Document.of(1)),
            Arguments.of(Document.of(1L)),
            Arguments.of(Document.of(1f)),
            Arguments.of(Document.of(1.0)),
            Arguments.of(Document.of(BigInteger.valueOf(1))),
            Arguments.of(Document.of(new BigDecimal(1))),
            Arguments.of(Document.of("a")),
            Arguments.of(Document.of("a".getBytes(StandardCharsets.UTF_8))),
            Arguments.of(Document.of(true)),
            Arguments.of(Document.of(Instant.EPOCH)),
            Arguments.of(Document.of(List.of(Document.of(1), Document.of("a"))))
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
            Arguments.of(Document.of((byte) 1)),
            Arguments.of(Document.of((short) 1)),
            Arguments.of(Document.of(1)),
            Arguments.of(Document.of(1L)),
            Arguments.of(Document.of(1f)),
            Arguments.of(Document.of(1.0)),
            Arguments.of(Document.of(BigInteger.valueOf(1))),
            Arguments.of(Document.of(new BigDecimal(1)))
        );
    }

    @Test
    public void convertsStringToBytes() {
        var doc = Document.of("Hi");

        assertThat(doc.asBlob(), equalTo("Hi".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void convertsByteToString() {
        var doc = Document.of("Hi".getBytes(StandardCharsets.UTF_8));

        assertThat(doc.asString(), equalTo("Hi"));
    }

    @ParameterizedTest
    @MethodSource("invalidConversionSupplier")
    public void throwsOnInvalidConversion(Document value, Consumer<Document> call) {
        Assertions.assertThrows(SdkSerdeException.class, () -> call.accept(value));
    }

    public static List<Arguments> invalidConversionSupplier() {
        return List.of(
            Arguments.of(Document.of("a"), (Consumer<Document>) Document::asNumber),
            Arguments.of(Document.of(1), (Consumer<Document>) Document::asString),
            Arguments.of(Document.of("a"), (Consumer<Document>) Document::asBoolean),
            Arguments.of(Document.of("a"), (Consumer<Document>) Document::asTimestamp),
            Arguments.of(Document.of("a"), (Consumer<Document>) Document::asByte),
            Arguments.of(Document.of("a"), (Consumer<Document>) Document::asShort),
            Arguments.of(Document.of("a"), (Consumer<Document>) Document::asInteger),
            Arguments.of(Document.of("a"), (Consumer<Document>) Document::asLong),
            Arguments.of(Document.of("a"), (Consumer<Document>) Document::asFloat),
            Arguments.of(Document.of("a"), (Consumer<Document>) Document::asDouble),
            Arguments.of(Document.of("a"), (Consumer<Document>) Document::asBigDecimal),
            Arguments.of(Document.of("a"), (Consumer<Document>) Document::asBigInteger),
            Arguments.of(Document.of("a"), (Consumer<Document>) Document::asList),
            Arguments.of(Document.of("a"), (Consumer<Document>) Document::asMap),
            Arguments.of(Document.of(1), (Consumer<Document>) Document::asBlob),
            Arguments.of(Document.of(1), (Consumer<Document>) d -> d.getMember("a"))
        );
    }

    @ParameterizedTest
    @MethodSource("ofValueProvider")
    public void documentFromValue(Document value, Object expected, Function<Document, Object> extractor) {
        assertThat(extractor.apply(Document.ofValue(value)), equalTo(expected));
    }

    public static List<Arguments> ofValueProvider() {
        return List.of(
            Arguments.of(Document.of("a"), "a", (Function<Document, Object>) Document::asString),
            Arguments.of(Document.of((byte) 1), (byte) 1, (Function<Document, Object>) Document::asByte),
            Arguments.of(Document.of((short) 1), (short) 1, (Function<Document, Object>) Document::asShort),
            Arguments.of(Document.of(1), 1, (Function<Document, Object>) Document::asInteger),
            Arguments.of(Document.of(1L), 1L, (Function<Document, Object>) Document::asLong),
            Arguments.of(Document.of(1f), 1f, (Function<Document, Object>) Document::asFloat),
            Arguments.of(Document.of(1.0), 1.0, (Function<Document, Object>) Document::asDouble),
            Arguments.of(
                Document.of(BigInteger.valueOf(1)),
                BigInteger.valueOf(1),
                (Function<Document, Object>) Document::asBigInteger
            ),
            Arguments.of(
                Document.of(new BigDecimal(1)),
                new BigDecimal(1),
                (Function<Document, Object>) Document::asBigDecimal
            ),
            Arguments.of(Document.of(true), true, (Function<Document, Object>) Document::asBoolean),
            Arguments.of(
                Document.of("a".getBytes(StandardCharsets.UTF_8)),
                "a".getBytes(StandardCharsets.UTF_8),
                (Function<Document, Object>) Document::asBlob
            ),
            Arguments.of(Document.of(Instant.EPOCH), Instant.EPOCH, (Function<Document, Object>) Document::asTimestamp),
            Arguments.of(
                Document.of(List.of(Document.of(1), Document.of(2))),
                List.of(Document.of(1), Document.of(2)),
                (Function<Document, Object>) Document::asList
            ),
            Arguments.of(
                Document.ofMap(Map.of(Document.of("A"), Document.of(1))),
                Map.of(Document.of("A"), Document.of(1)),
                (Function<Document, Object>) Document::asMap
            ),
            Arguments.of(
                Document.ofMap(Map.of(Document.of(1), Document.of(1))),
                Map.of(Document.of(1), Document.of(1)),
                (Function<Document, Object>) Document::asMap
            ),
            Arguments.of(
                Document.ofMap(Map.of(Document.of(1L), Document.of(1))),
                Map.of(Document.of(1L), Document.of(1)),
                (Function<Document, Object>) Document::asMap
            ),
            Arguments.of(
                Document.ofStruct(Map.of("A", Document.of(1))),
                Map.of(Document.of("A"), Document.of(1)),
                (Function<Document, Object>) Document::asMap
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
        var doc = Document.ofStruct(person);

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
                Document.of("a"),
                "a",
                (Function<ShapeDeserializer, Object>) s -> s.readString(PreludeSchemas.STRING)
            ),
            Arguments.of(
                Document.of("a".getBytes(StandardCharsets.UTF_8)),
                "a".getBytes(StandardCharsets.UTF_8),
                (Function<ShapeDeserializer, Object>) s -> s.readBlob(PreludeSchemas.BLOB)
            ),
            Arguments.of(
                Document.of(true),
                true,
                (Function<ShapeDeserializer, Object>) s -> s.readBoolean(PreludeSchemas.BOOLEAN)
            ),
            Arguments.of(
                Document.of((byte) 1),
                (byte) 1,
                (Function<ShapeDeserializer, Object>) s -> s.readByte(PreludeSchemas.BYTE)
            ),
            Arguments.of(
                Document.of((short) 1),
                (short) 1,
                (Function<ShapeDeserializer, Object>) s -> s.readShort(PreludeSchemas.SHORT)
            ),
            Arguments.of(
                Document.of(1),
                1,
                (Function<ShapeDeserializer, Object>) s -> s.readInteger(PreludeSchemas.INTEGER)
            ),
            Arguments.of(
                Document.of(1L),
                1L,
                (Function<ShapeDeserializer, Object>) s -> s.readLong(PreludeSchemas.LONG)
            ),
            Arguments.of(
                Document.of(1f),
                1f,
                (Function<ShapeDeserializer, Object>) s -> s.readFloat(PreludeSchemas.FLOAT)
            ),
            Arguments.of(
                Document.of(1.0),
                1.0,
                (Function<ShapeDeserializer, Object>) s -> s.readDouble(PreludeSchemas.DOUBLE)
            ),
            Arguments.of(
                Document.of(BigInteger.ONE),
                BigInteger.ONE,
                (Function<ShapeDeserializer, Object>) s -> s.readBigInteger(PreludeSchemas.BIG_INTEGER)
            ),
            Arguments.of(
                Document.of(BigDecimal.ONE),
                BigDecimal.ONE,
                (Function<ShapeDeserializer, Object>) s -> s.readBigDecimal(PreludeSchemas.BIG_DECIMAL)
            ),
            Arguments.of(
                Document.of("a"),
                Document.of("a"),
                (Function<ShapeDeserializer, Object>) ShapeDeserializer::readDocument
            ),
            Arguments.of(
                Document.of(Instant.EPOCH),
                Instant.EPOCH,
                (Function<ShapeDeserializer, Object>) s -> s.readTimestamp(PreludeSchemas.TIMESTAMP)
            )
        );
    }

    @Test
    public void deserializesListDocuments() {
        Document value = Document.of(List.of(Document.of("a"), Document.of("b")));
        DocumentDeserializer deserializer = new DocumentDeserializer(value);
        List<String> result = new ArrayList<>();

        deserializer.readList(PreludeSchemas.DOCUMENT, c -> result.add(c.readString(PreludeSchemas.STRING)));

        assertThat(result, contains("a", "b"));
    }

    @Test
    public void deserializesStringMap() {
        Document value = Document.ofMap(Map.of(Document.of("a"), Document.of("v")));
        DocumentDeserializer deserializer = new DocumentDeserializer(value);
        Map<String, String> result = new HashMap<>();

        deserializer.readStringMap(
            PreludeSchemas.DOCUMENT,
            (k, v) -> result.put(k, v.readString(PreludeSchemas.STRING))
        );

        assertThat(result, equalTo(Map.of("a", "v")));
    }

    @Test
    public void deserializesIntMap() {
        Document value = Document.ofMap(Map.of(Document.of(1), Document.of("v")));
        DocumentDeserializer deserializer = new DocumentDeserializer(value);
        Map<Integer, String> result = new HashMap<>();

        deserializer.readIntMap(PreludeSchemas.DOCUMENT, (k, v) -> result.put(k, v.readString(PreludeSchemas.STRING)));

        assertThat(result, equalTo(Map.of(1, "v")));
    }

    @Test
    public void deserializesLongMap() {
        Document value = Document.ofMap(Map.of(Document.of(1L), Document.of("v")));
        DocumentDeserializer deserializer = new DocumentDeserializer(value);
        Map<Long, String> result = new HashMap<>();

        deserializer.readLongMap(PreludeSchemas.DOCUMENT, (k, v) -> result.put(k, v.readString(PreludeSchemas.STRING)));

        assertThat(result, equalTo(Map.of(1L, "v")));
    }
}
