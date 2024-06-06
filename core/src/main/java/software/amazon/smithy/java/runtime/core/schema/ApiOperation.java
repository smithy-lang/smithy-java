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
public interface ApiOperation<I extends SerializableStruct, O extends SerializableStruct> {
    /**
     * Create a builder used to create the input of the operation.
     *
     * @return Returns the input builder.
     */
    ShapeBuilder<I> inputBuilder();

    /**
     * Create a builder used to create the output of the operation.
     *
     * @return Returns the operation output builder.
     */
    ShapeBuilder<O> outputBuilder();

    /**
     * Get the schema of the operation.
     *
     * @return Returns the operation schema, including relevant traits.
     */
    Schema schema();

    /**
     * Get the input structure schema.
     *
     * @return Returns the input schema.
     */
    Schema inputSchema();

    /**
     * @return true if the input has a payload or event stream.
     */
    boolean streamingInput();

    /**
     * Get the output structure schema.
     *
     * @return Returns the output schema.
     */
    Schema outputSchema();

    /**
     * true if the output has a payload or event stream.
     */
    boolean streamingOutput();

    /**
     * Get a type registry for the operation used to create errors and output types.
     *
     * @return Returns the type registry.
     */
    TypeRegistry typeRegistry();
}
