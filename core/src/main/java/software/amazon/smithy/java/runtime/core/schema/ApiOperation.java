/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.List;
import java.util.Set;
import software.amazon.smithy.java.runtime.core.serde.TypeRegistry;
import software.amazon.smithy.java.runtime.retries.api.RetrySafety;
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
     * Get a type registry for the operation used to create errors and output types.
     *
     * @return Returns the type registry.
     */
    TypeRegistry typeRegistry();

    /**
     * Get a list of effective authScheme for the operation.
     *
     * @return List of effective auth schemes
     */
    List<ShapeId> effectiveAuthSchemes();

    /**
     * Return a Set of {@link Schema} representing errors that are allowed to be returned from this Operation.
     *
     * @return Set of {@link Schema} for the errors throwable by this operation.
     */
    Set<Schema> errorSchemas();

    /**
     * A helper method to mutate the retry information of an exception based on information from the model.
     *
     * <p>The following logic is applied:
     * <ul>
     *     <li>If the operation is modeled as readonly or idempotent, then it is marked safe to retry.</li>
     *     <li>If the exception is modeled as retryable, then it is marked safe to retry.</li>
     *     <li>If the exception is modeled as retryable and is a throttling error, then it's marked as throttling.</li>
     * </ul>
     *
     * @param operationSchema Schema of the operation to check.
     * @param e Exception to mutate.
     */
    static void applyRetryInfoFromModel(Schema operationSchema, ApiException e) {
        // If the operation is readonly or idempotent, then it's safe to retry (assuming protocols don't disqualify it).
        var isRetryable = operationSchema.hasTrait(TraitKey.READ_ONLY_TRAIT)
            || operationSchema.hasTrait(TraitKey.IDEMPOTENT_TRAIT);

        if (isRetryable) {
            e.isRetrySafe(RetrySafety.YES);
        }

        // If the exception is modeled as retryable or a throttle, then use that information.
        if (e instanceof ModeledApiException mae) {
            var retryTrait = mae.schema().getTrait(TraitKey.RETRYABLE_TRAIT);
            if (retryTrait != null) {
                e.isRetrySafe(RetrySafety.YES);
                e.isThrottle(retryTrait.getThrottling());
            }
        }
    }
}
