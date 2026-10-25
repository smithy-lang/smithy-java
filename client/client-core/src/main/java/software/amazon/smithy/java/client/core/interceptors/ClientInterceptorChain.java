/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.interceptors;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.logging.InternalLogger;

final class ClientInterceptorChain implements ClientInterceptor {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(ClientInterceptorChain.class);
    private static final ClientInterceptor[] NONE = new ClientInterceptor[0];

    enum Hook {
        MODIFY_BEFORE_CALL("modifyBeforeCall", CallHook.class),
        READ_BEFORE_EXECUTION("readBeforeExecution", InputHook.class),
        MODIFY_BEFORE_SERIALIZATION("modifyBeforeSerialization", InputHook.class),
        READ_BEFORE_SERIALIZATION("readBeforeSerialization", InputHook.class),
        READ_AFTER_SERIALIZATION("readAfterSerialization", RequestHook.class),
        MODIFY_BEFORE_RETRY_LOOP("modifyBeforeRetryLoop", RequestHook.class),
        READ_BEFORE_ATTEMPT("readBeforeAttempt", RequestHook.class),
        MODIFY_BEFORE_SIGNING("modifyBeforeSigning", RequestHook.class),
        READ_BEFORE_SIGNING("readBeforeSigning", RequestHook.class),
        READ_AFTER_SIGNING("readAfterSigning", RequestHook.class),
        MODIFY_BEFORE_TRANSMIT("modifyBeforeTransmit", RequestHook.class),
        READ_BEFORE_TRANSMIT("readBeforeTransmit", RequestHook.class),
        READ_AFTER_TRANSMIT("readAfterTransmit", ResponseHook.class),
        MODIFY_BEFORE_DESERIALIZATION("modifyBeforeDeserialization", ResponseHook.class),
        READ_BEFORE_DESERIALIZATION("readBeforeDeserialization", ResponseHook.class),
        READ_AFTER_DESERIALIZATION("readAfterDeserialization", OutputHook.class, RuntimeException.class),
        MODIFY_BEFORE_ATTEMPT_COMPLETION("modifyBeforeAttemptCompletion", OutputHook.class, RuntimeException.class),
        READ_AFTER_ATTEMPT("readAfterAttempt", OutputHook.class, RuntimeException.class),
        MODIFY_BEFORE_COMPLETION("modifyBeforeCompletion", OutputHook.class, RuntimeException.class),
        READ_AFTER_EXECUTION("readAfterExecution", OutputHook.class, RuntimeException.class);

        final Method method;
        final long bit;

        Hook(String name, Class<?>... parameters) {
            this.bit = 1L << ordinal();
            try {
                this.method = ClientInterceptor.class.getMethod(name, parameters);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("ClientInterceptor has no hook " + name, e);
            }
        }
    }

    private static final ClassValue<Long> OVERRIDES = new ClassValue<>() {
        @Override
        protected Long computeValue(Class<?> type) {
            long overrides = 0;
            for (var hook : Hook.values()) {
                if (overrides(type, hook.method)) {
                    overrides |= hook.bit;
                }
            }
            return overrides;
        }
    };

    // Subset of interceptors where intercepts() == true. Folded for interceptCall.
    private final ClientInterceptor[] wrappers;
    private final boolean interceptsCalls;

    private final ClientInterceptor[] modifyBeforeCallInterceptors;
    private final ClientInterceptor[] readBeforeExecutionInterceptors;
    private final ClientInterceptor[] modifyBeforeSerializationInterceptors;
    private final ClientInterceptor[] readBeforeSerializationInterceptors;
    private final ClientInterceptor[] readAfterSerializationInterceptors;
    private final ClientInterceptor[] modifyBeforeRetryLoopInterceptors;
    private final ClientInterceptor[] readBeforeAttemptInterceptors;
    private final ClientInterceptor[] modifyBeforeSigningInterceptors;
    private final ClientInterceptor[] readBeforeSigningInterceptors;
    private final ClientInterceptor[] readAfterSigningInterceptors;
    private final ClientInterceptor[] modifyBeforeTransmitInterceptors;
    private final ClientInterceptor[] readBeforeTransmitInterceptors;
    private final ClientInterceptor[] readAfterTransmitInterceptors;
    private final ClientInterceptor[] modifyBeforeDeserializationInterceptors;
    private final ClientInterceptor[] readBeforeDeserializationInterceptors;
    private final ClientInterceptor[] readAfterDeserializationInterceptors;
    private final ClientInterceptor[] modifyBeforeAttemptCompletionInterceptors;
    private final ClientInterceptor[] readAfterAttemptInterceptors;
    private final ClientInterceptor[] modifyBeforeCompletionInterceptors;
    private final ClientInterceptor[] readAfterExecutionInterceptors;

