/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.utils.SmithyBuilder;

/**
 * Builds deserializable shapes.
 *
 * @param <T> Shape to build.
 */
public interface SdkShapeBuilder<T extends SerializableShape> extends SmithyBuilder<T> {
    /**
     * Set a stream of data on the shape, if allowed.
     *
     * @param stream Stream to set.
     * @throws UnsupportedOperationException if the shape has no stream.
     */
    default void setDataStream(DataStream stream) {
        throw new UnsupportedOperationException("This shape does not have a stream: " + getClass().getName());
    }

    /**
     * Set an event stream on the shape, if allowed.
     *
     * <p>TODO: Implement event streams.
     *
     * @param eventStream Event stream to set.
     * @throws UnsupportedOperationException if the shape has not event stream.
     */
    default void setEventStream(Object eventStream) {
        throw new UnsupportedOperationException("This shape does not have an event stream: " + getClass().getName());
    }

    /**
     * Deserializes data from the given decoder into the state of the builder.
     *
     * @param decoder Decoder used to deserialize the shape.
     */
    SdkShapeBuilder<T> deserialize(ShapeDeserializer decoder);

    /**
     * Performs any necessary error correction before the shape can be built.
     *
     * <p>For example, a shape might be missing a required number member, and error correction would set the
     * member to zero to allow the shape to build.
     *
     * @return Returns the builder.
     */
    default SdkShapeBuilder<T> errorCorrection() {
        return this;
    }
}
