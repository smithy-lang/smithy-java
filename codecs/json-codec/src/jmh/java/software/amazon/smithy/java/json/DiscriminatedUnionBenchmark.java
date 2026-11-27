/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.json.jackson.JacksonJsonSerdeProvider;
import software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.DiscriminatedTrait;

/**
 * Microbenchmark for the {@code @discriminated} union implementation (both encode and decode), using the
 * actual {@link DiscriminatedTrait} on the runtime schema.
 *
 * <p>The benchmark compares, for both JSON backends:
 * <ul>
 *     <li>{@link #encodeDiscriminated} vs {@link #encodePlainUnion}: the flat discriminated encoding
 *     against the standard externally tagged union encoding of the same logical value.</li>
 *     <li>{@link #decodeDiscriminated}: decoding the flat encoding, with {@code discriminatorLast}
 *     selecting the native fast path (discriminator first) or the document fallback (discriminator
 *     last). On the Jackson backend both positions use the uniform document strategy.</li>
 *     <li>{@link #decodePlainUnion}: decoding the standard externally tagged encoding of the same
 *     value, as a baseline for what a non-discriminated union costs.</li>
 * </ul>
 *
 * <p>Shapes (a discriminated and an equivalent plain union over the same variant structs):
 * <pre>
 * {@literal @}discriminated("type")
 * union Shape      { circle: CircleData, rectangle: RectangleData }
 * union PlainShape { circle: CircleData, rectangle: RectangleData }
 * structure CircleData    { radius: Integer }
 * structure RectangleData { width: Integer, height: Integer }
 * </pre>
 * The {@code rectangle} variant (two fields) is used so both the discriminator and sibling fields
 * are exercised.
 */
@State(Scope.Benchmark)
public class DiscriminatedUnionBenchmark {

    private static final ShapeId SHAPE_ID = ShapeId.from("smithy.benchmark#Shape");
    private static final ShapeId PLAIN_SHAPE_ID = ShapeId.from("smithy.benchmark#PlainShape");
    private static final ShapeId CIRCLE_ID = ShapeId.from("smithy.benchmark#CircleData");
    private static final ShapeId RECTANGLE_ID = ShapeId.from("smithy.benchmark#RectangleData");

    private static final Schema CIRCLE_SCHEMA;
    private static final Schema RECTANGLE_SCHEMA;
    private static final Schema RECTANGLE_WIDTH_MEMBER;
    private static final Schema RECTANGLE_HEIGHT_MEMBER;

    private static final Schema SHAPE_SCHEMA;
    private static final Schema SHAPE_RECTANGLE_MEMBER;
    private static final Schema PLAIN_SHAPE_SCHEMA;
    private static final Schema PLAIN_SHAPE_RECTANGLE_MEMBER;

    static {
        var circleBuilder = Schema.structureBuilder(CIRCLE_ID);
        circleBuilder.putMember("radius", Schema.createInteger(CIRCLE_ID.withMember("radius")));
        var rectangleBuilder = Schema.structureBuilder(RECTANGLE_ID);
        rectangleBuilder.putMember("width", Schema.createInteger(RECTANGLE_ID.withMember("width")));
        rectangleBuilder.putMember("height", Schema.createInteger(RECTANGLE_ID.withMember("height")));

        var shapeBuilder = Schema.unionBuilder(SHAPE_ID, new DiscriminatedTrait("type"));
        shapeBuilder.putMember("circle", circleBuilder);
        shapeBuilder.putMember("rectangle", rectangleBuilder);

        var plainShapeBuilder = Schema.unionBuilder(PLAIN_SHAPE_ID);
        plainShapeBuilder.putMember("circle", circleBuilder);
        plainShapeBuilder.putMember("rectangle", rectangleBuilder);

        CIRCLE_SCHEMA = circleBuilder.build();
        RECTANGLE_SCHEMA = rectangleBuilder.build();
        RECTANGLE_WIDTH_MEMBER = RECTANGLE_SCHEMA.member("width");
        RECTANGLE_HEIGHT_MEMBER = RECTANGLE_SCHEMA.member("height");

        SHAPE_SCHEMA = shapeBuilder.build();
        SHAPE_RECTANGLE_MEMBER = SHAPE_SCHEMA.member("rectangle");
        PLAIN_SHAPE_SCHEMA = plainShapeBuilder.build();
        PLAIN_SHAPE_RECTANGLE_MEMBER = PLAIN_SHAPE_SCHEMA.member("rectangle");
    }