    public ClientInterceptorChain(List<ClientInterceptor> interceptors) {
        if (interceptors.isEmpty()) {
            throw new IllegalArgumentException("Interceptors cannot be empty");
        }
        ClientInterceptor[] all = interceptors.toArray(ClientInterceptor[]::new);
        this.wrappers = interceptors.stream()
                .filter(ClientInterceptor::interceptCalls)
                .toArray(ClientInterceptor[]::new);
        this.interceptsCalls = this.wrappers.length > 0;

        long[] overrides = new long[all.length];
        for (int i = 0; i < all.length; i++) {
            overrides[i] = OVERRIDES.get(all[i].getClass());
        }
        this.modifyBeforeCallInterceptors = overriding(all, overrides, Hook.MODIFY_BEFORE_CALL);
        this.readBeforeExecutionInterceptors = overriding(all, overrides, Hook.READ_BEFORE_EXECUTION);
        this.modifyBeforeSerializationInterceptors = overriding(all, overrides, Hook.MODIFY_BEFORE_SERIALIZATION);
        this.readBeforeSerializationInterceptors = overriding(all, overrides, Hook.READ_BEFORE_SERIALIZATION);
        this.readAfterSerializationInterceptors = overriding(all, overrides, Hook.READ_AFTER_SERIALIZATION);
        this.modifyBeforeRetryLoopInterceptors = overriding(all, overrides, Hook.MODIFY_BEFORE_RETRY_LOOP);
        this.readBeforeAttemptInterceptors = overriding(all, overrides, Hook.READ_BEFORE_ATTEMPT);
        this.modifyBeforeSigningInterceptors = overriding(all, overrides, Hook.MODIFY_BEFORE_SIGNING);
        this.readBeforeSigningInterceptors = overriding(all, overrides, Hook.READ_BEFORE_SIGNING);
        this.readAfterSigningInterceptors = overriding(all, overrides, Hook.READ_AFTER_SIGNING);
        this.modifyBeforeTransmitInterceptors = overriding(all, overrides, Hook.MODIFY_BEFORE_TRANSMIT);
        this.readBeforeTransmitInterceptors = overriding(all, overrides, Hook.READ_BEFORE_TRANSMIT);
        this.readAfterTransmitInterceptors = overriding(all, overrides, Hook.READ_AFTER_TRANSMIT);
        this.modifyBeforeDeserializationInterceptors = overriding(all, overrides, Hook.MODIFY_BEFORE_DESERIALIZATION);
        this.readBeforeDeserializationInterceptors = overriding(all, overrides, Hook.READ_BEFORE_DESERIALIZATION);
        this.readAfterDeserializationInterceptors = overriding(all, overrides, Hook.READ_AFTER_DESERIALIZATION);
        this.modifyBeforeAttemptCompletionInterceptors =
                overridingOrFirstDefault(all, overrides, Hook.MODIFY_BEFORE_ATTEMPT_COMPLETION);
        this.readAfterAttemptInterceptors = overriding(all, overrides, Hook.READ_AFTER_ATTEMPT);
        this.modifyBeforeCompletionInterceptors =
                overridingOrFirstDefault(all, overrides, Hook.MODIFY_BEFORE_COMPLETION);
        this.readAfterExecutionInterceptors = overriding(all, overrides, Hook.READ_AFTER_EXECUTION);
    }

    static boolean overrides(Class<?> type, Hook hook) {
        return (OVERRIDES.get(type) & hook.bit) != 0;
    }

