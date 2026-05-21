/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * A decorator that wraps client call execution, allowing cross-cutting behavior to be applied
 * around operation invocations.
 *
 * <p>Implementations can inspect or modify the input, add logging, metrics, caching, or other
 * concerns before delegating to the next invoker in the chain.
 *
 * @param <C> the client type this decorator is applied to.
 */
@FunctionalInterface
public interface ClientCallDecorator<C> {
    /**
     * Applies decoration logic around a client call.
     *
     * @param client the client instance making the call.
     * @param operation the API operation being invoked.
     * @param input the operation input.
     * @param overrideConfig optional per-request configuration overrides, or {@code null}.
     * @param overrideContext optional per-request context overrides, or {@code null}.
     * @param next the next invoker in the chain to delegate the actual call to.
     * @param <I> the input type.
     * @param <O> the output type.
     * @return the operation output.
     */
    <I extends SerializableStruct, O extends SerializableStruct> O apply(
            C client,
            ApiOperation<I, O> operation,
            I input,
            RequestOverrideConfig overrideConfig,
            Context overrideContext,
            ClientCallInvoker next
    );
}
