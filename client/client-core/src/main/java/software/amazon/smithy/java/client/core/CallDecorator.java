/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * A decorator that wraps client call execution, allowing cross-cutting behavior to be applied
 * around operation invocations.
 *
 * <p>Implementations can short-circuit the call (e.g. returning a cached result) by not invoking {@code next}.
 *
 * @param <C> the client type this decorator is applied to.
 */
@FunctionalInterface
public interface CallDecorator<C> {
    /**
     * Applies decoration logic around a client call.
     *
     * @param client the client instance making the call.
     * @param call a view of the resolved call (input, operation, merged context, etc.).
     * @param next the next invoker in the chain to delegate the actual call to.
     * @param <I> the input type.
     * @param <O> the output type.
     * @return the operation output.
     */
    <I extends SerializableStruct, O extends SerializableStruct> O apply(
            C client,
            ClientCallView<I, O> call,
            Invoker next
    );

    /**
     * Compose two decorators so that {@code first} wraps {@code second}: {@code first.apply} runs first
     * and its {@code next} invokes {@code second.apply}, which in turn invokes the original {@code next}.
     *
     * <p>Either argument may be {@code null}, in which case the other is returned unchanged. If both are
     * {@code null}, returns {@code null}.
     *
     * @param first  outer decorator (runs first, wraps {@code second}).
     * @param second inner decorator (runs after {@code first} delegates).
     * @param <C>    client type shared by both decorators.
     * @return the composed decorator, or one of the arguments if the other is {@code null}.
     */
    static <C> CallDecorator<C> chain(CallDecorator<C> first, CallDecorator<C> second) {
        if (first == null) {
            return second;
        } else if (second == null) {
            return first;
        }

        return new CallDecorator<>() {
            @Override
            public <I extends SerializableStruct, O extends SerializableStruct> O apply(
                    C client,
                    ClientCallView<I, O> call,
                    Invoker next
            ) {
                return first.apply(client, call, new Invoker() {
                    @Override
                    public <X extends SerializableStruct, Y extends SerializableStruct> Y invoke(
                            ClientCallView<X, Y> c
                    ) {
                        return second.apply(client, c, next);
                    }
                });
            }
        };
    }

    /**
     * Invokes a resolved client call.
     *
     * <p>This functional interface represents the core call execution logic that a
     * {@link CallDecorator} delegates to after applying its decoration.
     */
    @FunctionalInterface
    interface Invoker {
        /**
         * Invokes the call.
         *
         * @param call the resolved call to invoke.
         * @param <I> the input type.
         * @param <O> the output type.
         * @return the operation output.
         */
        <I extends SerializableStruct, O extends SerializableStruct> O invoke(ClientCallView<I, O> call);
    }
}
