/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.pattern.UriPattern;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpTrait;

public class PathSerializerTest {

    private static final ShapeId INPUT_ID = ShapeId.from("smithy.example#Input");
    private static final ShapeId COLOR_ID = ShapeId.from("smithy.example#Color");
    private static final ShapeId COUNT_ID = ShapeId.from("smithy.example#Count");

    private static HttpTrait httpTrait(String uri) {
        return HttpTrait.builder().method("GET").uri(UriPattern.parse(uri)).build();
    }

    // Each case binds a single member `v` as a label and asserts the serialized path. Cases cover every label type
    // supported by HTTP bindings (string, enum, intEnum, boolean, the numerics, and timestamp) in each of the three
    // value shapes a struct can hand back: a raw Java value, a SmithyEnum/SmithyIntEnum, and a wrapping Document.
    static List<Arguments> labelCases() {
        var ts = Instant.parse("2020-01-02T03:04:05Z");
        return List.of(
                // string
                Arguments.of(PreludeSchemas.STRING, "abc", "/foo/abc"),
                Arguments.of(PreludeSchemas.STRING, Document.of("abc"), "/foo/abc"),
                // enum
                Arguments.of(enumSchema(), (SmithyEnum) () -> "RED", "/foo/RED"),
                Arguments.of(enumSchema(), Document.of("RED"), "/foo/RED"),
                // boolean
                Arguments.of(PreludeSchemas.BOOLEAN, true, "/foo/true"),
                Arguments.of(PreludeSchemas.BOOLEAN, Document.of(true), "/foo/true"),
                // byte
                Arguments.of(PreludeSchemas.BYTE, (byte) 7, "/foo/7"),
                Arguments.of(PreludeSchemas.BYTE, Document.of((byte) 7), "/foo/7"),
                // short
                Arguments.of(PreludeSchemas.SHORT, (short) 8, "/foo/8"),
                Arguments.of(PreludeSchemas.SHORT, Document.of((short) 8), "/foo/8"),
                // integer
                Arguments.of(PreludeSchemas.INTEGER, 9, "/foo/9"),
                Arguments.of(PreludeSchemas.INTEGER, Document.of(9), "/foo/9"),
                // intEnum
                Arguments.of(intEnumSchema(), (SmithyIntEnum) () -> 2, "/foo/2"),
                Arguments.of(intEnumSchema(), Document.of(2), "/foo/2"),
                // long
                Arguments.of(PreludeSchemas.LONG, 10L, "/foo/10"),
                Arguments.of(PreludeSchemas.LONG, Document.of(10L), "/foo/10"),
                // float
                Arguments.of(PreludeSchemas.FLOAT, 1.5f, "/foo/1.5"),
                Arguments.of(PreludeSchemas.FLOAT, Document.of(1.5f), "/foo/1.5"),
                // double
                Arguments.of(PreludeSchemas.DOUBLE, 2.5d, "/foo/2.5"),
                Arguments.of(PreludeSchemas.DOUBLE, Document.of(2.5d), "/foo/2.5"),
                // bigInteger
                Arguments.of(PreludeSchemas.BIG_INTEGER, BigInteger.valueOf(11), "/foo/11"),
                Arguments.of(PreludeSchemas.BIG_INTEGER, Document.of(BigInteger.valueOf(11)), "/foo/11"),
                // bigDecimal
                Arguments.of(PreludeSchemas.BIG_DECIMAL, new BigDecimal("12.5"), "/foo/12.5"),
                Arguments.of(PreludeSchemas.BIG_DECIMAL, Document.of(new BigDecimal("12.5")), "/foo/12.5"),
                // timestamp (default date-time / ISO-8601 format for a path label; ':' is percent-encoded)
                Arguments.of(PreludeSchemas.TIMESTAMP, ts, "/foo/2020-01-02T03%3A04%3A05Z"),
                Arguments.of(PreludeSchemas.TIMESTAMP, Document.of(ts), "/foo/2020-01-02T03%3A04%3A05Z"));
    }

    @ParameterizedTest
    @MethodSource("labelCases")
    public void serializesLabel(Schema memberTarget, Object value, String expected) {
        var schema = Schema.structureBuilder(INPUT_ID)
                .putMember("v", memberTarget, new HttpLabelTrait())
                .build();
        var serializer = new PathSerializer(httpTrait("/foo/{v}"), schema);

        assertEquals(expected, serializer.serialize(struct(schema, Map.of("v", value))));
    }

    private static Schema enumSchema() {
        return Schema.createEnum(COLOR_ID, Set.of("RED", "GREEN"));
    }

    private static Schema intEnumSchema() {
        return Schema.createIntEnum(COUNT_ID, Set.of(1, 2));
    }

    private static SerializableStruct struct(Schema schema, Map<String, Object> values) {
        return new SerializableStruct() {
            @Override
            public Schema schema() {
                return schema;
            }

            @Override
            public void serializeMembers(ShapeSerializer serializer) {
                // Not exercised by PathSerializer.
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T getMemberValue(Schema member) {
                return (T) values.get(member.memberName());
            }
        };
    }
}
