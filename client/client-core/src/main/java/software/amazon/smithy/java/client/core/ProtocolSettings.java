/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Settings used to instantiate a {@link ClientProtocol} implementation.
 */
public final class ProtocolSettings {
    private final ShapeId service;
    private final String serviceVersion;

    private ProtocolSettings(Builder builder) {
        this.service = builder.service;
        this.serviceVersion = builder.serviceVersion;
    }

    public ShapeId service() {
        return service;
    }

    /**
     * Gets the service version string.
     *
     * <p>The service version is required by some protocols (e.g., AWS Query)
     * that include the version in the request body.
     *
     * @return the service version, or null if not set
     */
    public String serviceVersion() {
        return serviceVersion;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ShapeId service;
        private String serviceVersion;

        private Builder() {}

        public Builder service(ShapeId service) {
            this.service = service;
            return this;
        }

        /**
         * Sets the service version string.
         *
         * @param serviceVersion the service version
         * @return the builder
         */
        public Builder serviceVersion(String serviceVersion) {
            this.serviceVersion = serviceVersion;
            return this;
        }

        public ProtocolSettings build() {
            return new ProtocolSettings(this);
        }
    }
}
