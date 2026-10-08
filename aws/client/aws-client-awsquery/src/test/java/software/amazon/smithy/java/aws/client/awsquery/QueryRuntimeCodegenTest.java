/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static software.amazon.smithy.java.aws.client.awsquery.QueryFormSerializer.QueryVariant.AWS_QUERY;
import static software.amazon.smithy.java.aws.client.awsquery.QueryFormSerializer.QueryVariant.EC2_QUERY;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.amazon.smithy.java.aws.client.awsquery.QueryFormSerializer.QueryVariant;
import software.amazon.smithy.java.aws.client.awsquery.bench.model.FaceCard;
import software.amazon.smithy.java.aws.client.awsquery.bench.model.LeafStruct;
import software.amazon.smithy.java.aws.client.awsquery.bench.model.ListStruct;
import software.amazon.smithy.java.aws.client.awsquery.bench.model.MapStruct;
import software.amazon.smithy.java.aws.client.awsquery.bench.model.MapUnderListStruct;
import software.amazon.smithy.java.aws.client.awsquery.bench.model.MiddleStruct;
import software.amazon.smithy.java.aws.client.awsquery.bench.model.NestedCollectionStruct;
import software.amazon.smithy.java.aws.client.awsquery.bench.model.NestedStruct;
import software.amazon.smithy.java.aws.client.awsquery.bench.model.PrimitiveStruct;
import software.amazon.smithy.java.aws.client.awsquery.bench.model.QueryUnion;
import software.amazon.smithy.java.aws.client.awsquery.bench.model.RecursiveStruct;
import software.amazon.smithy.java.aws.client.awsquery.bench.model.RenameStruct;
import software.amazon.smithy.java.aws.client.awsquery.bench.model.ScalarStruct;
import software.amazon.smithy.java.aws.client.awsquery.bench.model.Suit;
import software.amazon.smithy.java.aws.client.awsquery.bench.model.UnionStruct;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecRegistry;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenFeature;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenStats;
import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * Differential tests for the generated Query serializer.
 *
 * <p>Every test here answers the same question: does the generated codec emit exactly the bytes
 * {@link QueryFormSerializer} emits? A generated writer folds static path segments into constants and
 * drops the runtime schema walk, so nothing but a byte comparison against the interpreted path proves
 * the fold was right. The protocol compliance suites cannot stand in for this: they parse both sides
 * into a map and URL-decode it, so they see neither parameter order nor percent-encoding.
 *
 * <p>These run only where generation is possible. The default {@code test} task is on JDK 21 and
 * skips the class wholesale; {@code jdk25CodegenTest} is where it does its work.
 */
class QueryRuntimeCodegenTest {
    private static final String ACTION = "TestAction";
    private static final String VERSION = "2020-01-01";
    private static final String HEADER = "Action=TestAction&Version=2020-01-01";

    /** Three UTF-8 bytes each, so a value of these overruns any bound derived from char count. */
    private static final String CJK = "日本語";
    private static final String CJK_ENCODED = "%E6%97%A5%E6%9C%AC%E8%AA%9E";

    private static final Instant WHEN = Instant.parse("2015-01-25T08:00:00Z");

    @BeforeEach
    void requireCodegen() {
        assumeTrue(RuntimeCodegenFeature.available(), "runtime codegen needs JDK 25");
    }

