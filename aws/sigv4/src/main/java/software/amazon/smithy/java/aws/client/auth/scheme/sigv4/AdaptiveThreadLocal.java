/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Supplier;

/**
 * Create an AdaptiveThreadLocal based on if running in a virtual or platform thread.
 *
 * <p>Virtual threads will always just allocate a value using the supplier, whereas a platform thread will cache
 * the value using a {@link ThreadLocal}. Remember to reset any state when using a returned value.
 *
 * @param <T> Kind of value to return.
 */
sealed interface AdaptiveThreadLocal<T> {
    /**
     * Create an AdaptiveThreadLocal.
     *
     * @param supplier Supplier used to create the value.
     * @return the created AdaptiveThreadLocal.
     * @param <T> Value to return.
     */
    static <T> AdaptiveThreadLocal<T> withInitial(Supplier<T> supplier) {
        return isVirtual(Thread.currentThread())
            ? new NoCache<>(supplier)
            : new ThreadLocalBased<>(supplier);
    }

    /**
     * Get the value.
     *
     * @return The value, whether it's cached or new.
     */
    T get();

    private static boolean isVirtual(Thread thread) {
        if (ThreadLocalBased.IS_VIRTUAL_HANDLE == null) {
            return false;
        } else {
            try {
                return (boolean) ThreadLocalBased.IS_VIRTUAL_HANDLE.invoke(thread);
            } catch (Throwable e) {
                throw new RuntimeException("Error invoking isVirtual method", e);
            }
        }
    }

    /**
     * Values are cached in a {@link ThreadLocal}.
     *
     * @param <T> Value to return.
     */
    final class ThreadLocalBased<T> implements AdaptiveThreadLocal<T> {
        private static final MethodHandle IS_VIRTUAL_HANDLE;

        static {
            MethodHandle handle = null;
            try {
                handle = MethodHandles
                    .lookup()
                    .findVirtual(Thread.class, "isVirtual", MethodType.methodType(boolean.class));
            } catch (NoSuchMethodException | IllegalAccessException ignored) {
                // Older JDK that doesn't support virtual threads.
            }
            IS_VIRTUAL_HANDLE = handle;
        }

        private final ThreadLocal<T> threadLocal;

        ThreadLocalBased(Supplier<T> supplier) {
            this.threadLocal = ThreadLocal.withInitial(supplier);
        }

        @Override
        public T get() {
            return threadLocal.get();
        }
    }

    /**
     * Values are always created when get is called.
     *
     * @param <T> Value to return.
     */
    final class NoCache<T> implements AdaptiveThreadLocal<T> {
        private final Supplier<T> supplier;

        NoCache(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public T get() {
            return supplier.get();
        }
    }
}
