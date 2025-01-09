/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.List;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;

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
     * Get the output structure schema.
     *
     * @return Returns the output schema.
     */
    Schema outputSchema();

    /**
     * Get a type registry for the operation used to create errors.
     *
     * @return Returns the type registry of errors.
     */
    TypeRegistry errorRegistry();

    /**
     * Get a list of effective authScheme for the operation.
     *
     * @return List of effective auth schemes
     */
    List<ShapeId> effectiveAuthSchemes();

    /**
     * Get the input schema member that serves as the idempotency token.
     *
     * @return the idempotency token bearing input schema member, or null if no member has a token.
     */
    default Schema idempotencyTokenMember() {
        for (var m : inputSchema().members()) {
            if (m.hasTrait(TraitKey.IDEMPOTENCY_TOKEN)) {
                return m;
            }
        }
        return null;
    }

    /**
     * Get the input schema member that contains an event stream or data stream.
     *
     * @return the input schema member, or null if no member has a stream.
     */
    default Schema inputStreamMember() {
        for (var m : inputSchema().members()) {
            if (m.hasTrait(TraitKey.STREAMING_TRAIT)) {
                return m;
            }
        }
        return null;
    }

    /**
     * Get the output schema member that contains an event stream or data stream.
     *
     * @return the output schema member, or null if no member has a stream.
     */
    default Schema outputStreamMember() {
        for (var m : outputSchema().members()) {
            if (m.hasTrait(TraitKey.STREAMING_TRAIT)) {
                return m;
            }
        }
        return null;
    }

    /**
     * Api Resource that this operation is bound to, if any.
     *
     * <p>Note: Operations can be bound to only a single resource within a service, and may be bound to the service directly.
     *
     * @return Resource the operation is bound to or null if the operation has no parent resource.
     */
    default ApiResource boundResource() {
        return null;
    }
}
