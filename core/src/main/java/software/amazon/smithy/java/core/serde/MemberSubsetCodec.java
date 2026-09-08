/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import java.nio.ByteBuffer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.utils.SmithyInternalApi;

/** Optional {@link Codec} capability for coding selected structure members. */
@SmithyInternalApi
public interface MemberSubsetCodec {
    /**
     * Deserializes a complete structure directly into {@code builder}.
     *
     * @return true if consumed; false must leave {@code source} reusable.
     */
    default boolean deserialize(
            Schema schema,
            ShapeBuilder<?> builder,
            ByteBuffer source
    ) {
        return false;
    }

    /** Returns selected members serialized, or null when unsupported. */
    ByteBuffer serialize(SerializableStruct struct, MemberSubset subset);

    /**
     * Deserializes only the members {@code subset} includes directly into {@code builder}.
     *
     * @return true if consumed; false must leave {@code source} reusable.
     */
    default boolean deserialize(
            Schema schema,
            ShapeBuilder<?> builder,
            ByteBuffer source,
            MemberSubset subset
    ) {
        return false;
    }

    /** Stable, reusable selector for structure members. */
    @FunctionalInterface
    interface MemberSubset {
        boolean includes(Schema struct, Schema member);
    }
}
