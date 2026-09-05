/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.java.codecs.commons.NumberCodec;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;

class QueryFormSerializerTest {
    /**
     * URL encoding correctness, exercising the same lookup tables and encoding logic as the serializer
     * via {@link AwsQuerySchemaExtensions#encodeName}.
     */
    @Nested
    class UrlEncoding {

        private String urlEncode(String input) {
            return new String(AwsQuerySchemaExtensions.encodeName(input), StandardCharsets.UTF_8);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                "abcdefghijklmnopqrstuvwxyz",
                "0123456789",
                "-._~",
                "Hello",
                "test123",
                "a-b.c_d~e"
        })
        void unreservedCharactersPassThrough(String input) {
            assertThat(urlEncode(input), equalTo(input));
        }

        @ParameterizedTest
        @MethodSource("software.amazon.smithy.java.aws.client.awsquery.QueryFormSerializerTest#reservedCharactersProvider")
        void reservedCharactersArePercentEncoded(String input, String expected) {
            assertThat(urlEncode(input), equalTo(expected));
        }

        @ParameterizedTest
        @MethodSource("software.amazon.smithy.java.aws.client.awsquery.QueryFormSerializerTest#utf8TwoByteProvider")
        void twoByteUtf8CharactersAreEncoded(String input, String expected) {
            assertThat(urlEncode(input), equalTo(expected));
        }

        @ParameterizedTest
        @MethodSource("software.amazon.smithy.java.aws.client.awsquery.QueryFormSerializerTest#utf8ThreeByteProvider")
        void threeByteUtf8CharactersAreEncoded(String input, String expected) {
            assertThat(urlEncode(input), equalTo(expected));
        }

        @ParameterizedTest
        @MethodSource("software.amazon.smithy.java.aws.client.awsquery.QueryFormSerializerTest#utf8FourByteProvider")
        void fourByteUtf8SurrogatePairsAreEncoded(String input, String expected) {
            assertThat(urlEncode(input), equalTo(expected));
        }

        @Test
        void writeUrlEncodedWithEmptyString() {
            assertThat(urlEncode(""), equalTo(""));
        }

        @Test
        void writeUrlEncodedWithMixedContent() {
            assertThat(urlEncode("Hello World! café 日本 🎉"),
                    equalTo("Hello%20World%21%20caf%C3%A9%20%E6%97%A5%E6%9C%AC%20%F0%9F%8E%89"));
        }

        @Test
        void hexEncodingUsesUppercase() {
            String result = urlEncode("ÿ");
            assertThat(result, equalTo("%C3%BF"));
            assertThat(result.contains("a") || result.contains("b")
                    || result.contains("c")
                    || result.contains("d")
                    || result.contains("e")
                    || result.contains("f"), equalTo(false));
        }

        @Test
        void unpairedHighSurrogateIsEncodedAsSingleCharacter() {
            assertThat(urlEncode("a\uD83Cb"), equalTo("a%ED%A0%BCb"));
        }

        @Test
        void highSurrogateFollowedByNonSurrogateEncodesEachSeparately() {
            assertThat(urlEncode("\uD83CX"), equalTo("%ED%A0%BCX"));
        }

        @Test
        void highSurrogateAtEndOfStringIsEncoded() {
            assertThat(urlEncode("test\uD83C"), equalTo("test%ED%A0%BC"));
        }