    private static boolean overrides(Class<?> type, Method hook) {
        try {
            return type.getMethod(hook.getName(), hook.getParameterTypes())
                    .getDeclaringClass() != ClientInterceptor.class;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(type.getName() + " has no " + hook.getName(), e);
        }
    }

    private static ClientInterceptor[] overriding(ClientInterceptor[] all, long[] overrides, Hook hook) {
        return select(all, overrides, hook, false);
    }

    /**
     * The default completion hooks rethrow pending errors.
     * Keep the first default to preserve the chain's stopping point.
     */
    private static ClientInterceptor[] overridingOrFirstDefault(
            ClientInterceptor[] all,
            long[] overrides,
            Hook hook
    ) {
        return select(all, overrides, hook, true);
    }

    private static ClientInterceptor[] select(
            ClientInterceptor[] all,
            long[] overrides,
            Hook hook,
            boolean keepFirstDefault
    ) {
        long bit = hook.bit;
        boolean keepDefault = keepFirstDefault;
        var selected = new ClientInterceptor[all.length];
        int n = 0;
        for (int i = 0; i < all.length; i++) {
            if ((overrides[i] & bit) != 0) {
                selected[n++] = all[i];
            } else if (keepDefault) {
                selected[n++] = all[i];
                keepDefault = false;
            }
        }
        if (n == 0) {
            return NONE;
        }
        return n == all.length ? selected : Arrays.copyOf(selected, n);
    }

    @Override
    public boolean interceptCalls() {
        return interceptsCalls;
    }

    @Override
    public <I extends SerializableStruct, O extends SerializableStruct> O interceptCall(
            InputHook<I, O> hook,
            NextCall<I, O> next
    ) {
        return new ChainInvoker<>(wrappers, next).invoke(hook);
    }

    @Override
    public void readBeforeExecution(InputHook<?, ?> hook) {
        RuntimeException error = null;
        for (var interceptor : readBeforeExecutionInterceptors) {
            try {
                interceptor.readBeforeExecution(hook);
            } catch (RuntimeException e) {
                error = swapError("readBeforeExecution", error, e);
            }
        }
        if (error != null) {
            throw error;
        }
    }

    @Override
    public ClientConfig modifyBeforeCall(CallHook<?, ?> hook) {
        var config = hook.config();
        for (var interceptor : modifyBeforeCallInterceptors) {
            config = interceptor.modifyBeforeCall(hook.withConfig(config));
        }
        return config;
    }

    @Override
    public <I extends SerializableStruct> I modifyBeforeSerialization(InputHook<I, ?> hook) {
        var input = hook.input();
        for (var interceptor : modifyBeforeSerializationInterceptors) {
            input = interceptor.modifyBeforeSerialization(hook.withInput(input));
        }
        return input;
    }

    @Override
    public void readBeforeSerialization(InputHook<?, ?> hook) {
        for (var interceptor : readBeforeSerializationInterceptors) {
            interceptor.readBeforeSerialization(hook);
        }
    }

    @Override
    public void readAfterSerialization(RequestHook<?, ?, ?> hook) {
        for (var interceptor : readAfterSerializationInterceptors) {
            interceptor.readAfterSerialization(hook);
        }
    }

    @Override
    public <RequestT> RequestT modifyBeforeRetryLoop(RequestHook<?, ?, RequestT> hook) {
        var request = hook.request();
        for (var interceptor : modifyBeforeRetryLoopInterceptors) {
            request = interceptor.modifyBeforeRetryLoop(hook.withRequest(request));
        }
        return request;
    }

    @Override
    public void readBeforeAttempt(RequestHook<?, ?, ?> hook) {
        RuntimeException error = null;
        for (var interceptor : readBeforeAttemptInterceptors) {
            try {
                interceptor.readBeforeAttempt(hook);
            } catch (RuntimeException e) {
                error = swapError("readBeforeAttempt", error, e);
            }
        }
        if (error != null) {
            throw error;
        }
    }

