/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

/**
 * Settings used to instantiate a {@link ClientProtocol} implementation.
 */
public final class ProtocolSettings {
    private final String namespace;

    public ProtocolSettings(Builder builder) {
        this.namespace = builder.namespace;
    }

    public String namespace() {
        return namespace;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String namespace;

        private Builder() {}

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public ProtocolSettings build() {
            return new ProtocolSettings(this);
        }
    }
}
