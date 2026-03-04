/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.function.Supplier;

/**
 * Represents a modeled Smithy operation.
 *
 * @param <I>  Operation input shape type.
 * @param <O>  Operation output shape type.
 * @param <IE> Operation input event shape type.
 */
public interface InputEventStreamingApiOperation<I extends SerializableStruct, O extends SerializableStruct,
        IE extends SerializableStruct>
        extends ApiOperation<I, O> {
    /**
     * Retrieves a supplier of builders for input events.
     *
     * @return Returns a supplier of input event shape builders.
     */
    Supplier<ShapeBuilder<IE>> inputEventBuilderSupplier();

    /**
     * Returns the schema of the streaming member.
     *
     * @return the schema of the streaming member
     */
    default Schema inputEventStreamMember() {
        var schema = inputSchema();
        for (var member : schema.members()) {
            if (member.isMember() && member.memberTarget().hasTrait(TraitKey.STREAMING_TRAIT)) {
                return member;
            }
        }
        throw new IllegalArgumentException("No streaming member found");

    }
}
