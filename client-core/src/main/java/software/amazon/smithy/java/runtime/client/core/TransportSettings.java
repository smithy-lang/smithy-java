/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

/**
 * Settings used to configure a {@link ClientTransport} implementation.
 */
// TODO: Determine settings
public final class TransportSettings {

    private TransportSettings(Builder builder) {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Builder() {}

        public TransportSettings build() {
            return new TransportSettings(this);
        }
    }
}
