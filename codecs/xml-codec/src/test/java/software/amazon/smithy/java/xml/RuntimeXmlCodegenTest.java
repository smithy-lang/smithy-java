/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import smithy.java.xml.test.model.AllListsStruct;
import smithy.java.xml.test.model.AttributeAfterElementStruct;
import smithy.java.xml.test.model.BlobStruct;
import smithy.java.xml.test.model.Color;
import smithy.java.xml.test.model.ComplexStruct;
import smithy.java.xml.test.model.FlattenedListStruct;
import smithy.java.xml.test.model.FlattenedMapStruct;
import smithy.java.xml.test.model.InnerStruct;
import smithy.java.xml.test.model.IntEnumStruct;
import smithy.java.xml.test.model.NamespacedMembersStruct;
import smithy.java.xml.test.model.NamespacedStruct;
import smithy.java.xml.test.model.NestedStruct;
import smithy.java.xml.test.model.NumericStruct;
import smithy.java.xml.test.model.PrefixedAttributeStruct;
import smithy.java.xml.test.model.Priority;
import smithy.java.xml.test.model.RecursiveStruct;
import smithy.java.xml.test.model.SimpleStruct;
import smithy.java.xml.test.model.StringStruct;
import smithy.java.xml.test.model.TimestampStruct;
import smithy.java.xml.test.model.TypedAttributeStruct;
import smithy.java.xml.test.model.UnionStruct;
import smithy.java.xml.test.model.ValueUnion;
import smithy.java.xml.test.model.XmlAttributeStruct;
import smithy.java.xml.test.model.XmlNameStruct;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenFeature;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenStats;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.MemberSubsetCodec;
import software.amazon.smithy.model.traits.XmlNamespaceTrait;

/**
 * Holds the XML backend to the interpreted serde's behavior, byte for byte wherever that is possible.
 *
 * <p>Round-trip tests cannot catch a divergence that both halves of the generated pair agree on, and
 * they cannot catch a shape the backend silently declines to lower. Every write case here therefore
 * encodes the same value twice, once through generated code and once through the dispatch serializer, and
 * compares the documents; the read cases hand both readers the same bytes and compare what comes back,
 * including when what comes back is a rejection. {@link #everyModelShapeIsGeneratedRatherThanFallingBack()}
 * and {@link #everyModelShapeIsReadByGeneratedCodeRatherThanFallingBack()} then prove that the generated
 * halves were really generated.
 */
final class RuntimeXmlCodegenTest {

    /**
     * Shapes whose two writers agree on content but not on the order of a structure's members.
     *
     * <p>The plan walks {@code schema.members()}, whose order is set by
     * {@code SchemaBuilder.sortMembers}: a required member with no default is hoisted to the front so the
     * presence tracker can use a compact bitfield. The dispatch serializer instead follows the generated
     * {@code serializeMembers}, which walks the model's declaration order. {@code ComplexStruct} declares
     * a required {@code nested} fifteenth, so the two disagree from that point on. Declaration order does
     * not survive into the runtime schema, so no backend can reproduce it.
     *
     * <p>Matching it is also not worth reaching for. What a backend would be matching is not the model's
     * declaration order but the order one particular {@code serializeMembers} happens to call in, which is
     * a property of the generated class rather than of the schema: a hand-written
     * {@code SerializableStruct} may emit its members in any order at all, so a member index that recorded
     * declaration order would look like a guarantee while quietly not applying to those. Struct member
     * order carries no meaning in the XML protocols either — the one place order does matter, attributes
     * before elements, is asserted directly by {@link #attributesArePartitionedAheadOfElements()}, and
     * list and map element order, which is significant, stays byte-identical — and the JSON and CBOR
     * backends already ship the same reorder with the same cross-reading parity tests, so this keeps one
     * story across the three backends. These shapes are held to
     * {@link #crossReadsMatchTheOriginal(String, SerializableShape)} instead, which pins every byte of
     * content without pinning a field order nothing consumes.
     */
    private static final Set<String> MEMBER_ORDER_DIFFERS = Set.of("ComplexStruct");

    private static XmlCodec generated() {
        return XmlCodec.builder().useNative(true).runtimeCodegen(true).build();
    }

