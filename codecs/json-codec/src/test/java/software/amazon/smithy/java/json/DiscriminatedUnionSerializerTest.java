/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.DiscriminatedTrait;

/**
 * Verifies that the JSON serializer encodes a {@code @discriminated} union as a flat, internally
 * tagged object instead of the standard externally tagged union encoding. Runs against both JSON
 * backends.
 */
public class DiscriminatedUnionSerializerTest extends ProviderTestBase {

    // Shape := @discriminated("type") union { circle: CircleData, rectangle: RectangleData }
    // CircleData := structure { radius: Integer }
    // RectangleData := structure { width: Integer, height: Integer }
    private static final ShapeId SHAPE_ID = ShapeId.from("smithy.example#Shape");
    private static final ShapeId CIRCLE_ID = ShapeId.from("smithy.example#CircleData");
    private static final ShapeId RECTANGLE_ID = ShapeId.from("smithy.example#RectangleData");

    private static final Schema SHAPE_SCHEMA;
    private static final Schema CIRCLE_SCHEMA;
    private static final Schema RECTANGLE_SCHEMA;
    private static final Schema CIRCLE_RADIUS_MEMBER;
    private static final Schema RECTANGLE_WIDTH_MEMBER;
    private static final Schema RECTANGLE_HEIGHT_MEMBER;

    static {
        var shapeBuilder = Schema.unionBuilder(SHAPE_ID, new DiscriminatedTrait("type"));
        var circleBuilder = Schema.structureBuilder(CIRCLE_ID);
        var rectangleBuilder = Schema.structureBuilder(RECTANGLE_ID);

        circleBuilder.putMember("radius", Schema.createInteger(CIRCLE_ID.withMember("radius")));
        rectangleBuilder.putMember("width", Schema.createInteger(RECTANGLE_ID.withMember("width")));
        rectangleBuilder.putMember("height", Schema.createInteger(RECTANGLE_ID.withMember("height")));
        shapeBuilder.putMember("circle", circleBuilder);
        shapeBuilder.putMember("rectangle", rectangleBuilder);

        SHAPE_SCHEMA = shapeBuilder.build();
        CIRCLE_SCHEMA = circleBuilder.build();
        RECTANGLE_SCHEMA = rectangleBuilder.build();
        CIRCLE_RADIUS_MEMBER = CIRCLE_SCHEMA.member("radius");
        RECTANGLE_WIDTH_MEMBER = RECTANGLE_SCHEMA.member("width");
        RECTANGLE_HEIGHT_MEMBER = RECTANGLE_SCHEMA.member("height");
    }

    private record Circle(int radius) implements SerializableStruct {
        @Override
        public Schema schema() {
            return CIRCLE_SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeInteger(CIRCLE_RADIUS_MEMBER, radius);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }
    }

    private record Rectangle(int width, int height) implements SerializableStruct {
        @Override
        public Schema schema() {
            return RECTANGLE_SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeInteger(RECTANGLE_WIDTH_MEMBER, width);
            serializer.writeInteger(RECTANGLE_HEIGHT_MEMBER, height);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }
    }

    // Union value with the "circle" member set, mirroring the sealed-interface-of-records shape UnionGenerator
    // emits for a generated union (one record per variant implementing the union's SerializableStruct).
    private record ShapeCircle(Circle circle) implements SerializableStruct {
        private static final Schema CIRCLE_MEMBER = SHAPE_SCHEMA.member("circle");

        @Override
        public Schema schema() {
            return SHAPE_SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct(CIRCLE_MEMBER, circle);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }
    }

    private record ShapeRectangle(Rectangle rectangle) implements SerializableStruct {
        private static final Schema RECTANGLE_MEMBER = SHAPE_SCHEMA.member("rectangle");

        @Override
        public Schema schema() {
            return SHAPE_SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct(RECTANGLE_MEMBER, rectangle);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void writesDiscriminatorFirstFollowedByInlinedVariantFields(JsonSerdeProvider provider) {
        var result = serialize(provider, new ShapeCircle(new Circle(5)));
        assertThat(result, equalTo("{\"type\":\"circle\",\"radius\":5}"));
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void inlinesMultipleVariantFieldsAsSiblings(JsonSerdeProvider provider) {
        var result = serialize(provider, new ShapeRectangle(new Rectangle(3, 4)));
        assertThat(result, equalTo("{\"type\":\"rectangle\",\"width\":3,\"height\":4}"));
    }

    private static String serialize(JsonSerdeProvider provider, SerializableStruct value) {
        try (
                JsonCodec codec = codec(provider);
                var output = new ByteArrayOutputStream();
                var serializer = codec.createSerializer(output)) {
            value.serialize(serializer);
            serializer.flush();
            return output.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