    @Param({"false", "true"})
    public boolean discriminatorLast;

    @Param({"smithy", "jackson"})
    public String backend;

    private JsonCodec codec;
    private SerializableStruct discriminatedValue;
    private SerializableStruct plainValue;
    private byte[] discriminatedPayload;
    private byte[] plainPayload;

    @Setup
    public void setup() {
        codec = JsonCodec.builder()
                .overrideSerdeProvider(
                        "jackson".equals(backend) ? new JacksonJsonSerdeProvider() : new SmithyJsonSerdeProvider())
                .build();

        discriminatedValue = new UnionRectangle(SHAPE_SCHEMA, SHAPE_RECTANGLE_MEMBER, new Rectangle(3, 4));
        plainValue = new UnionRectangle(PLAIN_SHAPE_SCHEMA, PLAIN_SHAPE_RECTANGLE_MEMBER, new Rectangle(3, 4));

        // Discriminator-first is what the serializer emits; build the discriminator-last variant by
        // hand to exercise the native fallback path.
        discriminatedPayload = (discriminatorLast
                ? "{\"width\":3,\"height\":4,\"type\":\"rectangle\"}"
                : "{\"type\":\"rectangle\",\"width\":3,\"height\":4}").getBytes(StandardCharsets.UTF_8);
        plainPayload = "{\"rectangle\":{\"width\":3,\"height\":4}}".getBytes(StandardCharsets.UTF_8);
    }

    @Benchmark
    public void encodeDiscriminated(Blackhole bh) {
        bh.consume(encode(discriminatedValue));
    }

    @Benchmark
    public void encodePlainUnion(Blackhole bh) {
        bh.consume(encode(plainValue));
    }

    @Benchmark
    public void decodeDiscriminated(Blackhole bh) {
        var builder = new UnionBuilder(SHAPE_SCHEMA);
        var de = codec.createDeserializer(discriminatedPayload);
        builder.deserialize(de);
        de.close();
        bh.consume(builder.build());
    }

    @Benchmark
    public void decodePlainUnion(Blackhole bh) {
        var builder = new UnionBuilder(PLAIN_SHAPE_SCHEMA);
        var de = codec.createDeserializer(plainPayload);
        builder.deserialize(de);
        de.close();
        bh.consume(builder.build());
    }

    private byte[] encode(SerializableStruct value) {
        try (var out = new ByteArrayOutputStream(); var serializer = codec.createSerializer(out)) {
            value.serialize(serializer);
            serializer.flush();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ----- Value / builder types mirroring generated code -----

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

    /** A union value with its {@code rectangle} member set; schema() returns the (plain or discriminated) union. */
    private record UnionRectangle(Schema unionSchema, Schema rectangleMember, Rectangle rectangle)
            implements SerializableStruct {
        @Override
        public Schema schema() {
            return unionSchema;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct(rectangleMember, rectangle);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
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

    private static final class UnionBuilder implements ShapeBuilder<SerializableStruct> {
        private final Schema unionSchema;
        private final Schema rectangleMember;
        private SerializableStruct value;

        UnionBuilder(Schema unionSchema) {
            this.unionSchema = unionSchema;
            this.rectangleMember = unionSchema.member("rectangle");
        }

        @Override
        public Schema schema() {
            return unionSchema;
        }

        @Override
        public SerializableStruct build() {
            return value;
        }

        @Override
        public ShapeBuilder<SerializableStruct> deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(unionSchema, this, (state, member, de) -> {
                // This benchmark only exercises the rectangle variant.
                if ("rectangle".equals(member.memberName())) {
                    state.value = new UnionRectangle(
                            state.unionSchema,
                            state.rectangleMember,
                            new RectangleBuilder().deserializeMember(de, member).build());
                }
            });
            return this;
        }
    }
}