    private static XmlCodec interpreted() {
        return XmlCodec.builder().useNative(true).runtimeCodegen(false).build();
    }

    /** One populated instance per shape in the test model. */
    static List<Arguments> shapes() {
        List<Arguments> shapes = new ArrayList<>();
        for (Map.Entry<String, SerializableShape> entry : allShapes().entrySet()) {
            shapes.add(Arguments.of(entry.getKey(), entry.getValue()));
        }
        return shapes;
    }

    private static Map<String, SerializableShape> allShapes() {
        Map<String, SerializableShape> shapes = new LinkedHashMap<>();
        shapes.put("SimpleStruct",
                SimpleStruct.builder()
                        .name("test")
                        .age(42)
                        .active(true)
                        .score(98.6)
                        .createdAt(Instant.parse("2025-01-15T10:30:00.123Z"))
                        .build());
        shapes.put("ComplexStruct",
                ComplexStruct.builder()
                        .id("id")
                        .count(7)
                        .enabled(true)
                        .ratio(0.5)
                        .score(1.5f)
                        .bigCount(9_000_000_000L)
                        .optionalString("optional")
                        .optionalInt(-3)
                        .createdAt(Instant.parse("2024-02-29T00:00:00Z"))
                        .payload(ByteBuffer.wrap("payload".getBytes(StandardCharsets.UTF_8)))
                        .tags(Arrays.asList("a", null, "c & <d>"))
                        .intList(List.of(1, 2, 3))
                        .metadata(orderedMap("k1", "v1", "k2", "v & 2"))
                        .intMap(Map.of("only", 5))
                        .nested(nested())
                        .optionalNested(nested())
                        .structList(List.of(nested(), nested()))
                        .structMap(Map.of("n", nested()))
                        .color(Color.GREEN)
                        .colorList(List.of(Color.RED, Color.BLUE))
                        .bigIntValue(new BigInteger("123456789012345678901234567890"))
                        .bigDecValue(new BigDecimal("1.2300"))
                        .build());
        shapes.put("NestedStruct", nested());
        shapes.put("InnerStruct", InnerStruct.builder().value("inner").numbers(List.of(-1, 0, 1)).build());
        shapes.put("NumericStruct",
                NumericStruct.builder()
                        .byteVal((byte) -128)
                        .shortVal((short) 32767)
                        .intVal(Integer.MIN_VALUE)
                        .longVal(Long.MAX_VALUE)
                        .floatVal(3.4028235E38f)
                        .doubleVal(-1.7976931348623157E308)
                        .bigIntVal(BigInteger.ZERO)
                        .bigDecVal(new BigDecimal("0.000"))
                        .build());
        shapes.put("StringStruct", StringStruct.builder().value("<>&\"'\n\ttext é 😀").build());
        shapes.put("TimestampStruct",
                TimestampStruct.builder()
                        .epochSeconds(Instant.ofEpochSecond(1_700_000_000L, 500_000_000L))
                        .dateTime(Instant.parse("1969-12-31T23:59:59.000000001Z"))
                        .httpDate(Instant.parse("2030-06-15T12:00:00Z"))
                        .build());
        shapes.put("XmlNameStruct",
                XmlNameStruct.builder().id("i").displayName("d").normalField("n").build());
        shapes.put("XmlAttributeStruct",
                XmlAttributeStruct.builder()
                        .version("1.0")
                        .identifier("a\"b<c&d")
                        .content("body")
                        .build());
        shapes.put("FlattenedListStruct",
                FlattenedListStruct.builder()
                        .items(Arrays.asList("x", null, "z"))
                        .numbers(List.of(9, 8))
                        .normalList(List.of("wrapped"))
                        .build());
        shapes.put("FlattenedMapStruct",
                FlattenedMapStruct.builder()
                        .entries(orderedMap("fk", "fv"))
                        .normalMap(orderedMap("nk", "nv"))
                        .build());
        shapes.put("NamespacedStruct", NamespacedStruct.builder().name("ns").value(1).build());
        shapes.put("RecursiveStruct",
                RecursiveStruct.builder()
                        .value("depth0")
                        .child(RecursiveStruct.builder()
                                .value("depth1")
                                .child(RecursiveStruct.builder().value("depth2").build())
                                .build())
                        .build());
        shapes.put("BlobStruct",
                BlobStruct.builder().data(ByteBuffer.wrap(new byte[] {0, 1, 2, (byte) 0xff})).build());
        shapes.put("AllListsStruct",
                AllListsStruct.builder()
                        .booleans(List.of(true, false))
                        .bytes(List.of((byte) 1, (byte) -1))
                        .shorts(List.of((short) 2, (short) -2))
                        .ints(List.of(3, -3))
                        .longs(List.of(4L, -4L))
                        .floats(List.of(1.5f, Float.NaN))
                        .doubles(List.of(2.5, Double.POSITIVE_INFINITY))
                        .bigInts(List.of(BigInteger.TEN))
                        .bigDecs(List.of(new BigDecimal("1.10")))
                        .strings(Arrays.asList("s", null))
                        .blobs(List.of(ByteBuffer.wrap("b".getBytes(StandardCharsets.UTF_8))))
                        .timestamps(List.of(Instant.EPOCH))
                        .build());
        shapes.put("UnionStruct",
                UnionStruct.builder()
                        .name("u")
                        .choice(new ValueUnion.StringValueMember("s"))
                        .choices(List.of(
                                new ValueUnion.StringValueMember("a & b"),
                                new ValueUnion.IntValueMember(-7),
                                new ValueUnion.StructValueMember(nested()),
                                new ValueUnion.ListValueMember(List.of(1, 2)),
                                new ValueUnion.MapValueMember(orderedMap("mk", "mv")),
                                new ValueUnion.RenamedValueMember("renamed")))
                        .flatChoices(List.of(new ValueUnion.IntValueMember(1)))
                        .build());
        shapes.put("IntEnumStruct",
                IntEnumStruct.builder()
                        .priority(Priority.HIGH)
                        .priorities(List.of(Priority.LOW, Priority.MEDIUM))
                        .priorityMap(Map.of("p", Priority.MEDIUM))
                        .build());
        shapes.put("TypedAttributeStruct",
                TypedAttributeStruct.builder()
                        .flag(true)
                        .boolAttr(false)
                        .intAttr(-1)
                        .longAttr(Long.MIN_VALUE)
                        .floatAttr(1.25f)
                        .doubleAttr(-0.5)
                        .timestampAttr(Instant.parse("2021-03-04T05:06:07.008Z"))
                        .colorAttr(Color.YELLOW)
                        .priorityAttr(Priority.LOW)
                        .renamedAttr("q\"<&>")
                        .body("text")
                        .build());
        shapes.put("NamespacedMembersStruct",
                NamespacedMembersStruct.builder()
                        .name("n")
                        .items(List.of("i1", "i2"))
                        .flatItems(List.of("f1"))
                        .lookup(orderedMap("lk", "lv"))
                        .flatLookup(orderedMap("fk", "fv"))
                        .build());
        shapes.put("AttributeAfterElementStruct", attributeAfterElement());
        return shapes;
    }

