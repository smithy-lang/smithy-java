/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * Invokes a client operation call with the given input and configuration.
 *
 * <p>This functional interface represents the core call execution logic that a
 * {@link ClientCallDecorator} delegates to after applying its decoration.
 */
@FunctionalInterface
public interface ClientCallInvoker {
    /**
     * Invokes the operation.
     *
     * @param input the operation input.
     * @param operation the API operation being invoked.
     * @param overrideConfig optional per-request configuration overrides, or {@code null}.
     * @param <I> the input type.
     * @param <O> the output type.
     * @return the operation output.
     */
    <I extends SerializableStruct, O extends SerializableStruct> O invoke(
            I input,
            ApiOperation<I, O> operation,
            RequestOverrideConfig overrideConfig
    );
}
