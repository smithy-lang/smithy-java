/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.io.ByteBufferOutputStream;

/**
 * Generic shape serialization and deserialization.
 */
public interface Codec extends AutoCloseable {

    @Override
    default void close() {}

    /**
     * Returns the default media type used by this codec.
     *
     * @return Returns the default media type.
     */
    String getMediaType();

    /**
     * Create a serializer from this Codec that serializes a shape into the sink.
     *
     * @param sink Where to serialize a shape.
     * @return Returns the created serializer.
     */
    ShapeSerializer createSerializer(OutputStream sink);

    /**
     * Serialize the given shape into a ByteBuffer.
     *
     * <p>By default, this method is a convenience method for the serialization idiom:
     * <pre>{@code
     * var outputStream = new ByteArrayOutputStream();
     * try (var serializer = codec.createSerializer(outputStream)) {
     *     shape.serialize(serializer);
     *     serializer.flush();
     * }
     * return ByteBuffer.wrap(outputStream.toByteArray());
     * }</pre>
     *
     * However, individual Codec implementations may provide versions that are more efficient than
     * their non-streaming counterparts.
     *
     * <p>The returned buffer may or may not {@linkplain ByteBuffer#hasArray() have an accessible backing array} and,
     * if it does, may not {@linkplain ByteBuffer#arrayOffset() start at offset 0}. Always use this idiom for
     * interacting with heap-based ByteBuffers:
     *
     * <pre>{@code
     * if (buffer.hasArray()) {
     *     int pos = buffer.arrayOffset() + buffer.position();
     *     int len = buffer.remaining();
     *     doSomethingWithBuffer(buffer.array(), pos, len);
     * } else {
     *     // use ByteBuffer retrieval methods
     * }
     * }</pre>
     *
     * @param shape the shape to serialize
     * @return A ByteBuffer containing the serialized shape
     */
    default ByteBuffer serialize(SerializableShape shape) {
        ByteBufferOutputStream baos = new ByteBufferOutputStream();
        try (var serializer = createSerializer(baos)) {
            shape.serialize(serializer);
            serializer.flush();
        }
        return baos.toByteBuffer();
    }

    /**
     * Create a deserializer from this Codec that deserializes a shape from the source.
     *
     * @param source Source to parse.
     * @return Returns the created deserializer.
     */
    ShapeDeserializer createDeserializer(byte[] source);

    /**
     * Create a deserializer from this Codec that deserializes a shape from the source.
     *
     * @param source Source to parse.
     * @return Returns the created deserializer.
     */
    ShapeDeserializer createDeserializer(ByteBuffer source);

    /**
     * Helper method to serialize a shape a string.
     *
     * <p>This should only be used with protocols that support UTF-8 encodings.
     *
     * @param shape      Shape to serialize.
     * @return Returns the serialized string.
     */
    default String serializeToString(SerializableShape shape) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ShapeSerializer serializer = createSerializer(stream);
        shape.serialize(serializer);
        serializer.flush();
        return stream.toString(StandardCharsets.UTF_8);
    }

    /**
     * Helper method to deserialize and build a shape.
     *
     * @param source  What to parse.
     * @param builder Builder to populate.
     * @return Returns the built and error-corrected shape.
     * @param <T> Shape to build.
     */
    default <T extends SerializableShape> T deserializeShape(byte[] source, ShapeBuilder<T> builder) {
        return builder.deserialize(createDeserializer(source)).errorCorrection().build();
    }

    /**
     * Helper method to deserialize and build a shape.
     *
     * @param source  What to parse.
     * @param builder Builder to populate.
     * @return Returns the built and error-corrected shape.
     * @param <T> Shape to build.
     */
    default <T extends SerializableShape> T deserializeShape(ByteBuffer source, ShapeBuilder<T> builder) {
        return builder.deserialize(createDeserializer(source)).errorCorrection().build();
    }

    /**
     * Helper method to deserialize and build a shape using a String as input.
     *
     * @param source  What to parse.
     * @param builder Builder to populate.
     * @return Returns the built and error-corrected shape.
     * @param <T> Shape to build.
     */
    default <T extends SerializableShape> T deserializeShape(String source, ShapeBuilder<T> builder) {
        return deserializeShape(source.getBytes(StandardCharsets.UTF_8), builder);
    }
}