    private static AttributeAfterElementStruct attributeAfterElement() {
        return AttributeAfterElementStruct.builder()
                .lateAttr("a")
                .otherAttr(7)
                .body("b")
                .nested(PrefixedAttributeStruct.builder().prefixed("n").value("v").build())
                .trailing("t")
                .build();
    }

    private static NestedStruct nested() {
        return NestedStruct.builder()
                .field1("f1")
                .field2(2)
                .inner(InnerStruct.builder().value("iv").numbers(List.of(1)).build())
                .build();
    }

    private static Map<String, String> orderedMap(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shapes")
    void generatedBytesMatchInterpretedBytes(String name, SerializableShape shape) {
        assumeFalse(MEMBER_ORDER_DIFFERS.contains(name), "member order differs; see MEMBER_ORDER_DIFFERS");
        try (var generated = generated(); var interpreted = interpreted()) {
            assertThat(text(generated.serialize(shape))).isEqualTo(text(interpreted.serialize(shape)));
        }
    }

    /** The streaming entry point takes a different route into the generated codec than {@code serialize}. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("shapes")
    void generatedStreamBytesMatchInterpretedBytes(String name, SerializableShape shape) {
        assumeFalse(MEMBER_ORDER_DIFFERS.contains(name), "member order differs; see MEMBER_ORDER_DIFFERS");
        try (var generated = generated(); var interpreted = interpreted()) {
            assertThat(stream(generated, shape)).isEqualTo(stream(interpreted, shape));
        }
    }

    /**
     * Holds the read side to the interpreted deserializer's values.
     *
     * <p>The document is produced once, by the interpreted serializer, so a write-side divergence cannot
     * hide a read-side one: both readers are handed the same bytes. Comparing each result against the
     * original as well as against the other reader is what catches a mistake the two happen to share,
     * such as a wrapper that reads the wrong primitive.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("shapes")
    void generatedReadsMatchInterpretedReads(String name, SerializableShape shape) {
        byte[] document;
        try (var interpreted = interpreted()) {
            document = bytes(interpreted.serialize(shape));
        }
        try (var generated = generated(); var interpreted = interpreted()) {
            SerializableShape fromGenerated = read(generated, shape, document);
            assertThat(fromGenerated).isEqualTo(read(interpreted, shape, document));
            assertThat(fromGenerated).isEqualTo(shape);
        }
    }

    /**
     * Reads a generated document with both readers, which is what stands in for byte identity.
     *
     * <p>Together with {@link #generatedReadsMatchInterpretedReads(String, SerializableShape)} this covers
     * all three directions: interpreted bytes through the generated reader, generated bytes through the
     * interpreted reader, and generated bytes through the generated reader. A writer that dropped,
     * duplicated, misnamed or mistyped a member fails here; only the order of a structure's members is
     * left unpinned, and the interpreted reader is order-independent by construction because it matches
     * members by name. See {@link #MEMBER_ORDER_DIFFERS} for why that is the right thing to leave
     * unpinned.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("shapes")
    void crossReadsMatchTheOriginal(String name, SerializableShape shape) {
        byte[] document;
        try (var generated = generated()) {
            document = bytes(generated.serialize(shape));
        }
        try (var generated = generated(); var interpreted = interpreted()) {
            assertThat(read(interpreted, shape, document)).isEqualTo(shape);
            assertThat(read(generated, shape, document)).isEqualTo(shape);
        }
    }

    /**
     * Attributes have to be written inside the start tag, whatever order the plan lists members in.
     *
     * <p>This is the one ordering constraint XML places on a structure's members, so unlike the rest of
     * the member order it is asserted directly rather than left to a cross-read. The shape is built so the
     * plan actively works against the writer: both attribute members are declared ahead of {@code body},
     * but {@code body} is required with no default, so {@code SchemaBuilder.sortMembers} hoists it to the
     * front and the writer sees an element member before either attribute. A writer that followed the
     * plan's order would emit {@code lateAttr} after the start tag had already been closed.
     */
    @Test
    void attributesArePartitionedAheadOfElements() {
        try (var generated = generated(); var interpreted = interpreted()) {
            String expected = "<AttributeAfterElementStruct lateAttr=\"a\" renamed=\"7\">"
                    + "<body>b</body>"
                    + "<nested xmlns:xsi=\"https://example.com\" xsi:someName=\"n\"><value>v</value></nested>"
                    + "<trailing>t</trailing>"
                    + "</AttributeAfterElementStruct>";
            assertThat(text(generated.serialize(attributeAfterElement()))).isEqualTo(expected);
            assertThat(text(interpreted.serialize(attributeAfterElement()))).isEqualTo(expected);
        }
    }

    @Test
    void everyModelShapeIsGeneratedRatherThanFallingBack() {
        assumeTrue(RuntimeCodegenFeature.available(), "runtime code generation requires JDK 25");

        // A wrapper element list no other test uses, so these settings intern to an instance the
        // registry has never seen and generation is forced to run rather than answered from a cache.
        Map<String, SerializableShape> shapes = allShapes();
        RuntimeCodegenStats.reset();
        try (var codec = XmlCodec.builder()
                .wrapperElements(List.of("RuntimeXmlCodegenTestFreshIdentity"))
                .useNative(true)
                .runtimeCodegen(true)
                .build()) {
            for (SerializableShape shape : shapes.values()) {
                codec.serialize(shape);
            }
        }

        var snapshot = RuntimeCodegenStats.snapshot("xml");
        assertThat(snapshot.generated()).isEqualTo(shapes.size());
        assertThat(snapshot.unsupported()).isZero();
        assertThat(snapshot.failed()).isZero();
    }

    /**
     * The read counterpart: proves the deserializer resolved a generated codec rather than falling back.
     *
     * <p>A default namespace is the settings field to vary here. It gives an identity the registry has
     * never seen, which forces generation to run, and unlike a wrapper element list it changes nothing
     * about how a document is read, so the documents below stay ordinary.
     *
     * <p>This codec never serializes, so a positive {@code generated()} can only have come from a
     * deserializer asking for a codec. Once it has one, the only way a generated reader declines is a
     * builder of the wrong type, which a top-level read cannot produce.
     */
    @Test
    void everyModelShapeIsReadByGeneratedCodeRatherThanFallingBack() {
        assumeTrue(RuntimeCodegenFeature.available(), "runtime code generation requires JDK 25");

        Map<String, SerializableShape> shapes = allShapes();
        Map<String, byte[]> documents = new LinkedHashMap<>();
        try (var interpreted = interpreted()) {
            for (Map.Entry<String, SerializableShape> entry : shapes.entrySet()) {
                documents.put(entry.getKey(), bytes(interpreted.serialize(entry.getValue())));
            }
        }

        RuntimeCodegenStats.reset();
        try (var codec = XmlCodec.builder()
                .defaultNamespace(XmlNamespaceTrait.builder()
                        .uri("urn:runtime-xml-codegen-test:read")
                        .build())
                .useNative(true)
                .runtimeCodegen(true)
                .build()) {
            for (Map.Entry<String, SerializableShape> entry : shapes.entrySet()) {
                read(codec, entry.getValue(), documents.get(entry.getKey()));
            }
        }

        var snapshot = RuntimeCodegenStats.snapshot("xml");
        assertThat(snapshot.generated()).isEqualTo(shapes.size());
        assertThat(snapshot.unsupported()).isZero();
        assertThat(snapshot.failed()).isZero();
    }

    /**
     * Both readers read a prefixed attribute name at the root.
     *
     * <p>{@code @xmlName("xsi:someName")} is written verbatim, but the parser records every attribute under
     * its local name, dropping everything before the first colon. The dispatch reader used to compensate
     * for that only when it had no {@code StructExtension} to consult — the case for a nested structure but
     * not for a root one, where it looked the attribute up through the extension's name table, which holds
     * the full name, and found nothing. So the same structure read at the root lost an attribute it kept
     * when nested, and the generated reader, which is one class for both positions and looks attributes up
     * by local name, read it in both. That was the only document the two paths read differently.
     *
     * <p>{@code getAttributeValueByLocalName} closes it on the dispatch side, which is the side that was
     * wrong. This asserts the agreement rather than the old split.
     */
    @Test
    void prefixedRootAttributeIsReadByBothPaths() {
        assumeTrue(RuntimeCodegenFeature.available(), "runtime code generation requires JDK 25");

        byte[] document = ("<PrefixedAttributeStruct xmlns:xsi=\"https://example.com\" xsi:someName=\"n\">"
                + "<value>v</value></PrefixedAttributeStruct>").getBytes(StandardCharsets.UTF_8);
        var expected = PrefixedAttributeStruct.builder().prefixed("n").value("v").build();
        try (var generated = generated(); var interpreted = interpreted()) {
            assertThat(readPrefixed(generated, document)).isEqualTo(expected);
            assertThat(readPrefixed(interpreted, document)).isEqualTo(expected);
        }
    }

    private static PrefixedAttributeStruct readPrefixed(XmlCodec codec, byte[] document) {
        return codec.deserializeShape(ByteBuffer.wrap(document), PrefixedAttributeStruct.builder());
    }

    /** Members the subset leaves out, held in an enum because the registry keys codecs on its identity. */
    private enum WithoutActiveOrScore implements MemberSubsetCodec.MemberSubset {
        INSTANCE;

        @Override
        public boolean includes(Schema struct, Schema member) {
            return !member.memberName().equals("active") && !member.memberName().equals("score");
        }
    }

    /** A distinct selector identity so this test proves that a narrowed reader was generated. */
    private enum ReadWithoutActiveOrScore implements MemberSubsetCodec.MemberSubset {
        INSTANCE;

        @Override
        public boolean includes(Schema struct, Schema member) {
            return !member.memberName().equals("active") && !member.memberName().equals("score");
        }
    }

    /**
     * The {@link MemberSubsetCodec} path generates a codec for the narrowed shape rather than falling back.
     *
     * <p>Falling back here would be silent and would still produce the right bytes, because the caller
     * then hides the excluded members behind a proxy, so the interesting assertion is the stats one. The
     * subset is a third axis of the codec cache alongside the shape and the settings, so a subset instance
     * no other test uses forces generation to run even under the default settings every other test shares.
     *
     * <p>The bytes are compared against the dispatch serializer given the same shape with the excluded
     * members left unset, which is the definition being claimed: excluding a member has to look exactly
     * like the member not being there.
     */
    @Test
    void memberSubsetIsWrittenByGeneratedCode() {
        assumeTrue(RuntimeCodegenFeature.available(), "runtime code generation requires JDK 25");

        Instant createdAt = Instant.parse("2025-01-15T10:30:00.123Z");
        SimpleStruct full = SimpleStruct.builder()
                .name("test")
                .age(42)
                .active(true)
                .score(98.6)
                .createdAt(createdAt)
                .build();
        SimpleStruct narrowed = SimpleStruct.builder().name("test").age(42).createdAt(createdAt).build();

        ByteBuffer subsetBytes;
        RuntimeCodegenStats.reset();
        try (var codec = generated()) {
            subsetBytes = codec.serialize(full, WithoutActiveOrScore.INSTANCE);
        }
        assertThat(subsetBytes).isNotNull();

        var snapshot = RuntimeCodegenStats.snapshot("xml");
        assertThat(snapshot.generated()).isEqualTo(1);
        assertThat(snapshot.unsupported()).isZero();
        assertThat(snapshot.failed()).isZero();

        try (var interpreted = interpreted()) {
            assertThat(text(subsetBytes)).isEqualTo(text(interpreted.serialize(narrowed)));
        }
    }

    @Test
    void memberSubsetIsReadByGeneratedCode() {
        assumeTrue(RuntimeCodegenFeature.available(), "runtime code generation requires JDK 25");

        Instant createdAt = Instant.parse("2025-01-15T10:30:00.123Z");
        SimpleStruct full = SimpleStruct.builder()
                .name("test")
                .age(42)
                .active(true)
                .score(98.6)
                .createdAt(createdAt)
                .build();
        SimpleStruct expected = SimpleStruct.builder().name("test").age(42).createdAt(createdAt).build();

        byte[] document;
        try (var interpreted = interpreted()) {
            document = bytes(interpreted.serialize(full));
        }

        RuntimeCodegenStats.reset();
        var builder = SimpleStruct.builder();
        try (var codec = generated()) {
            assertThat(codec.deserialize(
                    SimpleStruct.$SCHEMA,
                    builder,
                    ByteBuffer.wrap(document),
                    ReadWithoutActiveOrScore.INSTANCE)).isTrue();
        }
        assertThat(builder.build()).isEqualTo(expected);

        var snapshot = RuntimeCodegenStats.snapshot("xml");
        assertThat(snapshot.generated()).isEqualTo(1);
        assertThat(snapshot.unsupported()).isZero();
        assertThat(snapshot.failed()).isZero();
    }

    @Test
    void genericStructReaderStillInvokesItsConsumerForBuilderState() {
        assumeTrue(RuntimeCodegenFeature.available(), "runtime code generation requires JDK 25");

        byte[] document = ("<SimpleStruct><name>n</name><age>1</age>"
                + "<active>true</active></SimpleStruct>").getBytes(StandardCharsets.UTF_8);
        int[] calls = {0};
        try (var codec = generated()) {
            codec.createDeserializer(ByteBuffer.wrap(document))
                    .readStruct(
                            SimpleStruct.$SCHEMA,
                            SimpleStruct.builder(),
                            (builder, member, value) -> calls[0]++);
        }
        assertThat(calls[0]).isEqualTo(3);
    }

    /**
     * Documents no schema-driven writer would produce, which the reader still has to survive.
     *
     * <p>A round trip only ever hands the reader documents the writer just produced, so nothing above
     * covers a service that sends a member this client does not model, or a union that arrives empty.
     *
     * <p>The {@code readable} flag records whether the dispatch reader accepts the document at all. It is
     * the one thing here not taken from the dispatch reader on the spot, and it is written down so the
     * comparison cannot quietly go vacuous: without it, a change that made the generated reader reject
     * everything would still pass as long as the dispatch reader rejected the same documents.
     */
    static List<Arguments> unusualDocuments() {
        Supplier<ShapeBuilder<?>> simple = SimpleStruct::builder;
        Supplier<ShapeBuilder<?>> union = UnionStruct::builder;
        return List.of(
                Arguments.of("unknownElement",
                        simple,
                        "<SimpleStruct><name>n</name><mystery>x</mystery><age>1</age></SimpleStruct>",
                        true),
                Arguments.of("unknownElementWithChildren",
                        simple,
                        "<SimpleStruct><mystery><deep>x</deep></mystery><name>n</name></SimpleStruct>",
                        true),
                Arguments.of("unknownSelfClosingElement",
                        simple,
                        "<SimpleStruct><name>n</name><mystery/></SimpleStruct>",
                        true),
                Arguments.of("unknownAttribute",
                        simple,
                        "<SimpleStruct mystery=\"x\"><name>n</name></SimpleStruct>",
                        true),
                Arguments.of("emptyStringElement",
                        simple,
                        "<SimpleStruct><name></name><age>1</age></SimpleStruct>",
                        true),
                Arguments.of("selfClosingStringElement",
                        simple,
                        "<SimpleStruct><name/><age>1</age></SimpleStruct>",
                        true),
                Arguments.of("noMembersAtAll", simple, "<SimpleStruct/>", true),
                Arguments.of("unionWithUnknownMember",
                        union,
                        "<UnionStruct><name>u</name><choice><mystery>a</mystery></choice></UnionStruct>",
                        true),
                // An empty element for a primitive-typed member reaches the value parsers with nothing to
                // parse, so each type rejects it in its own words. Agreeing on the wording is the point:
                // it is how the generated reader is shown to be running the same parsers.
                Arguments.of("emptyIntegerElement",
                        simple,
                        "<SimpleStruct><name>n</name><age></age></SimpleStruct>",
                        false),
                Arguments.of("selfClosingIntegerElement",
                        simple,
                        "<SimpleStruct><name>n</name><age/></SimpleStruct>",
                        false),
                Arguments.of("emptyBooleanElement",
                        simple,
                        "<SimpleStruct><name>n</name><active/></SimpleStruct>",
                        false),
                Arguments.of("emptyDoubleElement",
                        simple,
                        "<SimpleStruct><name>n</name><score/></SimpleStruct>",
                        false),
                Arguments.of("emptyTimestampElement",
                        simple,
                        "<SimpleStruct><name>n</name><createdAt/></SimpleStruct>",
                        false),
                Arguments.of("unionWithNoChild",
                        union,
                        "<UnionStruct><name>u</name><choice></choice></UnionStruct>",
                        false),
                Arguments.of("selfClosingUnion",
                        union,
                        "<UnionStruct><name>u</name><choice/></UnionStruct>",
                        false),
                Arguments.of("unionWithTwoChildren",
                        union,
                        "<UnionStruct><name>u</name><choice><stringValue>a</stringValue>"
                                + "<intValue>2</intValue></choice></UnionStruct>",
                        false),
                Arguments.of("unknownElementInNestedStruct",
                        union,
                        "<UnionStruct><name>u</name><choice><structValue><mystery>x</mystery>"
                                + "<field1>f</field1></structValue></choice></UnionStruct>",
                        false),
                Arguments.of("unknownElementInWrappedList",
                        union,
                        "<UnionStruct><name>u</name><choices><mystery>x</mystery>"
                                + "<member><intValue>1</intValue></member></choices></UnionStruct>",
                        false));
    }

    /**
     * Pins the generated reader to the dispatch reader, including when the dispatch reader throws.
     *
     * <p>The outcome is compared rather than the value, so "both readers reject this" counts as agreement
     * and a divergence in which one rejects does not. There is nothing to compare against the original
     * shape here: these documents have no original.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("unusualDocuments")
    void unusualDocumentsReadLikeTheInterpretedPath(
            String name,
            Supplier<ShapeBuilder<?>> builder,
            String document,
            boolean readable
    ) {
        byte[] bytes = document.getBytes(StandardCharsets.UTF_8);
        try (var generated = generated(); var interpreted = interpreted()) {
            Object outcome = readOutcome(generated, builder.get(), bytes);
            assertThat(outcome).isEqualTo(readOutcome(interpreted, builder.get(), bytes));
            assertThat(outcome instanceof SerializableShape).isEqualTo(readable);
        }
    }

    /**
     * Every prefix of every document has to be rejected rather than read past the end of the buffer.
     *
     * <p>A truncated response is the one malformed document a client is guaranteed to meet, because a
     * connection can drop anywhere. Rejecting it is the reader's job; throwing
     * {@code ArrayIndexOutOfBoundsException} is not a rejection, since a caller catching the codec's own
     * exception type does not catch it and cannot tell a short read from a bug.
     *
     * <p>Both readers are checked, because they share the primitives that do the bounds arithmetic. This
     * found a real one: {@code readTextContent} probed for a CDATA section with {@code buf[pos + 1]} after
     * checking only {@code pos < limit}, so a document whose last byte was {@code <} read one byte past the
     * end. The generated reader met it through {@code generatedReadString} and the dispatch reader through
     * {@code MemberDeserializer.readString}.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("shapes")
    void everyTruncationIsRejectedCleanly(String name, SerializableShape shape) {
        byte[] document;
        try (var interpreted = interpreted()) {
            document = bytes(interpreted.serialize(shape));
        }
        try (var generated = generated(); var interpreted = interpreted()) {
            for (int length = 0; length < document.length; length++) {
                byte[] prefix = Arrays.copyOf(document, length);
                assertRejectedCleanly(generated, shape, prefix);
                assertRejectedCleanly(interpreted, shape, prefix);
            }
        }
    }

    private static void assertRejectedCleanly(XmlCodec codec, SerializableShape shape, byte[] document) {
        ShapeBuilder<?> builder = ((SerializableStruct) shape).schema().shapeBuilder();
        try {
            codec.deserializeShape(ByteBuffer.wrap(document), builder);
        } catch (IndexOutOfBoundsException | NegativeArraySizeException | StackOverflowError e) {
            throw new AssertionError(
                    "read past the end of " + new String(document, StandardCharsets.UTF_8),
                    e);
        } catch (RuntimeException e) {
            // A rejection, whatever it is called. Which exception names a truncation is compared for the
            // documents in unusualDocuments; here the only question is whether the reader stayed in bounds.
        }
    }

    /** The shape read, or a description of the failure, so a rejection can be compared like a value. */
    private static Object readOutcome(XmlCodec codec, ShapeBuilder<?> builder, byte[] document) {
        try {
            return codec.deserializeShape(ByteBuffer.wrap(document), builder);
        } catch (RuntimeException e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }

    private static SerializableShape read(XmlCodec codec, SerializableShape shape, byte[] document) {
        ShapeBuilder<?> builder = ((SerializableStruct) shape).schema().shapeBuilder();
        return codec.deserializeShape(ByteBuffer.wrap(document), builder);
    }

    private static String stream(XmlCodec codec, SerializableShape shape) {
        var out = new ByteArrayOutputStream();
        try (var serializer = codec.createSerializer(out)) {
            shape.serialize(serializer);
            serializer.flush();
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static String text(ByteBuffer buffer) {
        return StandardCharsets.UTF_8.decode(buffer).toString();
    }

    /** Copies the document out, because deserializing consumes the buffer and both readers need it. */
    private static byte[] bytes(ByteBuffer buffer) {
        byte[] copy = new byte[buffer.remaining()];
        buffer.duplicate().get(copy);
        return copy;
    }
}
