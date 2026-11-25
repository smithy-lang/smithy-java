/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.DiscriminatedTrait;

/**
 * Verifies that the JSON deserializer decodes a {@code @discriminated} union from the flat,
 * internally tagged encoding, covering the discriminator-first fast path (native backend), the
 * discriminator-last document fallback, and unknown-variant handling. Runs against both JSON
 * backends.
 *
 * <p>The hand-rolled builders below mirror the shape of generated code: a union builder whose
 * {@code deserialize} drives {@code readStruct} with a {@link ShapeDeserializer.StructMemberConsumer}
 * that dispatches on member index to a variant struct builder, exactly as
 * {@code StructureDeserializerGenerator} emits for a generated union.
 */
public class DiscriminatedUnionDeserializerTest extends ProviderTestBase {

    private static final ShapeId SHAPE_ID = ShapeId.from("smithy.example#Shape");
    private static final ShapeId CIRCLE_ID = ShapeId.from("smithy.example#CircleData");
    private static final ShapeId RECTANGLE_ID = ShapeId.from("smithy.example#RectangleData");

    private static final Schema SHAPE_SCHEMA;
    private static final Schema CIRCLE_SCHEMA;
    private static final Schema RECTANGLE_SCHEMA;
    private static final Schema CIRCLE_MEMBER;
    private static final Schema RECTANGLE_MEMBER;
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
        CIRCLE_MEMBER = SHAPE_SCHEMA.member("circle");
        RECTANGLE_MEMBER = SHAPE_SCHEMA.member("rectangle");
        CIRCLE_RADIUS_MEMBER = CIRCLE_SCHEMA.member("radius");
        RECTANGLE_WIDTH_MEMBER = RECTANGLE_SCHEMA.member("width");
        RECTANGLE_HEIGHT_MEMBER = RECTANGLE_SCHEMA.member("height");
    }

    // ----- Variant value types -----
    //
    // Mirrors generated code: Circle/Rectangle are the plain variant structs; the Shape union is a
    // sealed interface whose variant records (ShapeCircle/ShapeRectangle/ShapeUnknown) wrap a struct
    // value, report the UNION schema, and serialize by writing their union member.

    private sealed interface Shape extends SerializableStruct permits ShapeCircle, ShapeRectangle, ShapeUnknown {}

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

    private record ShapeCircle(Circle circle) implements Shape {
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

    private record ShapeRectangle(Rectangle rectangle) implements Shape {
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

    private record ShapeUnknown(String memberName) implements Shape {
        @Override
        public Schema schema() {
            return SHAPE_SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }
    }

    // ----- Builders mirroring generated code -----

    private static final class CircleBuilder implements ShapeBuilder<Circle> {
        private int radius;

        @Override
        public Schema schema() {
            return CIRCLE_SCHEMA;
        }

        @Override
        public Circle build() {
            return new Circle(radius);
        }

        @Override
        public ShapeBuilder<Circle> deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(CIRCLE_SCHEMA, this, (state, member, de) -> {
                if (member.memberIndex() == 0) {
                    state.radius = de.readInteger(member);
                }
            });
            return this;
        }

        @Override
        public ShapeBuilder<Circle> deserializeMember(ShapeDeserializer decoder, Schema schema) {
            decoder.readStruct(schema.assertMemberTargetIs(CIRCLE_SCHEMA), this, (state, member, de) -> {
                if (member.memberIndex() == 0) {
                    state.radius = de.readInteger(member);
                }
            });
            return this;
        }
    }

    private static final class RectangleBuilder implements ShapeBuilder<Rectangle> {
        private int width;
        private int height;

        @Override
        public Schema schema() {
            return RECTANGLE_SCHEMA;
        }

        @Override
        public Rectangle build() {
            return new Rectangle(width, height);
        }

        @Override
        public ShapeBuilder<Rectangle> deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(RECTANGLE_SCHEMA, this, RectangleBuilder::accept);
            return this;
        }

        @Override
        public ShapeBuilder<Rectangle> deserializeMember(ShapeDeserializer decoder, Schema schema) {
            decoder.readStruct(schema.assertMemberTargetIs(RECTANGLE_SCHEMA), this, RectangleBuilder::accept);
            return this;
        }

        private static void accept(RectangleBuilder state, Schema member, ShapeDeserializer de) {
            switch (member.memberIndex()) {
                case 0 -> state.width = de.readInteger(member);
                case 1 -> state.height = de.readInteger(member);
                default -> throw new IllegalArgumentException("Unexpected member: " + member.memberName());
            }
        }
    }

    private static final class ShapeBuilderImpl implements ShapeBuilder<Shape> {
        private Shape value;

        @Override
        public Schema schema() {
            return SHAPE_SCHEMA;
        }

        @Override
        public Shape build() {
            return value;
        }

        @Override
        public ShapeBuilder<Shape> deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SHAPE_SCHEMA, this, InnerDeserializer.INSTANCE);
            return this;
        }

        private static final class InnerDeserializer
                implements ShapeDeserializer.StructMemberConsumer<ShapeBuilderImpl> {
            private static final InnerDeserializer INSTANCE = new InnerDeserializer();

            @Override
            public void accept(ShapeBuilderImpl builder, Schema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.value =
                            new ShapeCircle(new CircleBuilder().deserializeMember(de, member).build());
                    case 1 -> builder.value =
                            new ShapeRectangle(new RectangleBuilder().deserializeMember(de, member).build());
                    default -> throw new IllegalArgumentException("Unexpected member: " + member.memberName());
                }
            }

            @Override
            public void unknownMember(ShapeBuilderImpl builder, String memberName) {
                builder.value = new ShapeUnknown(memberName);
            }
        }
    }

    // ----- Tests -----

    @ParameterizedTest
    @MethodSource("providers")
    public void fastPathDiscriminatorFirst(JsonSerdeProvider provider) {
        Shape result = deserialize(provider, "{\"type\":\"circle\",\"radius\":5}");
        assertThat(result, instanceOf(ShapeCircle.class));
        assertThat(((ShapeCircle) result).circle().radius(), equalTo(5));
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void fastPathMultipleVariantFields(JsonSerdeProvider provider) {
        Shape result = deserialize(provider, "{\"type\":\"rectangle\",\"width\":3,\"height\":4}");
        assertThat(result, instanceOf(ShapeRectangle.class));
        assertThat(((ShapeRectangle) result).rectangle().width(), equalTo(3));
        assertThat(((ShapeRectangle) result).rectangle().height(), equalTo(4));
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void fastPathVariantWithNoFields(JsonSerdeProvider provider) {
        // A circle with the radius omitted: the variant reads no fields before the object ends.
        Shape result = deserialize(provider, "{\"type\":\"circle\"}");
        assertThat(result, instanceOf(ShapeCircle.class));
        assertThat(((ShapeCircle) result).circle().radius(), equalTo(0));
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void fallbackDiscriminatorLast(JsonSerdeProvider provider) {
        Shape result = deserialize(provider, "{\"radius\":5,\"type\":\"circle\"}");
        assertThat(result, instanceOf(ShapeCircle.class));
        assertThat(((ShapeCircle) result).circle().radius(), equalTo(5));
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void fallbackDiscriminatorInMiddle(JsonSerdeProvider provider) {
        Shape result = deserialize(provider, "{\"width\":3,\"type\":\"rectangle\",\"height\":4}");
        assertThat(result, instanceOf(ShapeRectangle.class));
        assertThat(((ShapeRectangle) result).rectangle().width(), equalTo(3));
        assertThat(((ShapeRectangle) result).rectangle().height(), equalTo(4));
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void unknownVariantFastPath(JsonSerdeProvider provider) {
        Shape result = deserialize(provider, "{\"type\":\"triangle\",\"base\":2,\"height\":3}");
        assertThat(result, instanceOf(ShapeUnknown.class));
        assertThat(((ShapeUnknown) result).memberName(), equalTo("triangle"));
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void unknownVariantFallback(JsonSerdeProvider provider) {
        Shape result = deserialize(provider, "{\"base\":2,\"type\":\"triangle\"}");
        assertThat(result, instanceOf(ShapeUnknown.class));
        assertThat(((ShapeUnknown) result).memberName(), equalTo("triangle"));
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void roundTripsThroughBothDirections(JsonSerdeProvider provider) {
        for (String json : new String[] {
                "{\"type\":\"circle\",\"radius\":5}",
                "{\"type\":\"rectangle\",\"width\":3,\"height\":4}"
        }) {
            Shape decoded = deserialize(provider, json);
            assertThat(serialize(provider, decoded), equalTo(json));
        }
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void emptyObjectIsAnError(JsonSerdeProvider provider) {
        // An empty object has no discriminator, so no variant can be determined.
        var e = assertThrows(SerializationException.class, () -> deserialize(provider, "{}"));
        assertThat(e.getMessage(), containsString("missing a string discriminator field 'type'"));
        assertThrows(SerializationException.class, () -> deserialize(provider, "{ \n }"));
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void fieldsBeforeTheDiscriminatorAreReadIntoTheVariant(JsonSerdeProvider provider) {
        // Unknown fields (including one holding a nested "type"), a null member and a known member all come
        // before the discriminator; another known member comes after it.
        Shape result = deserialize(
                provider,
                "{\"extra\":{\"a\":[1,{\"b\":null}],\"type\":\"circle\"},\"skip\":null,\"height\":4,"
                        + "\"type\":\"rectangle\",\"width\":3}");
        assertThat(result, instanceOf(ShapeRectangle.class));
        assertThat(((ShapeRectangle) result).rectangle().width(), equalTo(3));
        assertThat(((ShapeRectangle) result).rectangle().height(), equalTo(4));
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void nullDiscriminatorIsAnError(JsonSerdeProvider provider) {
        for (String json : new String[] {
                "{\"type\":null,\"radius\":5}",
                "{\"radius\":5,\"type\":null}"
        }) {
            var e = assertThrows(SerializationException.class, () -> deserialize(provider, json));
            assertThat(json, e.getMessage(), containsString("discriminator"));
        }
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void nonStringDiscriminatorIsAnError(JsonSerdeProvider provider) {
        for (String json : new String[] {
                "{\"type\":1,\"radius\":5}",
                "{\"radius\":5,\"type\":{\"name\":\"circle\"}}"
        }) {
            assertThrows(SerializationException.class, () -> deserialize(provider, json), json);
        }
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void nonObjectIsAnError(JsonSerdeProvider provider) {
        for (String json : new String[] {"\"circle\"", "[]", "5"}) {
            assertThrows(SerializationException.class, () -> deserialize(provider, json), json);
        }
    }

    private static Shape deserialize(JsonSerdeProvider provider, String json) {
        try (JsonCodec codec = codec(provider)) {
            var builder = new ShapeBuilderImpl();
            var de = codec.createDeserializer(json.getBytes(StandardCharsets.UTF_8));
            builder.deserialize(de);
            de.close();
            return builder.build();
        }
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
