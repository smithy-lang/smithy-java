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
     * Sentinel returned by {@link #presenceBits()} when member presence is not known upfront.
     *
     * <p>Implementations never set bit 63 (overrides are only generated for shapes with at most 63
     * members), so any negative value is a reliable "unknown" marker.
     */
    long PRESENCE_UNKNOWN = Long.MIN_VALUE;

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
    <T> T getMemberValue(Schema member);

    /**
     * Returns which members {@link #serializeMembers} will emit, as a bitfield where bit i corresponds to
     * the member with memberIndex i, or {@link #PRESENCE_UNKNOWN} when presence is not known upfront.
     *
     * <p>Serializers of size-prefixed binary formats use this to write member presence bitsets before the
     * member values arrive. A non-negative return value is a contract: exactly the members whose bits are
     * set must subsequently be written by {@link #serializeMembers}. Codegen overrides this on generated
     * structures with at most 63 members; hand-written implementations may leave the default.
     *
     * @return presence bitfield keyed by memberIndex, or {@link #PRESENCE_UNKNOWN}.
     */
    default long presenceBits() {
        return PRESENCE_UNKNOWN;
    }
}
