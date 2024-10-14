/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;

/**
 * Builds deserializable shapes.
 *
 * @param <T> Shape to build.
 */
public interface ShapeBuilder<T extends SerializableShape> {
    /**
     * Build the shape.
     *
     * @return the built shape.
     */
    T build();

    /**
     * Deserializes data from the given decoder into the state of the builder.
     *
     * @param decoder Decoder used to deserialize the shape.
     */
    ShapeBuilder<T> deserialize(ShapeDeserializer decoder);

    /**
     * Performs any necessary error correction before the shape can be built.
     *
     * <p>For example, a shape might be missing a required number member, and error correction would set the
     * member to zero to allow the shape to build.
     *
     * @return Returns the builder.
     * @see <a href="https://smithy.io/2.0/spec/aggregate-types.html#client-error-correction">Client Error Correction</a>
     */
    default ShapeBuilder<T> errorCorrection() {
        return this;
    }

    /**
     * Get the schema definition of the shape this builder creates.
     *
     * @return the schema of the shape.
     */
    Schema schema();
}
