/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.function.Supplier;

/**
 * Represents a modeled Smithy operation.
 *
 * @param <I> Operation input shape type.
 * @param <O> Operation output shape type.
 * @param <IE> Operation input event shape type.
 */
public interface InputEventStreamingApiOperation<I extends SerializableStruct, O extends SerializableStruct, IE extends SerializableStruct>
    extends ApiOperation<I, O> {
    /**
     * Retrieves a supplier of builders for input events.
     *
     * @return Returns a supplier of input event shape builders.
     */
    Supplier<ShapeBuilder<IE>> inputEventBuilderSupplier();

    /**
     * Get the schema of the input events.
     *
     * @return Returns the input event schema, including relevant traits.
     */
    Schema inputEventSchema();
}
