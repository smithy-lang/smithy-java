/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.DiscriminatedTrait;

/**
 * Covers the native decoder's handling of a {@code @discriminated} union whose discriminator is not the first field:
 * it skips the fields in front of the discriminator, then reads the object again from the start into the variant.
 */
final class DiscriminatedUnionScanTest {

    // Shape := @discriminated("type") union { circle: Circle }
    // Circle := structure { radius: Integer }
    private static final Schema SHAPE;
    private static final Schema CIRCLE_MEMBER;

    // Node := @discriminated("kind") union { wrap: Wrap, leaf: Leaf }
    // Wrap := structure { inner: Node, extra: String }
    // Leaf := structure { value: String }
    private static final Schema NODE;

    static {
        var circle = Schema.structureBuilder(ShapeId.from("smithy.example#Circle"));
        circle.putMember("radius", PreludeSchemas.INTEGER);
        var shape = Schema.unionBuilder(ShapeId.from("smithy.example#Shape"), new DiscriminatedTrait("type"));
        shape.putMember("circle", circle);
        SHAPE = shape.build();
        CIRCLE_MEMBER = SHAPE.member("circle");

        var node = Schema.unionBuilder(ShapeId.from("smithy.example#Node"), new DiscriminatedTrait("kind"));
        var wrap = Schema.structureBuilder(ShapeId.from("smithy.example#Wrap"));
        wrap.putMember("inner", node);
        wrap.putMember("extra", PreludeSchemas.STRING);
        var leaf = Schema.structureBuilder(ShapeId.from("smithy.example#Leaf"));
        leaf.putMember("value", PreludeSchemas.STRING);
        node.putMember("wrap", wrap);
        node.putMember("leaf", leaf);
        NODE = node.build();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"type\":\"circle\",\"radius\":5}",
            "{\"radius\":5,\"type\":\"circle\"}",
            "{\"other\":{\"type\":\"x\"},\"radius\":5,\"type\":\"circle\",\"more\":[1,2]}"
    })
    void discriminatorIsNotReportedAsAnUnknownMember(String json) {
        var unknown = new ArrayList<String>();
        int[] radius = new int[1];
        try (var de = deserializer(json)) {
            de.readStruct(SHAPE, unknown, (state, member, d) -> {
                assertThat(member).isSameAs(CIRCLE_MEMBER);
                d.readStruct(member, state, new ShapeDeserializer.StructMemberConsumer<List<String>>() {
                    @Override
                    public void accept(List<String> s, Schema m, ShapeDeserializer md) {
                        radius[0] = md.readInteger(m);
                    }

                    @Override
                    public void unknownMember(List<String> s, String memberName) {
                        s.add(memberName);
                    }
                });
            });
        }

        assertThat(radius[0]).isEqualTo(5);
        assertThat(unknown).doesNotContain("type");
    }

    // The fields in front of the discriminator are skipped, then read again into the variant. Malformed content
    // in them must be rejected.
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"radius\":01,\"type\":\"circle\"}",
            "{\"radius\":1 2,\"type\":\"circle\"}",
            "{\"bogus\":tru,\"type\":\"circle\",\"radius\":1}",
            "{\"bogus\":nul,\"type\":\"circle\",\"radius\":1}",
            "{\"bogus\":1.,\"type\":\"circle\",\"radius\":1}",
            "{\"bogus\":[1},\"type\":\"circle\",\"radius\":1}",
            "{\"bogus\":{\"x\":1,},\"type\":\"circle\",\"radius\":1}",
            "{\"bogus\":{\"x\" 1},\"type\":\"circle\",\"radius\":1}",
            "{\"bogus\":\"\\q\",\"type\":\"circle\",\"radius\":1}",
            "{\"bogus\":\"a\u0001b\",\"type\":\"circle\",\"radius\":1}",
            "{\"bog\u0001us\":1,\"type\":\"circle\",\"radius\":1}",
            "{\"bogus\":\"abc,\"type\":\"circle\",\"radius\":1}"
    })
    void malformedContentBeforeTheDiscriminatorIsRejected(String json) {
        assertThatThrownBy(() -> {
            try (var de = deserializer(json)) {
                de.readStruct(SHAPE, null, (state, member, d) -> {
                    d.readStruct(member, null, (s, m, md) -> md.readInteger(m));
                });
            }
        }).isInstanceOf(SerializationException.class);
    }

    @Test
    void deeplyNestedUnionsWithTheDiscriminatorLastAreReadInLinearTime() {
        int levels = 300;
        var json = new StringBuilder();
        for (int i = 0; i < levels; i++) {
            json.append("{\"inner\":");
        }
        json.append("{\"value\":\"bottom\",\"kind\":\"leaf\"}");
        for (int i = 0; i < levels; i++) {
            json.append(",\"extra\":\"level\",\"kind\":\"wrap\"}");
        }
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);

        var reached = new Reached();
        var de = new SmithyJsonDeserializer(bytes, 0, bytes.length, JsonSettings.builder().build());
        try {
            readNode(de, NODE, reached, 0);
            // Without a bound, each level would skip everything inside it again, about levels / 2 times the
            // input in total. The budget allows twice the input, and the scan that spends it skips at most the
            // input once more.
            assertThat(de.rescanBudget()).isLessThanOrEqualTo(0).isGreaterThan(-bytes.length);
        } finally {
            de.close();
        }

        assertThat(reached.depth).isEqualTo(levels);
        assertThat(reached.value).isEqualTo("bottom");
    }

    private static final class Reached {
        int depth;
        String value;
    }

    private static void readNode(ShapeDeserializer de, Schema schema, Reached reached, int depth) {
        de.readStruct(schema, reached, (state, member, d) -> {
            if (member.memberName().equals("leaf")) {
                d.readStruct(member, state, (s, m, md) -> {
                    s.depth = depth;
                    s.value = md.readString(m);
                });
            } else {
                d.readStruct(member, state, (s, m, md) -> {
                    if (m.memberName().equals("inner")) {
                        readNode(md, m, s, depth + 1);
                    } else {
                        assertThat(md.readString(m)).isEqualTo("level");
                    }
                });
            }
        });
    }

    private static SmithyJsonDeserializer deserializer(String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return new SmithyJsonDeserializer(bytes, 0, bytes.length, JsonSettings.builder().build());
    }
}
