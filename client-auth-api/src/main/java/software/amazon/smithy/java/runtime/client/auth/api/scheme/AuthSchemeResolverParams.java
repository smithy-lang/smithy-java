/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.auth.api.scheme;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * AuthSchemeResolver parameters.
 */
public final class AuthSchemeResolverParams {

    private final String protocolId;
    private final String operationName;
    private final List<ShapeId> operationAuthSchemes;
    private final Context context;

    private AuthSchemeResolverParams(Builder builder) {
        this.protocolId = Objects.requireNonNull(builder.protocolId, "protocolId is null");
        this.operationName = Objects.requireNonNull(builder.operationName, "operationName is null");
        this.context = Objects.requireNonNullElseGet(builder.context, Context::create);
        this.operationAuthSchemes = Objects.requireNonNullElse(builder.operationAuthSchemes, Collections.emptyList());
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
     * Protocol ID used the caller.
     *
     * @return the protocol ID.
     */
    public String protocolId() {
        return protocolId;
    }

    /**
     * Get the name of the operation to resolve auth schemes for.
     *
     * @return the operation name.
     */
    public String operationName() {
        return operationName;
    }

    /**
     * List of effective authSchemes for the operation being called.
     *
     * @return list of authScheme id's
     */
    public List<ShapeId> operationAuthSchemes() {
        return operationAuthSchemes;
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
            && Objects.equals(operationName, params.operationName)
            && Objects.equals(context, params.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocolId, operationName, context);
    }

    /**
     * Builder used to create {@link AuthSchemeResolverParams}.
     */
    public static final class Builder {

        private String protocolId;
        private String operationName;
        public List<ShapeId> operationAuthSchemes;
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
         * Set the name of the operation.
         *
         * @param operationName Name of the operation.
         * @return the builder.
         */
        public Builder operationName(String operationName) {
            this.operationName = operationName;
            return this;
        }

        /**
         * Set the effective auth scheme list for the operation.
         *
         * @param operationAuthSchemes list of auth scheme ids.
         * @return the builder.
         */
        public Builder operationAuthSchemes(List<ShapeId> operationAuthSchemes) {
            this.operationAuthSchemes = operationAuthSchemes;
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
