/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;

/**
 * SDK Shape that can be serialized by a {@link ShapeSerializer}.
 */
@FunctionalInterface
public interface SerializableShape {
    /**
     * Serialize the state of the shape into the given serializer.
     *
     * @param encoder Where to serialize the shape.
     */
    void serialize(ShapeSerializer encoder);
}