        @Test
        void lowSurrogateAloneIsEncoded() {
            assertThat(urlEncode("a\uDE89b"), equalTo("a%ED%BA%89b"));
        }
    }

    /**
     * Nested collections: the serializer shares one list/map serializer instance, and a nested
     * collection re-enters and {@code reset()}s it mid-iteration. Without saving and restoring that
     * state around the recursive call, the outer collection's index is clobbered. These tests drive the
     * serializer directly with hand-built schemas and assert the emitted query string.
     */
    @Nested
    class NestedCollections {

        private final Schema innerList = Schema.listBuilder(ShapeId.from("smithy.test#InnerList"))
                .putMember("member", PreludeSchemas.INTEGER)
                .build();

        private final Schema outerList = Schema.listBuilder(ShapeId.from("smithy.test#OuterList"))
                .putMember("member", innerList)
                .build();

        private final Schema stringMap = Schema.mapBuilder(ShapeId.from("smithy.test#StringMap"))
                .putMember("key", PreludeSchemas.STRING)
                .putMember("value", PreludeSchemas.STRING)
                .build();

        // A struct holding a map, used as a map value so the inner map re-enters the top-level writeMap.
        private final Schema structWithMap = Schema.structureBuilder(ShapeId.from("smithy.test#Wrap"))
                .putMember("inner", stringMap)
                .build();

        private final Schema mapOfStructs = Schema.mapBuilder(ShapeId.from("smithy.test#MapOfStructs"))
                .putMember("key", PreludeSchemas.STRING)
                .putMember("value", structWithMap)
                .build();

        @Test
        void listOfListsKeepsOuterIndex() {
            Schema member = outerList.member("member");
            Schema innerMember = innerList.member("member");

            String out = serialize(outerList, (m, ser) -> ser.writeList((Schema) m, null, 2, (st, outer) -> {
                outer.writeList(member, null, 2, (s1, inner1) -> {
                    inner1.writeInteger(innerMember, 10);
                    inner1.writeInteger(innerMember, 20);
                });
                outer.writeList(member, null, 2, (s2, inner2) -> {
                    inner2.writeInteger(innerMember, 30);
                    inner2.writeInteger(innerMember, 40);
                });
            }));

            // Both outer elements must get distinct indices. (The doubled ".member" is the outer element
            // member prefix plus the inner element member prefix.)
            assertThat(out, containsString("OuterList.member.1.member.member.1=10"));
            assertThat(out, containsString("OuterList.member.1.member.member.2=20"));
            assertThat(out, containsString("OuterList.member.2.member.member.1=30"));
            assertThat(out, containsString("OuterList.member.2.member.member.2=40"));
            // Without the fix the second element resets to index 1, colliding with the first.
            assertThat(out, not(containsString("OuterList.member.1.member.member.1=30")));
        }

        @Test
        void mapWithNestedMapValueKeepsOuterEntryIndex() {
            Schema outerKey = mapOfStructs.member("key");
            Schema innerKey = stringMap.member("key");
            Schema innerValue = stringMap.member("value");
            Schema innerMapMember = structWithMap.member("inner");

            SerializableStruct wrapA = wrap(innerMapMember, innerKey, innerValue, "a");
            SerializableStruct wrapB = wrap(innerMapMember, innerKey, innerValue, "b");

            String out = serialize(mapOfStructs, (m, ser) -> ser.writeMap(mapOfStructs, null, 2, (st, outer) -> {
                outer.writeEntry(outerKey, "o1", null, (s1, ov1) -> ov1.writeStruct(structWithMap, wrapA));
                outer.writeEntry(outerKey, "o2", null, (s2, ov2) -> ov2.writeStruct(structWithMap, wrapB));
            }));

            // Both outer entries must keep distinct indices; the inner map's reset() must not clobber them.
            assertThat(out, containsString("entry.1.key=o1"));
            assertThat(out, containsString("entry.2.key=o2"));
            assertThat(out, containsString("entry.1.value.inner.entry.1.value=a"));
            assertThat(out, containsString("entry.2.value.inner.entry.1.value=b"));
            assertThat(out, not(containsString("entry.1.key=o2")));
        }

        /**
         * A map value that is a list resets the shared list serializer, and the enclosing list is the one
         * iterating on it.
         */
        @Test
        void listOfMapsWithListValuesKeepsOuterIndex() {
            Schema intList = Schema.listBuilder(ShapeId.from("smithy.test#IntList"))
                    .putMember("member", PreludeSchemas.INTEGER)
                    .build();
            Schema mapOfLists = Schema.mapBuilder(ShapeId.from("smithy.test#MapOfLists"))
                    .putMember("key", PreludeSchemas.STRING)
                    .putMember("value", intList)
                    .build();
            Schema listOfMaps = Schema.listBuilder(ShapeId.from("smithy.test#ListOfMaps"))
                    .putMember("member", mapOfLists)
                    .build();

            Schema outerMember = listOfMaps.member("member");
            Schema mapKey = mapOfLists.member("key");
            Schema mapValue = mapOfLists.member("value");
            Schema intMember = intList.member("member");

            String out = serialize(listOfMaps, (m, ser) -> ser.writeList((Schema) m, null, 2, (st, outer) -> {
                for (int i = 1; i <= 2; i++) {
                    int n = i;
                    outer.writeMap(outerMember,
                            null,
                            1,
                            (s1, entries) -> entries.writeEntry(mapKey,
                                    "k" + n,
                                    null,
                                    (t, vs) -> vs.writeList(mapValue, null, 2, (s2, items) -> {
                                        items.writeInteger(intMember, n * 10);
                                        items.writeInteger(intMember, n * 10 + 1);
                                    })));
                }
            }));

            // The doubled ".member" is the outer element prefix plus the map member's own name.
            assertThat(out, containsString("ListOfMaps.member.1.member.entry.1.key=k1"));
            assertThat(out, containsString("ListOfMaps.member.1.member.entry.1.value.member.1=10"));
            assertThat(out, containsString("ListOfMaps.member.1.member.entry.1.value.member.2=11"));
            assertThat(out, containsString("ListOfMaps.member.2.member.entry.1.key=k2"));
            assertThat(out, containsString("ListOfMaps.member.2.member.entry.1.value.member.1=20"));
            assertThat(out, containsString("ListOfMaps.member.2.member.entry.1.value.member.2=21"));
            // Without the fix the inner list leaves the shared index at 3, so the second map lands on
            // "member.3" and the two outer elements are no longer consecutive.
            assertThat(out, not(containsString("ListOfMaps.member.3")));
        }

        /** A map value that is itself a map, reached without a struct in between. */
        @Test
        void mapOfMapsKeepsOuterEntryIndex() {
            Schema innerMap = Schema.mapBuilder(ShapeId.from("smithy.test#Inner"))
                    .putMember("key", PreludeSchemas.STRING)
                    .putMember("value", PreludeSchemas.STRING)
                    .build();
            Schema outerMap = Schema.mapBuilder(ShapeId.from("smithy.test#MapOfMaps"))
                    .putMember("key", PreludeSchemas.STRING)
                    .putMember("value", innerMap)
                    .build();

            Schema outerKey = outerMap.member("key");
            Schema outerValue = outerMap.member("value");
            Schema innerKey = innerMap.member("key");
            Schema innerValue = innerMap.member("value");

            String out = serialize(outerMap, (m, ser) -> ser.writeMap((Schema) m, null, 2, (st, outer) -> {
                for (int i = 1; i <= 2; i++) {
                    int n = i;
                    outer.writeEntry(outerKey,
                            "o" + n,
                            null,
                            (s1, ov) -> ov.writeMap(outerValue,
                                    null,
                                    1,
                                    (s2, inner) -> inner
                                            .writeEntry(innerKey,
                                                    "i" + n,
                                                    null,
                                                    (t, vs) -> vs.writeString(
                                                            innerValue,
                                                            "v" + n))));
                }
            }));

            assertThat(out, containsString("MapOfMaps.entry.1.key=o1"));
            assertThat(out, containsString("MapOfMaps.entry.1.value.entry.1.value=v1"));
            assertThat(out, containsString("MapOfMaps.entry.2.key=o2"));
            assertThat(out, containsString("MapOfMaps.entry.2.value.entry.1.value=v2"));
            // Without the fix the inner map leaves the shared index at 2, so the second entry is "entry.3".
            assertThat(out, not(containsString("MapOfMaps.entry.3")));
        }

        /** EC2 Query has its own list writer, which shares the same list serializer instance. */
        @Test
        void ec2ListOfListsKeepsOuterIndex() {
            Schema member = outerList.member("member");
            Schema innerMember = innerList.member("member");

            String out = QueryFormSerializerTest.serialize(QueryFormSerializer.QueryVariant.EC2_QUERY,
                    outerList,
                    (m, ser) -> ser.writeList((Schema) m, null, 2, (st, outer) -> {
                        outer.writeList(member, null, 2, (s1, inner1) -> {
                            inner1.writeInteger(innerMember, 10);
                            inner1.writeInteger(innerMember, 20);
                        });
                        outer.writeList(member, null, 2, (s2, inner2) -> {
                            inner2.writeInteger(innerMember, 30);
                            inner2.writeInteger(innerMember, 40);
                        });
                    }));

            // EC2 lists are always flattened, so the element index follows the prefix directly; the
            // capitalized "Member" is the inner list member's name under EC2 naming.
            assertThat(out, containsString("OuterList.1.Member.1=10"));
            assertThat(out, containsString("OuterList.1.Member.2=20"));
            assertThat(out, containsString("OuterList.2.Member.1=30"));
            assertThat(out, containsString("OuterList.2.Member.2=40"));
            assertThat(out, not(containsString("OuterList.3")));
        }

        @Test
        void flatListIsUnaffected() {
            Schema member = innerList.member("member");
            String out = serialize(innerList, (m, ser) -> ser.writeList((Schema) m, null, 3, (st, list) -> {
                list.writeInteger(member, 7);
                list.writeInteger(member, 8);
                list.writeInteger(member, 9);
            }));
            assertThat(out, containsString("InnerList.member.1=7"));
            assertThat(out, containsString("InnerList.member.2=8"));
            assertThat(out, containsString("InnerList.member.3=9"));
        }

        private String serialize(Schema memberSchema, BiConsumer<Object, ShapeSerializer> writeMember) {
            return QueryFormSerializerTest
                    .serialize(QueryFormSerializer.QueryVariant.AWS_QUERY, memberSchema, writeMember);
        }

        // A struct {inner: {i: <value>}} that serializes its map member.
        private SerializableStruct wrap(Schema innerMapMember, Schema innerKey, Schema innerValue, String value) {
            return new SerializableStruct() {
                @Override
                public Schema schema() {
                    return structWithMap;
                }

                @Override
                public void serializeMembers(ShapeSerializer serializer) {
                    serializer.writeMap(innerMapMember,
                            null,
                            1,
                            (st, map) -> map
                                    .writeEntry(innerKey, "i", null, (t, vs) -> vs.writeString(innerValue, value)));
                }

                @Override
                public <T> T getMemberValue(Schema m) {
                    return null;
                }
            };
        }
    }

    /**
     * Buffer bounds.
     *
     * <p>Every parameter is written by reserving a computed upper bound and then encoding into the
     * reserved space with no further checks, so a bound that under-counts is an out-of-bounds array
     * write rather than a wrong answer. These are the cases where the bound is not simply the value's
     * length: a character can encode to nine bytes, and a {@code BigInteger} has no fixed length at all.
     */
    @Nested
    class BufferBounds {

        /** Three UTF-8 bytes, each percent-encoding to three: nine bytes for one {@code char}. */
        private static final String CJK = "日";
        private static final String CJK_ENCODED = "%E6%97%A5";

        /** Long enough that the encoded form overruns both the reservation and the initial buffer. */
        private static final int LONG = 400;

        @Test
        void longNonAsciiStringIsWrittenInFull() {
            String out = serializeString(CJK.repeat(LONG));
            assertThat(out, equalTo(header() + "&String=" + CJK_ENCODED.repeat(LONG)));
        }

        @Test
        void asciiPrefixIsWrittenOnceWhenTheTailIsNotAscii() {
            String out = serializeString("a".repeat(LONG) + CJK.repeat(LONG));
            assertThat(out, equalTo(header() + "&String=" + "a".repeat(LONG) + CJK_ENCODED.repeat(LONG)));
        }

        @Test
        void longSurrogatePairStringIsWrittenInFull() {
            String out = serializeString("🎉".repeat(LONG));
            assertThat(out, equalTo(header() + "&String=" + "%F0%9F%8E%89".repeat(LONG)));
        }

        @Test
        void longNonAsciiListElementIsWrittenInFull() {
            Schema list = Schema.listBuilder(ShapeId.from("smithy.test#StringList"))
                    .putMember("member", PreludeSchemas.STRING)
                    .build();
            Schema element = list.member("member");
            String out = serialize(QueryFormSerializer.QueryVariant.AWS_QUERY,
                    list,
                    (m, ser) -> ser.writeList((Schema) m,
                            null,
                            1,
                            (st, items) -> items.writeString(element, CJK.repeat(LONG))));
            assertThat(out, equalTo(header() + "&StringList.member.1=" + CJK_ENCODED.repeat(LONG)));
        }

        @Test
        void longNonAsciiMapKeyAndValueAreWrittenInFull() {
            Schema map = Schema.mapBuilder(ShapeId.from("smithy.test#StringMap"))
                    .putMember("key", PreludeSchemas.STRING)
                    .putMember("value", PreludeSchemas.STRING)
                    .build();
            Schema key = map.member("key");
            Schema value = map.member("value");
            String big = CJK.repeat(LONG);
            String encoded = CJK_ENCODED.repeat(LONG);
            String out = serialize(QueryFormSerializer.QueryVariant.AWS_QUERY,
                    map,
                    (m, ser) -> ser.writeMap((Schema) m,
                            null,
                            1,
                            (st, entries) -> entries
                                    .writeEntry(key, big, null, (t, vs) -> vs.writeString(value, big))));
            assertThat(out,
                    equalTo(header() + "&StringMap.entry.1.key=" + encoded
                            + "&StringMap.entry.1.value=" + encoded));
        }

        @Test
        void arbitrarilyLongBigIntegerIsWrittenInFull() {
            BigInteger value = BigInteger.TEN.pow(2000);
            String out = serialize(QueryFormSerializer.QueryVariant.AWS_QUERY,
                    PreludeSchemas.BIG_INTEGER,
                    (m, ser) -> ser.writeBigInteger((Schema) m, value));
            assertThat(out, equalTo(header() + "&BigInteger=" + value));
        }

        @Test
        void arbitrarilyLongBigIntegerListElementIsWrittenInFull() {
            Schema list = Schema.listBuilder(ShapeId.from("smithy.test#BigList"))
                    .putMember("member", PreludeSchemas.BIG_INTEGER)
                    .build();
            Schema element = list.member("member");
            BigInteger value = BigInteger.TEN.pow(2000);
            String out = serialize(QueryFormSerializer.QueryVariant.AWS_QUERY,
                    list,
                    (m, ser) -> ser.writeList((Schema) m,
                            null,
                            1,
                            (st, items) -> items.writeBigInteger(element, value)));
            assertThat(out, equalTo(header() + "&BigList.member.1=" + value));
        }

        @Test
        void arbitrarilyLongBigIntegerMapValueIsWrittenInFull() {
            Schema map = Schema.mapBuilder(ShapeId.from("smithy.test#BigMap"))
                    .putMember("key", PreludeSchemas.STRING)
                    .putMember("value", PreludeSchemas.BIG_INTEGER)
                    .build();
            Schema key = map.member("key");
            Schema value = map.member("value");
            BigInteger big = BigInteger.TEN.pow(2000);
            String out = serialize(QueryFormSerializer.QueryVariant.AWS_QUERY,
                    map,
                    (m, ser) -> ser.writeMap((Schema) m,
                            null,
                            1,
                            (st, entries) -> entries
                                    .writeEntry(key, "k", null, (t, vs) -> vs.writeBigInteger(value, big))));
            assertThat(out, equalTo(header() + "&BigMap.entry.1.key=k&BigMap.entry.1.value=" + big));
        }

        @Test
        void maxBigIntegerLengthBoundsDecimalEncoding() {
            BigInteger[] boundaries = {
                    BigInteger.ZERO,
                    BigInteger.ONE,
                    BigInteger.ONE.negate(),
                    BigInteger.TEN.pow(18),
                    BigInteger.TEN.pow(18).negate(),
                    BigInteger.TEN.pow(2000),
                    BigInteger.TEN.pow(2000).negate(),
            };
            for (BigInteger value : boundaries) {
                assertBigIntegerFits(value);
            }

            for (int bits = 1; bits <= 4096; bits += 31) {
                BigInteger powerOfTwo = BigInteger.ONE.shiftLeft(bits);
                BigInteger belowPowerOfTwo = powerOfTwo.subtract(BigInteger.ONE);
                assertBigIntegerFits(powerOfTwo);
                assertBigIntegerFits(powerOfTwo.negate());
                assertBigIntegerFits(belowPowerOfTwo);
                assertBigIntegerFits(belowPowerOfTwo.negate());
            }
        }

        /**
         * The serializer is pooled, so a body wrapping the pooled array would be rewritten in place by
         * the next request on the same thread.
         */
        @Test
        void finishDoesNotAliasThePooledBuffer() {
            ByteBuffer first = QueryFormSerializer
                    .acquire(QueryFormSerializer.QueryVariant.AWS_QUERY, "A1", "V1")
                    .finish();
            ByteBuffer second = QueryFormSerializer
                    .acquire(QueryFormSerializer.QueryVariant.AWS_QUERY, "A2", "V2")
                    .finish();
            assertThat(first.array(), not(sameInstance(second.array())));
        }

        @Test
        void finishedBodyIsUnaffectedByTheNextSerializer() {
            ByteBuffer first = QueryFormSerializer
                    .acquire(QueryFormSerializer.QueryVariant.AWS_QUERY, "A1", "V1")
                    .finish();
            QueryFormSerializer
                    .acquire(QueryFormSerializer.QueryVariant.AWS_QUERY, "A2", "V2")
                    .finish();
            assertThat(StandardCharsets.UTF_8.decode(first).toString(), equalTo("Action=A1&Version=V1"));
        }

        @Test
        void finishTransfersOversizedBuffer() {
            String value = "a".repeat(8192);
            ByteBuffer body = serializeStringBuffer(value);

            // The returned view has an exact limit, but keeps the oversized backing array rather than
            // copying it immediately before the pool would discard it.
            assertThat(body.array().length > body.remaining(), equalTo(true));
            QueryFormSerializer.acquire(QueryFormSerializer.QueryVariant.AWS_QUERY, "A2", "V2").finish();
            assertThat(StandardCharsets.UTF_8.decode(body).toString(),
                    equalTo(header() + "&String=" + value));
        }

        private void assertBigIntegerFits(BigInteger value) {
            int maxLength = QueryFormSerializer.maxBigIntegerLength(value);
            byte[] bytes = new byte[maxLength];
            int end = NumberCodec.writeBigInteger(bytes, 0, value);
            assertThat(end, lessThanOrEqualTo(maxLength));
            assertThat(new String(bytes, 0, end, StandardCharsets.US_ASCII), equalTo(value.toString()));
        }

        private String serializeString(String value) {
            return StandardCharsets.UTF_8.decode(serializeStringBuffer(value)).toString();
        }

        private ByteBuffer serializeStringBuffer(String value) {
            Schema struct = Schema.structureBuilder(ShapeId.from("smithy.test#Outer"))
                    .putMember("String", PreludeSchemas.STRING)
                    .build();
            QueryFormSerializer serializer = QueryFormSerializer.acquire(
                    QueryFormSerializer.QueryVariant.AWS_QUERY,
                    "TestAction",
                    "2020-01-01");
            serializer.writeString(struct.member("String"), value);
            return serializer.finish();
        }

        private String header() {
            return "Action=TestAction&Version=2020-01-01";
        }
    }

    /**
     * Wraps {@code memberSchema} in a single-member structure, serializes it through the serializer, and
     * returns the query string.
     *
     * <p>{@code writeMember} receives the member schema of the wrapper struct, not {@code memberSchema}
     * itself, because that is what a generated or hand-written shape would pass.
     */
    static String serialize(
            QueryFormSerializer.QueryVariant variant,
            Schema memberSchema,
            BiConsumer<Object, ShapeSerializer> writeMember
    ) {
        Schema structSchema = Schema.structureBuilder(ShapeId.from("smithy.test#Outer"))
                .putMember(memberSchema.id().getName(), memberSchema)
                .build();
        Schema member = structSchema.member(memberSchema.id().getName());

        SerializableStruct struct = new SerializableStruct() {
            @Override
            public Schema schema() {
                return structSchema;
            }

            @Override
            public void serializeMembers(ShapeSerializer serializer) {
                writeMember.accept(member, serializer);
            }

            @Override
            public <T> T getMemberValue(Schema m) {
                return null;
            }
        };

        QueryFormSerializer s = QueryFormSerializer.acquire(variant, "TestAction", "2020-01-01");
        s.writeStruct(structSchema, struct);
        return StandardCharsets.UTF_8.decode(s.finish()).toString();
    }

    static Stream<Arguments> reservedCharactersProvider() {
        return Stream.of(
                Arguments.of(" ", "%20"),
                Arguments.of("!", "%21"),
                Arguments.of("#", "%23"),
                Arguments.of("$", "%24"),
                Arguments.of("%", "%25"),
                Arguments.of("&", "%26"),
                Arguments.of("'", "%27"),
                Arguments.of("(", "%28"),
                Arguments.of(")", "%29"),
                Arguments.of("*", "%2A"),
                Arguments.of("+", "%2B"),
                Arguments.of(",", "%2C"),
                Arguments.of("/", "%2F"),
                Arguments.of(":", "%3A"),
                Arguments.of(";", "%3B"),
                Arguments.of("=", "%3D"),
                Arguments.of("?", "%3F"),
                Arguments.of("@", "%40"),
                Arguments.of("[", "%5B"),
                Arguments.of("]", "%5D"),
                Arguments.of("hello world", "hello%20world"),
                Arguments.of("a=b&c=d", "a%3Db%26c%3Dd"),
                Arguments.of("foo/bar", "foo%2Fbar"));
    }

    static Stream<Arguments> utf8TwoByteProvider() {
        return Stream.of(
                Arguments.of("é", "%C3%A9"),
                Arguments.of("ñ", "%C3%B1"),
                Arguments.of("ü", "%C3%BC"),
                Arguments.of("café", "caf%C3%A9"),
                Arguments.of("©", "%C2%A9"));
    }

    static Stream<Arguments> utf8ThreeByteProvider() {
        return Stream.of(
                Arguments.of("€", "%E2%82%AC"),
                Arguments.of("中", "%E4%B8%AD"),
                Arguments.of("日本", "%E6%97%A5%E6%9C%AC"),
                Arguments.of("☃", "%E2%98%83"));
    }

    static Stream<Arguments> utf8FourByteProvider() {
        return Stream.of(
                Arguments.of("🎉", "%F0%9F%8E%89"),
                Arguments.of("😀", "%F0%9F%98%80"),
                Arguments.of("𝄞", "%F0%9D%84%9E"),
                Arguments.of("hello🎉world", "hello%F0%9F%8E%89world"));
    }
}
