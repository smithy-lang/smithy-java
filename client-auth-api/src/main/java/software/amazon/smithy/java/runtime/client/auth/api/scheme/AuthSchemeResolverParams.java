/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.auth.api.scheme;

import java.util.Objects;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;

/**
 * AuthSchemeResolver parameters.
 */
public final class AuthSchemeResolverParams {

    private final String protocolId;
    private final ApiOperation<?, ?> operation;
    private final Context context;

    private AuthSchemeResolverParams(Builder builder) {
        this.protocolId = Objects.requireNonNull(builder.protocolId, "protocolId is null");
        this.operation = Objects.requireNonNull(builder.operation, "operationName is null");
        this.context = Objects.requireNonNullElseGet(builder.context, Context::create);
    }

    /**
     * Create a new builder to build {@link AuthSchemeResolverParams}.
     *
     * @return the builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Protocol ID used by the caller.
     *
     * @return the protocol ID.
     */
    public String protocolId() {
        return protocolId;
    }

    /**
     * Get model for the operation to resolve auth schemes for.
     *
     * @return the operation.
     */
    public ApiOperation<?, ?> operation() {
        return operation;
    }

    /**
     * Context available when resolving the auth schemes.
     *
     * @return context.
     */
    public Context context() {
        return context;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AuthSchemeResolverParams params = (AuthSchemeResolverParams) o;
        return Objects.equals(protocolId, params.protocolId)
            && Objects.equals(operation, params.operation)
            && Objects.equals(context, params.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocolId, operation, context);
    }

    /**
     * Builder used to create {@link AuthSchemeResolverParams}.
     */
    public static final class Builder {

        private String protocolId;
        private ApiOperation<?, ?> operation;
        private Context context;

        private Builder() {
        }

        /**
         * Build the params.
         * @return the built params.
         */
        public AuthSchemeResolverParams build() {
            return new AuthSchemeResolverParams(this);
        }

        /**
         * Set the protocol ID.
         *
         * @param protocolId The protocol ID.
         * @return the builder.
         */
        public Builder protocolId(String protocolId) {
            this.protocolId = protocolId;
            return this;
        }

        /**
         * Set the operation.
         *
         * @param operation operation.
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
    }
}
