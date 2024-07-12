/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api.scheme;

import java.util.Objects;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;

/**
 * AuthSchemeResolver parameters.
 */
public final class AuthSchemeResolverParams {

    private final String protocolId;
    private final String operationName;
    private final AuthProperties properties;

    private AuthSchemeResolverParams(Builder builder) {
        this.protocolId = Objects.requireNonNull(builder.protocolId, "protocolId is null");
        this.operationName = Objects.requireNonNull(builder.operationName, "operationName is null");
        this.properties = Objects.requireNonNullElseGet(builder.properties, () -> AuthProperties.builder().build());
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
     * Properties available when resolving the auth schemes.
     *
     * @return properties.
     */
    public AuthProperties properties() {
        return properties;
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
            && Objects.equals(properties, params.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocolId, operationName, properties);
    }

    /**
     * Builder used to create {@link AuthSchemeResolverParams}.
     */
    public static final class Builder {

        private String protocolId;
        private String operationName;
        private AuthProperties properties;

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
         * Set the auth properties.
         *
         * @param properties AuthProperties to set.
         * @return the builder.
         */
        public Builder properties(AuthProperties properties) {
            this.properties = properties;
            return this;
        }
    }
}
