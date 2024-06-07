/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

/**
 * Represents a modeled Smithy operation.
 *
 * @param <I> Operation input shape type.
 * @param <O> Operation output shape type.
 */
public interface SdkOperation<I extends SerializableStruct, O extends SerializableStruct> {
    /**
     * Create a builder used to create the input of the operation.
     *
     * @return Returns the input builder.
     */
    SdkShapeBuilder<I> inputBuilder();

    /**
     * Create a builder used to create the output of the operation.
     *
     * @return Returns the operation output builder.
     */
    SdkShapeBuilder<O> outputBuilder();

    /**
     * Get the schema of the operation.
     *
     * @return Returns the operation schema, including relevant traits.
     */
    SdkSchema schema();

    /**
     * Get the input structure schema.
     *
     * @return Returns the input schema.
     */
    SdkSchema inputSchema();

    /**
     * Get the output structure schema.
     *
     * @return Returns the output schema.
     */
    SdkSchema outputSchema();

    /**
     * Get a type registry for the operation used to create errors and output types.
     *
     * @return Returns the type registry.
     */
    TypeRegistry typeRegistry();
}
