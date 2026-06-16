/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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

        // Serialize a single-member struct via writeMember and return the query string.
        private String serialize(Schema memberSchema, BiConsumer<Object, ShapeSerializer> writeMember) {
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

            QueryFormSerializer s = QueryFormSerializer.acquire(
                    QueryFormSerializer.QueryVariant.AWS_QUERY,
                    "TestAction",
                    "2020-01-01");
            s.writeStruct(structSchema, struct);
            return StandardCharsets.UTF_8.decode(s.finish()).toString();
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
