/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.interceptors;

import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * Hook to add request level overrides to client calls using {@link RequestOverrideConfig}.
 *
 * @param <I> Input shape.
 * @param <O> Output shape.
 */
public record CallHook<I extends SerializableStruct, O extends SerializableStruct>(
        ApiOperation<I, O> operation,
        ClientConfig config,
        I input) {
    /**
     * Get the API operation being called.
     *
     * @return the operation being called.
     */
    @Override
    public ApiOperation<I, O> operation() {
        return operation;
    }

    /**
     * Get the client config of the hook.
     *
     * @return the config.
     */
    @Override
    public ClientConfig config() {
        return config;
    }

    /**
     * Get the always present input shape value.
     *
     * @return the input value.
     */
    @Override
    public I input() {
        return input;
    }

    /**
     * Create a new CallHook using the given config.
     *
     * @param config Config to use.
     * @return the new CallHook or the current hook if the config hasn't changed.
     */
    public CallHook<I, O> withConfig(ClientConfig config) {
        if (config == this.config) {
            return this;
        } else {
            return new CallHook<>(operation, config, input);
        }
    }
}
