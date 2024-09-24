/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoint.api;

import java.util.Objects;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Encapsulates endpoint resolver parameters.
 */
public final class EndpointResolverParams {

    private final Context context;
    private final Schema operationSchema;
    private final SerializableStruct inputShape;

    private EndpointResolverParams(Builder builder) {
        this.operationSchema = Objects.requireNonNull(builder.operationSchema, "operationSchema is null");
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
     * Get the schema of the operation to resolve the endpoint for.
     *
     * @return the operation Schema.
     */
    public Schema operationSchema() {
        return operationSchema;
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
     * TODO: Docs
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
        return Objects.equals(operationSchema, params.operationSchema)
            && Objects.equals(inputShape, params.inputShape)
            && Objects.equals(context, params.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationSchema, inputShape, context);
    }

    /**
     * Builder used to create {@link EndpointResolverParams}.
     */
    public static final class Builder {

        private Context context;
        private Schema operationSchema;
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
         * Set the schema for the operation to resolve endpoint for.
         *
         * @param operationSchema the operation schema.
         * @return the builder.
         */
        public Builder operationSchema(Schema operationSchema) {
            if (!operationSchema.type().equals(ShapeType.OPERATION)) {
                throw new IllegalArgumentException("Operation schema must be an operation");
            }
            this.operationSchema = operationSchema;
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
         * TODO: Docs
         * @param inputShape
         * @return
         */
        public Builder inputShape(SerializableStruct inputShape) {
            this.inputShape = inputShape;
            return this;
        }
    }
}
