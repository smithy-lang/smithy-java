/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import software.amazon.smithy.java.runtime.core.schema.SerializableShape;

/**
 * Wraps an input or output shape that can contain a stream of data.
 *
 * @param <ShapeT> Input or output shape.
 * @param <StreamT> Stream result type.
 */
public interface StreamingShape<ShapeT extends SerializableShape, StreamT> {

    /**
     * Input or output shape.
     *
     * @return the shape.
     */
    ShapeT shape();

    /**
     * Stream result value.
     *
     * @return the stream result value.
     */
    StreamT value();

    /**
     * Create a new StreamingShape.
     *
     * @param shape  Shape to wrap.
     * @param stream Stream result value.
     * @return the created streaming shape.
     * @param <ShapeT> Shape type.
     * @param <StreamT> Stream result type.
     */
    static <ShapeT extends SerializableShape, StreamT> StreamingShape<ShapeT, StreamT> of(
        ShapeT shape,
        StreamT stream
    ) {
        return new StreamingShape<>() {
            @Override
            public ShapeT shape() {
                return shape;
            }

            @Override
            public StreamT value() {
                return stream;
            }
        };
    }
}
