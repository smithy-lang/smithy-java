/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoint.api;

import java.util.Objects;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;

/**
 * Encapsulates endpoint resolver parameters.
 */
public final class EndpointResolverParams {

    private final Context context;
    private final ApiOperation<?, ?> operation;
    private final SerializableStruct inputShape;

    private EndpointResolverParams(Builder builder) {
        this.operation = Objects.requireNonNull(builder.operation, "operation is null");
        this.inputShape = Objects.requireNonNull(builder.inputShape, "inputShape is null");
        this.context = Objects.requireNonNullElseGet(builder.context, Context::create);
    }

    /**
     * Create a new builder to build {@link EndpointResolverParams}.
     *
     * @return the builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the model for the operation to resolve the endpoint for.
     *
     * @return the operation.
     */
    public ApiOperation<?, ?> operation() {
        return operation;
    }

    /**
     * Context available when resolving the endpoint.
     *
     * @return context.
     */
    public Context context() {
        return context;
    }

    /**
     * Input Shape for the client call the endpoint is being resolved for.
     *
     * @return input.
     */
    public SerializableStruct inputShape() {
        return inputShape;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EndpointResolverParams params = (EndpointResolverParams) o;
        return Objects.equals(operation, params.operation)
            && Objects.equals(inputShape, params.inputShape)
            && Objects.equals(context, params.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operation, inputShape, context);
    }

    /**
     * Builder used to create {@link EndpointResolverParams}.
     */
    public static final class Builder {

        private Context context;
        private ApiOperation<?, ?> operation;
        private SerializableStruct inputShape;

        private Builder() {
        }

        /**
         * Build the params.
         * @return the built params.
         */
        public EndpointResolverParams build() {
            return new EndpointResolverParams(this);
        }

        /**
         * Set the operation to resolve endpoint for.
         *
         * @param operation the operation.
         * @return the builder.
         */
        public Builder operation(ApiOperation<?, ?> operation) {
            this.operation = operation;
            return this;
        }

        /**
         * Set the client's context.
         *
         * @param context Context to set.
         * @return the builder.
         */
        public Builder context(Context context) {
            this.context = context;
            return this;
        }

        /**
         * Set the input shape used by the operation that the endpoint is being resolved for.
         *
         * @param inputShape input shape to set.
         * @return the builder.
         */
        public Builder inputShape(SerializableStruct inputShape) {
            this.inputShape = inputShape;
            return this;
        }
    }
}