    @Override
    public <RequestT> RequestT modifyBeforeSigning(RequestHook<?, ?, RequestT> hook) {
        var request = hook.request();
        for (var interceptor : modifyBeforeSigningInterceptors) {
            request = interceptor.modifyBeforeSigning(hook.withRequest(request));
        }
        return request;
    }

    @Override
    public void readBeforeSigning(RequestHook<?, ?, ?> hook) {
        for (var interceptor : readBeforeSigningInterceptors) {
            interceptor.readBeforeSigning(hook);
        }
    }

    @Override
    public void readAfterSigning(RequestHook<?, ?, ?> hook) {
        for (var interceptor : readAfterSigningInterceptors) {
            interceptor.readAfterSigning(hook);
        }
    }

    @Override
    public <RequestT> RequestT modifyBeforeTransmit(RequestHook<?, ?, RequestT> hook) {
        var request = hook.request();
        for (var interceptor : modifyBeforeTransmitInterceptors) {
            request = interceptor.modifyBeforeTransmit(hook.withRequest(request));
        }
        return request;
    }

    @Override
    public void readBeforeTransmit(RequestHook<?, ?, ?> hook) {
        for (var interceptor : readBeforeTransmitInterceptors) {
            interceptor.readBeforeTransmit(hook);
        }
    }

    @Override
    public void readAfterTransmit(ResponseHook<?, ?, ?, ?> hook) {
        for (var interceptor : readAfterTransmitInterceptors) {
            interceptor.readAfterTransmit(hook);
        }
    }

    @Override
    public <ResponseT> ResponseT modifyBeforeDeserialization(ResponseHook<?, ?, ?, ResponseT> hook) {
        var response = hook.response();
        for (var interceptor : modifyBeforeDeserializationInterceptors) {
            response = interceptor.modifyBeforeDeserialization(hook.withResponse(response));
        }
        return response;
    }

    @Override
    public void readBeforeDeserialization(ResponseHook<?, ?, ?, ?> hook) {
        for (var interceptor : readBeforeDeserializationInterceptors) {
            interceptor.readBeforeDeserialization(hook);
        }
    }

    @Override
    public void readAfterDeserialization(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
        for (var interceptor : readAfterDeserializationInterceptors) {
            interceptor.readAfterDeserialization(hook, error);
        }
    }

    @Override
    public <O extends SerializableStruct> O modifyBeforeAttemptCompletion(
            OutputHook<?, O, ?, ?> hook,
            RuntimeException error
    ) {
        var output = hook.output();
        for (var interceptor : modifyBeforeAttemptCompletionInterceptors) {
            output = interceptor.modifyBeforeAttemptCompletion(hook.withOutput(output), error);
        }
        return output;
    }

    @Override
    public void readAfterAttempt(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
        var originalError = error;
        for (var interceptor : readAfterAttemptInterceptors) {
            try {
                interceptor.readAfterAttempt(hook, error);
            } catch (RuntimeException e) {
                error = swapError("readAfterAttempt", error, e);
            }
        }
        // No need to rethrow the original error since it's already registered as the error.
        if (error != null && error != originalError) {
            throw error;
        }
    }

    @Override
    public <O extends SerializableStruct> O modifyBeforeCompletion(
            OutputHook<?, O, ?, ?> hook,
            RuntimeException error
    ) {
        var output = hook.output();
        for (var interceptor : modifyBeforeCompletionInterceptors) {
            output = interceptor.modifyBeforeCompletion(hook.withOutput(output), error);
        }
        return output;
    }

    @Override
    public void readAfterExecution(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
        for (var interceptor : readAfterExecutionInterceptors) {
            try {
                interceptor.readAfterExecution(hook, error);
            } catch (RuntimeException e) {
                error = swapError("readAfterExecution", error, e);
            }
        }
        // Always throw the error even if it's the original error.
        if (error != null) {
            throw error;
        }
    }

    private static RuntimeException swapError(String hook, RuntimeException oldE, RuntimeException newE) {
        if (oldE != null && oldE != newE) {
            LOGGER.trace("Replacing error after {}: {}", hook, newE.getClass().getName(), newE.getMessage());
        }
        return newE;
    }
}
