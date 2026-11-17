/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Failures raised by shape builders while deserializing (e.g. a union builder rejecting a payload with no member or
 * more than one member set) must surface from {@link Codec#deserializeShape} as {@link SerializationException}.
 */
public class CodecDeserializeShapeTest {

    private static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#Shape"))
            .putMember("value", PreludeSchemas.STRING)
            .build();

    private static final Codec CODEC = new Codec() {
        @Override
        public ShapeSerializer createSerializer(OutputStream sink) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShapeDeserializer createDeserializer(ByteBuffer source) {
            return new SpecificShapeDeserializer() {
                @Override
                public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
                    consumer.accept(state, schema.member("value"), this);
                }
            };
        }
    };

    static List<Arguments> overloads() {
        return List.of(
                Arguments.of("byte[]",
                        (BiConsumer<Codec, ShapeBuilder<?>>) (c, b) -> c.deserializeShape(new byte[0], b)),
                Arguments.of("ByteBuffer",
                        (BiConsumer<Codec, ShapeBuilder<?>>) (c, b) -> c.deserializeShape(ByteBuffer.allocate(0), b)),
                Arguments.of("String", (BiConsumer<Codec, ShapeBuilder<?>>) (c, b) -> c.deserializeShape("", b)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("overloads")
    void wrapsFailureWhileDeserializing(String name, BiConsumer<Codec, ShapeBuilder<?>> deserializeShape) {
        var builder = new FailingBuilder(new IllegalArgumentException("Only one value may be set for unions"), null);

        var e = assertThrows(SerializationException.class, () -> deserializeShape.accept(CODEC, builder));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("overloads")
    void wrapsFailureWhileBuilding(String name, BiConsumer<Codec, ShapeBuilder<?>> deserializeShape) {
        var builder = new FailingBuilder(null, new NullPointerException("no union value set"));

        var e = assertThrows(SerializationException.class, () -> deserializeShape.accept(CODEC, builder));
        assertInstanceOf(NullPointerException.class, e.getCause());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("overloads")
    void doesNotRewrapSerializationException(String name, BiConsumer<Codec, ShapeBuilder<?>> deserializeShape) {
        var original = new SerializationException("bad payload");
        var builder = new FailingBuilder(original, null);

        var e = assertThrows(SerializationException.class, () -> deserializeShape.accept(CODEC, builder));
        assertSame(original, e);
    }

    @Test
    void wrapReturnsSerializationExceptionUnchanged() {
        var original = new SerializationException("bad payload");

        assertSame(original, SerializationException.wrap(original));
    }

    @Test
    void wrapWrapsOtherExceptions() {
        var cause = new IllegalStateException("boom");

        assertSame(cause, SerializationException.wrap(cause).getCause());
    }

    private static final class FailingBuilder implements ShapeBuilder<SerializableShape> {
        private final RuntimeException onMember;
        private final RuntimeException onBuild;

        private FailingBuilder(RuntimeException onMember, RuntimeException onBuild) {
            this.onMember = onMember;
            this.onBuild = onBuild;
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public ShapeBuilder<SerializableShape> deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, this, (builder, member, de) -> {
                if (builder.onMember != null) {
                    throw builder.onMember;
                }
            });
            return this;
        }

        @Override
        public SerializableShape build() {
            if (onBuild != null) {
                throw onBuild;
            }
            return serializer -> {};
        }
    }
}