    // -- scalars --------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void everyScalarMatches(QueryVariant variant) {
        assertIdentical(variant, fullScalars());
    }

    @Test
    void scalarNamesAndValuesArePinned() {
        String actual = assertIdentical(AWS_QUERY, fullScalars());
        assertEquals(
                HEADER
                        + "&stringValue=hello"
                        + "&booleanValue=true"
                        + "&byteValue=7"
                        + "&shortValue=-9"
                        + "&integerValue=2147483647"
                        + "&longValue=-9223372036854775808"
                        + "&floatValue=1.5"
                        + "&doubleValue=-2.25"
                        + "&bigIntegerValue=123456789012345678901234567890"
                        + "&bigDecimalValue=1.2345"
                        + "&blobValue=aGk%3D"
                        + "&timestampValue=2015-01-25T08:00:00Z"
                        + "&dateTimeValue=2015-01-25T08:00:00Z"
                        + "&epochValue=1422172800"
                        + "&httpDateValue=Sun%2C%2025%20Jan%202015%2008%3A00%3A00%20GMT"
                        + "&enumValue=HEART"
                        + "&intEnumValue=2",
                actual);
    }

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void emptyStructMatches(QueryVariant variant) {
        assertEquals(HEADER, assertIdentical(variant, ScalarStruct.builder().build()));
    }

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void emptyAndBlankScalarsMatch(QueryVariant variant) {
        assertIdentical(variant,
                ScalarStruct.builder()
                        .stringValue("")
                        .blobValue(ByteBuffer.allocate(0))
                        .bigIntegerValue(BigInteger.ZERO)
                        .bigDecimalValue(new BigDecimal("0.000"))
                        .build());
    }

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void nonFiniteNumbersMatch(QueryVariant variant) {
        assertIdentical(variant,
                ScalarStruct.builder()
                        .floatValue(Float.NaN)
                        .doubleValue(Double.POSITIVE_INFINITY)
                        .build());
        assertIdentical(variant,
                ScalarStruct.builder()
                        .floatValue(Float.NEGATIVE_INFINITY)
                        .doubleValue(Double.NaN)
                        .build());
    }

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void extremeNumbersMatch(QueryVariant variant) {
        assertIdentical(variant,
                ScalarStruct.builder()
                        .byteValue(Byte.MIN_VALUE)
                        .shortValue(Short.MIN_VALUE)
                        .integerValue(Integer.MIN_VALUE)
                        .longValue(Long.MIN_VALUE)
                        .floatValue(Float.MIN_VALUE)
                        .doubleValue(Double.MAX_VALUE)
                        .bigIntegerValue(BigInteger.TEN.pow(400).negate())
                        .bigDecimalValue(new BigDecimal("-1E-40"))
                        .build());
    }

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void timestampsBeforeEpochMatch(QueryVariant variant) {
        Instant old = Instant.parse("1969-07-20T20:17:40.123456789Z");
        assertIdentical(variant,
                ScalarStruct.builder()
                        .timestampValue(old)
                        .dateTimeValue(old)
                        .epochValue(old)
                        .httpDateValue(old)
                        .build());
    }

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void primitivesWithDefaultsMatch(QueryVariant variant) {
        // Required primitives are written unconditionally, defaults included, so the zero case is the
        // one that catches a generated writer that guarded them like boxed members.
        assertIdentical(variant, PrimitiveStruct.builder().build());
        assertIdentical(variant,
                PrimitiveStruct.builder()
                        .primBoolean(true)
                        .primByte((byte) -1)
                        .primShort((short) 300)
                        .primInteger(-42)
                        .primLong(Long.MAX_VALUE)
                        .primFloat(0.5f)
                        .primDouble(1e-9)
                        .boxedInteger(0)
                        .build());
    }

    // -- names ----------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void renamedMembersMatch(QueryVariant variant) {
        assertIdentical(variant, allRenames());
    }

    @Test
    void awsQueryUsesXmlNameAndPercentEncodesIt() {
        assertEquals(
                HEADER
                        + "&plain=1"
                        + "&Renamed=2"
                        + "&ec2Renamed=3"
                        + "&BothRenamed=4"
                        + "&ns%3AOddName=5",
                assertIdentical(AWS_QUERY, allRenames()));
    }

    @Test
    void ec2QueryCapitalizesAndPrefersEc2QueryName() {
        assertEquals(
                HEADER
                        + "&Plain=1"
                        + "&Renamed=2"
                        + "&Ec2Renamed=3"
                        + "&Ec2BothRenamed=4"
                        + "&Ns%3AOddName=5",
                assertIdentical(EC2_QUERY, allRenames()));
    }

    // -- nesting and recursion ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void nestedStructuresMatch(QueryVariant variant) {
        assertIdentical(variant, nested());
    }

    @Test
    void nestedPathIsFoldedIntoOneParameterName() {
        assertEquals(
                HEADER
                        + "&name=outer"
                        + "&inner.label=middle"
                        + "&inner.inner.value=leaf"
                        + "&inner.inner.numbers.member.1=1"
                        + "&inner.inner.numbers.member.2=2",
                assertIdentical(AWS_QUERY, nested()));
    }

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void partiallyPopulatedNestingMatches(QueryVariant variant) {
        assertIdentical(variant,
                NestedStruct.builder()
                        .inner(MiddleStruct.builder().inner(LeafStruct.builder().build()).build())
                        .build());
    }

    /**
     * A self-recursive shape has no finite set of static paths, so past a depth bound the generated
     * codec pushes the path it has accumulated and calls the unfolded method. This has to reach that
     * bound, or the fallback is never executed.
     */
    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void deepRecursionMatches(QueryVariant variant) {
        RecursiveStruct current = RecursiveStruct.builder().name("depth-20").build();
        for (int i = 19; i >= 0; i--) {
            current = RecursiveStruct.builder().name("depth-" + i).next(current).build();
        }
        assertIdentical(variant, current);
    }

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void recursionThroughListsMatches(QueryVariant variant) {
        RecursiveStruct leaf = RecursiveStruct.builder().name("leaf").build();
        RecursiveStruct current = leaf;
        for (int i = 0; i < 12; i++) {
            current = RecursiveStruct.builder()
                    .name("level-" + i)
                    .children(List.of(current, leaf))
                    .build();
        }
        assertIdentical(variant, current);
    }

    // -- lists ----------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void everyListKindMatches(QueryVariant variant) {
        assertIdentical(variant, fullLists());
    }

    @Test
    void awsQueryListNamesArePinned() {
        String actual = assertIdentical(AWS_QUERY,
                ListStruct.builder()
                        .strings(List.of("a", "b"))
                        .flatStrings(List.of("a", "b"))
                        .namedMembers(List.of("a"))
                        .flatNamedMembers(List.of("a"))
                        .structs(List.of(LeafStruct.builder().value("s").build()))
                        .nested(List.of(List.of("x", "y"), List.of("z")))
                        .build());
        assertEquals(
                HEADER
                        + "&strings.member.1=a"
                        + "&strings.member.2=b"
                        + "&flatStrings.1=a"
                        + "&flatStrings.2=b"
                        + "&namedMembers.Item.1=a"
                        + "&flatNamedMembers.1=a"
                        + "&structs.member.1.value=s"
                        // Two ".member" segments: the outer list's, then the inner list's own, which
                        // the interpreted path pushes when a list element is itself a list.
                        + "&nested.member.1.member.member.1=x"
                        + "&nested.member.1.member.member.2=y"
                        + "&nested.member.2.member.member.1=z",
                actual);
    }

    @Test
    void ec2QueryFlattensEveryList() {
        String actual = assertIdentical(EC2_QUERY,
                ListStruct.builder()
                        .strings(List.of("a", "b"))
                        .flatStrings(List.of("a"))
                        .namedMembers(List.of("a"))
                        .structs(List.of(LeafStruct.builder().value("s").build()))
                        .build());
        assertEquals(
                HEADER
                        + "&Strings.1=a"
                        + "&Strings.2=b"
                        + "&FlatStrings.1=a"
                        + "&NamedMembers.1=a"
                        + "&Structs.1.Value=s",
                actual);
    }

    /**
     * An empty AWS Query list emits its own name with no value; an empty EC2 Query list emits nothing.
     * A generated writer that treated an empty collection like an absent member would silently drop a
     * parameter here.
     */
    @Test
    void awsQueryEmptyListEmitsBareName() {
        assertEquals(
                HEADER + "&strings=&flatStrings=&structs=&nested=",
                assertIdentical(AWS_QUERY, emptyLists()));
    }

    @Test
    void ec2QueryEmptyListEmitsNothing() {
        assertEquals(HEADER, assertIdentical(EC2_QUERY, emptyLists()));
    }

    @Test
    void awsQueryEmptyNestedListEmitsBareName() {
        assertEquals(
                HEADER + "&nested.member.1.member=&nested.member.2.member.member.1=x",
                assertIdentical(AWS_QUERY,
                        ListStruct.builder()
                                .nested(List.of(List.of(), List.of("x")))
                                .build()));
    }

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void sparseListWithNullsMatches(QueryVariant variant) {
        // A null element contributes no parameter but still consumes its index.
        assertIdentical(variant,
                ListStruct.builder()
                        .sparse(Arrays.asList("a", null, "b", null))
                        .build());
    }

    @Test
    void awsQuerySparseListSkipsIndexForNull() {
        assertEquals(
                HEADER + "&sparse.member.1=a&sparse.member.3=b",
                assertIdentical(AWS_QUERY,
                        ListStruct.builder()
                                .sparse(Arrays.asList("a", null, "b"))
                                .build()));
    }

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void listOfUnionsMatches(QueryVariant variant) {
        assertIdentical(variant,
                ListStruct.builder()
                        .unions(List.of(
                                new QueryUnion.StringValueMember("s"),
                                new QueryUnion.IntegerValueMember(3),
                                new QueryUnion.StructValueMember(LeafStruct.builder().value("v").build()),
                                new QueryUnion.ListValueMember(List.of("l1", "l2")),
                                new QueryUnion.EnumValueMember(Suit.CLUB)))
                        .build());
    }

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void nonRandomAccessListMatches(QueryVariant variant) {
        // A LinkedList is not RandomAccess, so the interpreted path iterates rather than indexes; the
        // generated writer indexes either way and must still agree.
        assertIdentical(variant,
                ListStruct.builder()
                        .strings(new LinkedList<>(List.of("a", "b", "c")))
                        .build());
    }

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void longListMatchesAcrossIndexWidths(QueryVariant variant) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            values.add("v" + i);
        }
        assertIdentical(variant, ListStruct.builder().strings(values).build());
    }

    // -- maps -----------------------------------------------------------------------------------

    @Test
    void everyMapKindMatches() {
        assertIdentical(AWS_QUERY, fullMaps());
    }

    @Test
    void awsQueryMapNamesArePinned() {
        String actual = assertIdentical(AWS_QUERY,
                MapStruct.builder()
                        .strings(ordered("k1", "v1", "k2", "v2"))
                        .flatStrings(ordered("k", "v"))
                        .named(ordered("k", "v"))
                        .flatNamed(ordered("k", "v"))
                        .lists(Map.of("k", List.of("a", "b")))
                        .build());
        assertEquals(
                HEADER
                        + "&strings.entry.1.key=k1"
                        + "&strings.entry.1.value=v1"
                        + "&strings.entry.2.key=k2"
                        + "&strings.entry.2.value=v2"
                        + "&flatStrings.1.key=k"
                        + "&flatStrings.1.value=v"
                        + "&named.entry.1.EntryKey=k"
                        + "&named.entry.1.EntryValue=v"
                        + "&flatNamed.1.EntryKey=k"
                        + "&flatNamed.1.EntryValue=v"
                        + "&lists.entry.1.key=k"
                        + "&lists.entry.1.value.member.1=a"
                        + "&lists.entry.1.value.member.2=b",
                actual);
    }

    @Test
    void sparseMapWithNullValuesMatches() {
        // A null value still emits its entry's key, and still consumes the entry index.
        Map<String, String> values = new LinkedHashMap<>();
        values.put("a", "1");
        values.put("b", null);
        values.put("c", "3");
        assertEquals(
                HEADER
                        + "&sparse.entry.1.key=a"
                        + "&sparse.entry.1.value=1"
                        + "&sparse.entry.2.key=b"
                        + "&sparse.entry.3.key=c"
                        + "&sparse.entry.3.value=3",
                assertIdentical(AWS_QUERY, MapStruct.builder().sparse(values).build()));
    }

    @Test
    void emptyMapMatches() {
        assertEquals(
                HEADER,
                assertIdentical(AWS_QUERY,
                        MapStruct.builder()
                                .strings(Map.of())
                                .lists(Map.of())
                                .build()));
    }

    @Test
    void enumKeyedMapMatches() {
        assertIdentical(AWS_QUERY,
                MapStruct.builder()
                        .enumKeyed(Map.of(Suit.DIAMOND, "d"))
                        .build());
    }

    @Test
    void mapUnderListMatches() {
        assertIdentical(AWS_QUERY,
                MapUnderListStruct.builder()
                        .maps(List.of(ordered("k", "v"), ordered("k2", "v2")))
                        .build());
    }

    @Test
    void threeDeepCollectionNestingMatches() {
        Map<String, List<String>> first = new LinkedHashMap<>();
        first.put("a", List.of("1", "2"));
        Map<String, List<String>> second = new LinkedHashMap<>();
        second.put("b", List.of("3"));
        assertEquals(
                HEADER
                        + "&listOfMapOfList.member.1.member.entry.1.key=a"
                        + "&listOfMapOfList.member.1.member.entry.1.value.member.1=1"
                        + "&listOfMapOfList.member.1.member.entry.1.value.member.2=2"
                        + "&listOfMapOfList.member.2.member.entry.1.key=b"
                        + "&listOfMapOfList.member.2.member.entry.1.value.member.1=3",
                assertIdentical(AWS_QUERY,
                        NestedCollectionStruct.builder()
                                .listOfMapOfList(List.of(first, second))
                                .build()));
    }

    @Test
    void mapOfMapsMatches() {
        Map<String, Map<String, String>> outer = new LinkedHashMap<>();
        outer.put("a", ordered("x", "1"));
        outer.put("b", ordered("y", "2"));
        assertEquals(
                HEADER
                        + "&mapOfMaps.entry.1.key=a"
                        + "&mapOfMaps.entry.1.value.entry.1.key=x"
                        + "&mapOfMaps.entry.1.value.entry.1.value=1"
                        + "&mapOfMaps.entry.2.key=b"
                        + "&mapOfMaps.entry.2.value.entry.1.key=y"
                        + "&mapOfMaps.entry.2.value.entry.1.value=2",
                assertIdentical(AWS_QUERY, MapStruct.builder().mapOfMaps(outer).build()));
    }

    /**
     * EC2 Query has no representation for a map, so the backend must decline the whole shape and leave
     * it to the interpreted path -- which throws, but that is the existing behaviour, not this
     * backend's to change.
     */
    @Test
    void ec2QueryRefusesMaps() {
        assertNull(SmithyGeneratedQuerySerde.serializeGenerated(
                EC2_QUERY,
                MapStruct.builder().strings(ordered("k", "v")).build(),
                ACTION,
                VERSION));
    }

    @Test
    void ec2QueryRefusesMapsNestedUnderLists() {
        assertNull(SmithyGeneratedQuerySerde.serializeGenerated(
                EC2_QUERY,
                MapUnderListStruct.builder().maps(List.of(ordered("k", "v"))).build(),
                ACTION,
                VERSION));
    }

    // -- unions ---------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void everyUnionVariantMatches(QueryVariant variant) {
        for (QueryUnion choice : List.of(
                new QueryUnion.StringValueMember("s"),
                new QueryUnion.IntegerValueMember(-7),
                new QueryUnion.StructValueMember(LeafStruct.builder().value("v").numbers(List.of(1)).build()),
                new QueryUnion.ListValueMember(List.of("a", "b")),
                new QueryUnion.EnumValueMember(Suit.HEART))) {
            assertIdentical(variant, UnionStruct.builder().choice(choice).build());
        }
    }

    @Test
    void unionMemberNameIsFoldedIntoThePath() {
        assertEquals(
                HEADER + "&choice.structValue.value=v",
                assertIdentical(AWS_QUERY,
                        UnionStruct.builder()
                                .choice(new QueryUnion.StructValueMember(LeafStruct.builder().value("v").build()))
                                .build()));
    }

    // -- buffer bounds --------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void multiByteStringValuesMatch(QueryVariant variant) {
        assertIdentical(variant, ScalarStruct.builder().stringValue(CJK.repeat(400)).build());
    }

    @Test
    void multiByteStringValueIsFullyEncoded() {
        assertEquals(
                HEADER + "&stringValue=" + CJK_ENCODED,
                assertIdentical(AWS_QUERY, ScalarStruct.builder().stringValue(CJK).build()));
    }

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void multiByteListElementsMatch(QueryVariant variant) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            values.add(CJK.repeat(20));
        }
        assertIdentical(variant, ListStruct.builder().strings(values).build());
    }

    @Test
    void multiByteMapKeysAndValuesMatch() {
        assertIdentical(AWS_QUERY,
                MapStruct.builder()
                        .strings(ordered(CJK.repeat(100), CJK.repeat(100)))
                        .build());
    }

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void largeBlobMatches(QueryVariant variant) {
        byte[] data = new byte[4096];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        assertIdentical(variant, ScalarStruct.builder().blobValue(ByteBuffer.wrap(data)).build());
    }

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void bufferGrowthMatches(QueryVariant variant) {
        // Past the pool's cacheable size, so the growth path and the discard-on-release path both run.
        List<String> values = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            values.add("value-" + i);
        }
        String output = assertIdentical(variant, ListStruct.builder().strings(values).build());
        assertTrue(output.length() > 4096, "expected the buffer to have grown, got " + output.length());
    }

    // -- pooling --------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(QueryVariant.class)
    void pooledWriterIsResetBetweenUses(QueryVariant variant) {
        List<String> large = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            large.add("padding-" + i);
        }
        assertIdentical(variant, ListStruct.builder().strings(large).build());
        // A writer that kept any state -- buffer position or prefix stack -- would show it here.
        String name = variant == EC2_QUERY ? "StringValue" : "stringValue";
        assertEquals(
                HEADER + "&" + name + "=after",
                assertIdentical(variant, ScalarStruct.builder().stringValue("after").build()));
    }

    @Test
    void detachedBufferDoesNotAliasThePooledWriter() {
        ByteBuffer first = SmithyGeneratedQuerySerde.serializeGenerated(
                AWS_QUERY,
                ScalarStruct.builder().stringValue("first").build(),
                ACTION,
                VERSION);
        assertNotNull(first);
        byte[] before = bytes(first);

        // Same thread, so the pool hands back the same instance and overwrites its buffer.
        for (int i = 0; i < 4; i++) {
            SmithyGeneratedQuerySerde.serializeGenerated(
                    AWS_QUERY,
                    ScalarStruct.builder().stringValue("overwrite-" + i).build(),
                    ACTION,
                    VERSION);
        }

        assertArrayEquals(before, bytes(first), "detach() handed out a view of the pooled buffer");
        assertEquals(HEADER + "&stringValue=first", latin1(first));
    }

    // -- generation actually happened -----------------------------------------------------------

    /**
     * Everything above passes if the backend declines every shape, because the helper would only ever
     * compare the interpreted path with itself. This asserts a class really was emitted.
     */
    @Test
    void generationSucceedsForEverySupportedRoot() {
        RuntimeCodegenStats.Snapshot before = RuntimeCodegenStats.snapshot("awsquery");

        // A fresh registry and a fresh settings identity, so nothing is served from the shared cache
        // and the delta is exactly what this test generated.
        RuntimeCodecRegistry<GeneratedQueryCodec> registry =
                new RuntimeCodecRegistry<>(new QueryRuntimeCodegenBackend(AWS_QUERY));
        Object settings = new Object();
        List<SerializableStruct> roots = List.of(
                fullScalars(),
                PrimitiveStruct.builder().build(),
                allRenames(),
                nested(),
                RecursiveStruct.builder().name("r").build(),
                fullLists(),
                fullMaps(),
                MapUnderListStruct.builder().build(),
                NestedCollectionStruct.builder().build(),
                UnionStruct.builder().build());

        for (SerializableStruct root : roots) {
            assertNotNull(
                    registry.get(root.schema(), settings),
                    () -> "no codec generated for " + root.schema().id());
        }

        RuntimeCodegenStats.Snapshot after = RuntimeCodegenStats.snapshot("awsquery");
        assertEquals(roots.size(), after.generated() - before.generated());
        assertEquals(before.unsupported(), after.unsupported());
        assertEquals(before.failed(), after.failed());
    }

    @Test
    void everyGeneratedClassIsDistinctPerVariant() {
        GeneratedQueryCodec aws = new RuntimeCodecRegistry<>(new QueryRuntimeCodegenBackend(AWS_QUERY))
                .get(ScalarStruct.$SCHEMA, new Object());
        GeneratedQueryCodec ec2 = new RuntimeCodecRegistry<>(new QueryRuntimeCodegenBackend(EC2_QUERY))
                .get(ScalarStruct.$SCHEMA, new Object());
        assertNotNull(aws);
        assertNotNull(ec2);
        assertFalse(aws.getClass() == ec2.getClass(), "a variant's codec must not be shared");
    }

    // -- fixtures -------------------------------------------------------------------------------

    private static ScalarStruct fullScalars() {
        return ScalarStruct.builder()
                .stringValue("hello")
                .booleanValue(true)
                .byteValue((byte) 7)
                .shortValue((short) -9)
                .integerValue(Integer.MAX_VALUE)
                .longValue(Long.MIN_VALUE)
                .floatValue(1.5f)
                .doubleValue(-2.25d)
                .bigIntegerValue(new BigInteger("123456789012345678901234567890"))
                .bigDecimalValue(new BigDecimal("1.2345"))
                .blobValue(ByteBuffer.wrap("hi".getBytes(StandardCharsets.UTF_8)))
                .timestampValue(WHEN)
                .dateTimeValue(WHEN)
                .epochValue(WHEN)
                .httpDateValue(WHEN)
                .enumValue(Suit.HEART)
                .intEnumValue(FaceCard.QUEEN)
                .build();
    }

    private static RenameStruct allRenames() {
        return RenameStruct.builder()
                .plain("1")
                .renamed("2")
                .ec2Renamed("3")
                .bothRenamed("4")
                .oddName("5")
                .build();
    }

    private static NestedStruct nested() {
        return NestedStruct.builder()
                .name("outer")
                .inner(MiddleStruct.builder()
                        .label("middle")
                        .inner(LeafStruct.builder()
                                .value("leaf")
                                .numbers(List.of(1, 2))
                                .build())
                        .build())
                .build();
    }

    private static ListStruct fullLists() {
        return ListStruct.builder()
                .strings(List.of("a", "b"))
                .flatStrings(List.of("c"))
                .namedMembers(List.of("d", "e"))
                .flatNamedMembers(List.of("f"))
                .structs(List.of(
                        LeafStruct.builder().value("g").numbers(List.of(1, 2)).build(),
                        LeafStruct.builder().build()))
                .flatStructs(List.of(LeafStruct.builder().value("h").build()))
                .nested(List.of(List.of("i", "j"), List.of("k")))
                .flatNested(List.of(List.of("l"), List.of("m", "n")))
                .enums(List.of(Suit.DIAMOND, Suit.CLUB))
                .timestamps(List.of(WHEN, Instant.EPOCH))
                .blobs(List.of(
                        ByteBuffer.wrap("o".getBytes(StandardCharsets.UTF_8)),
                        ByteBuffer.allocate(0)))
                .sparse(Arrays.asList("p", null, "q"))
                .stringSet(List.of("r", "s"))
                .unions(List.of(
                        new QueryUnion.StringValueMember("t"),
                        new QueryUnion.IntegerValueMember(1)))
                .build();
    }

    private static ListStruct emptyLists() {
        return ListStruct.builder()
                .strings(List.of())
                .flatStrings(List.of())
                .structs(List.of())
                .nested(List.of())
                .build();
    }

    private static MapStruct fullMaps() {
        Map<String, List<String>> lists = new LinkedHashMap<>();
        lists.put("l1", List.of("a", "b"));
        lists.put("l2", List.of());
        Map<String, LeafStruct> structs = new LinkedHashMap<>();
        structs.put("s1", LeafStruct.builder().value("v").numbers(List.of(3)).build());
        Map<String, String> sparse = new LinkedHashMap<>();
        sparse.put("x", null);
        sparse.put("y", "z");
        return MapStruct.builder()
                .strings(ordered("k1", "v1", "k2", "v2"))
                .flatStrings(ordered("k", "v"))
                .named(ordered("nk", "nv"))
                .flatNamed(ordered("fk", "fv"))
                .structs(structs)
                .lists(lists)
                .enumKeyed(Map.of(Suit.CLUB, "c"))
                .sparse(sparse)
                .mapOfMaps(Map.of("m", ordered("mk", "mv")))
                .build();
    }

    private static Map<String, String> ordered(String... keyValues) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put(keyValues[i], keyValues[i + 1]);
        }
        return result;
    }

    // -- comparison -----------------------------------------------------------------------------

    /** Asserts the generated bytes equal the interpreted bytes, and returns them for pinning. */
    private static String assertIdentical(QueryVariant variant, SerializableStruct input) {
        ByteBuffer generated = SmithyGeneratedQuerySerde.serializeGenerated(variant, input, ACTION, VERSION);
        assertNotNull(generated, () -> "no codec was generated for " + input.schema().id() + " " + variant);

        QueryFormSerializer serializer = QueryFormSerializer.acquire(variant, ACTION, VERSION);
        input.serializeMembers(serializer);
        ByteBuffer interpreted = serializer.finish();

        // Text first: a mismatch then reads as a parameter diff rather than a byte offset.
        assertEquals(latin1(interpreted), latin1(generated));
        assertArrayEquals(bytes(interpreted), bytes(generated));
        return latin1(generated);
    }

    private static byte[] bytes(ByteBuffer buffer) {
        ByteBuffer view = buffer.duplicate();
        byte[] result = new byte[view.remaining()];
        view.get(result);
        return result;
    }

    private static String latin1(ByteBuffer buffer) {
        return new String(bytes(buffer), StandardCharsets.ISO_8859_1);
    }
}
