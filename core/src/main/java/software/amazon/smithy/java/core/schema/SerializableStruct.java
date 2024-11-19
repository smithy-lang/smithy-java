/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import software.amazon.smithy.java.core.serde.ShapeSerializer;

/**
 * A structure or union shape.
 */
public interface SerializableStruct extends SerializableShape {
    /**
     * Get the schema of the shape.
     *
     * @return the schema of the shape.
     */
    Schema schema();

    @Override
    default void serialize(ShapeSerializer encoder) {
        encoder.writeStruct(schema(), this);
    }

    /**
     * Serializes the members of the structure or union.
     *
     * @param serializer Serializer to write to.
     */
    void serializeMembers(ShapeSerializer serializer);

    /**
     * Get the value of a member.
     *
     * @param member Member to get the value of.
     * @return the value of the member, or null.
     * @throws IllegalArgumentException if the provided schema is not a member of the shape.
     */
    Object getMemberValue(Schema member);
}
