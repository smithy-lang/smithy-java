/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.function.Supplier;

/**
 * Represents a modeled Smithy operation.
 *
 * @param <I> Operation input shape type.
 * @param <O> Operation output shape type.
 * @param <OE> Operation output event shape type.
 */
public interface OutputEventStreamingApiOperation<I extends SerializableStruct, O extends SerializableStruct,
        OE extends SerializableStruct>
        extends ApiOperation<I, O> {
    /**
     * Retrieves a supplier of builders for output events.
     *
     * @return Returns a supplier of output event shape builders.
     */
    Supplier<ShapeBuilder<OE>> outputEventBuilderSupplier();
}
