/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api.scheme;

import java.util.List;
import java.util.Objects;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;

/**
 * Resolves the authentication scheme that should be used to sign a request.
 */
@FunctionalInterface
public interface AuthSchemeResolver {
    /**
     * Resolve the auth scheme options using the given parameters.
     *
     * <p>The returned list of options is priority ordered. Clients should use the first option they support in the
     * returned list.
     *
     * @param params Params used to resolve the auth scheme.
     * @return the resolved auth scheme options.
     */
    List<AuthSchemeOption> resolveAuthScheme(Params params);

    /**
     * Create a new builder to build Params.
     *
     * @return the params builder.
     */
    static Params.Builder paramsBuilder() {
        return new Params.Builder();
    }

    /**
     * AuthSchemeResolver parameters.
     */
    final class Params {

        private final String protocolId;
        private final String operationName;
        private final AuthProperties properties;

        private Params(Builder builder) {
            this.protocolId = Objects.requireNonNull(builder.protocolId, "protocolId is null");
            this.operationName = Objects.requireNonNull(builder.operationName, "operationName is null");
            this.properties = Objects.requireNonNullElseGet(builder.properties, () -> AuthProperties.builder().build());
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
         * Get the name of the operation to resolve.
         *
         * @return the operation name.
         */
        public String operationName() {
            return operationName;
        }

        /**
         * Properties to query when resolving the auth shemes.
         *
         * @return properties.
         */
        public AuthProperties properties() {
            return properties;
        }

        /**
         * Builder used to create Params.
         */
        public static final class Builder {

            private String protocolId;
            private String operationName;
            private AuthProperties properties;

            private Builder() {
            }

            public Params build() {
                return new Params(this);
            }

            public Builder protocolId(String protocolId) {
                this.protocolId = protocolId;
                return this;
            }

            public Builder operationName(String operationName) {
                this.operationName = operationName;
                return this;
            }

            public Builder properties(AuthProperties properties) {
                this.properties = properties;
                return this;
            }
        }
    }
}
