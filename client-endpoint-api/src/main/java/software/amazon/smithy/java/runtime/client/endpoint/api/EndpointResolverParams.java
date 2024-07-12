/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoint.api;

import java.util.Objects;

/**
 * Encapsulates endpoint resolver parameters.
 */
public final class EndpointResolverParams {

    private final String operationName;
    private final EndpointProperties properties;

    private EndpointResolverParams(Builder builder) {
        this.operationName = Objects.requireNonNull(builder.operationName, "operationName is null");
        this.properties = Objects.requireNonNullElseGet(builder.properties, () -> EndpointProperties.builder().build());
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
     * Get the name of the operation to resolve the endpoint for.
     *
     * @return the operation name.
     */
    public String operationName() {
        return operationName;
    }

    /**
     * Properties available when resolving the endpoint.
     *
     * @return properties.
     */
    public EndpointProperties properties() {
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
        EndpointResolverParams params = (EndpointResolverParams) o;
        return Objects.equals(operationName, params.operationName)
            && Objects.equals(properties, params.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationName, properties);
    }

    /**
     * Builder used to create {@link EndpointResolverParams}.
     */
    public static final class Builder {

        private String operationName;
        private EndpointProperties properties;

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
         * Set the endpoint properties.
         *
         * @param properties EndpointProperties to set.
         * @return the builder.
         */
        public Builder properties(EndpointProperties properties) {
            this.properties = properties;
            return this;
        }
    }
}
