/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.interceptors;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.logging.InternalLogger;

final class ClientInterceptorChain implements ClientInterceptor {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(ClientInterceptorChain.class);
    private final List<ClientInterceptor> interceptors;

    public ClientInterceptorChain(List<ClientInterceptor> interceptors) {
        if (interceptors.isEmpty()) {
            throw new IllegalArgumentException("Interceptors cannot be empty");
        }
        this.interceptors = interceptors;
    }

    @Override
    public void readBeforeExecution(InputHook<?, ?> hook) {
        applyToEachThrowLastError("readBeforeExecution", ClientInterceptor::readBeforeExecution, hook);
    }

    // Many interceptors require running each hook, logging errors, and throwing the last.
    private <T> void applyToEachThrowLastError(String hookName, BiConsumer<ClientInterceptor, T> consumer, T hook) {
        RuntimeException error = null;
        for (var interceptor : interceptors) {
            try {
                consumer.accept(interceptor, hook);
            } catch (RuntimeException e) {
                error = swapError(hookName, error, e);
            }
        }

        if (error != null) {
            throw error;
        }
    }

    @Override
    public <I extends SerializableStruct> I modifyBeforeSerialization(InputHook<I, ?> hook) {
        var input = hook.input();
        for (var interceptor : interceptors) {
            input = interceptor.modifyBeforeSerialization(hook.withInput(input));
        }
        return input;
    }

    @Override
    public void readBeforeSerialization(InputHook<?, ?> hook) {
        for (var interceptor : interceptors) {
            interceptor.readBeforeSerialization(hook);
        }
    }

    @Override
    public void readAfterSerialization(RequestHook<?, ?, ?> hook) {
        for (var interceptor : interceptors) {
            interceptor.readAfterSerialization(hook);
        }
    }

    @Override
    public <RequestT> RequestT modifyBeforeRetryLoop(RequestHook<?, ?, RequestT> hook) {
        return modifyRequestHook(ClientInterceptor::modifyBeforeRetryLoop, hook);
    }

    private <I extends SerializableStruct, RequestT> RequestT modifyRequestHook(
            BiFunction<ClientInterceptor, RequestHook<I, ?, RequestT>, RequestT> mapper,
            RequestHook<I, ?, RequestT> hook
    ) {
        var request = hook.request();
        for (var interceptor : interceptors) {
            request = mapper.apply(interceptor, hook.withRequest(request));
        }
        return request;
    }

    @Override
    public void readBeforeAttempt(RequestHook<?, ?, ?> hook) {
        applyToEachThrowLastError("readBeforeAttempt", ClientInterceptor::readBeforeAttempt, hook);
    }

    @Override
    public <RequestT> RequestT modifyBeforeSigning(RequestHook<?, ?, RequestT> hook) {
        return modifyRequestHook(ClientInterceptor::modifyBeforeSigning, hook);
    }

    @Override
    public void readBeforeSigning(RequestHook<?, ?, ?> hook) {
        for (var interceptor : interceptors) {
            interceptor.readBeforeSigning(hook);
        }
    }

    @Override
    public void readAfterSigning(RequestHook<?, ?, ?> hook) {
        for (var interceptor : interceptors) {
            interceptor.readAfterSigning(hook);
        }
    }

    @Override
    public <RequestT> RequestT modifyBeforeTransmit(RequestHook<?, ?, RequestT> hook) {
        return modifyRequestHook(ClientInterceptor::modifyBeforeTransmit, hook);
    }

    @Override
    public void readBeforeTransmit(RequestHook<?, ?, ?> hook) {
        for (var interceptor : interceptors) {
            interceptor.readBeforeTransmit(hook);
        }
    }

    @Override
    public void readAfterTransmit(ResponseHook<?, ?, ?, ?> hook) {
        for (var interceptor : interceptors) {
            interceptor.readAfterTransmit(hook);
        }
    }

    @Override
    public <ResponseT> ResponseT modifyBeforeDeserialization(ResponseHook<?, ?, ?, ResponseT> hook) {
        var response = hook.response();
        for (var interceptor : interceptors) {
            response = interceptor.modifyBeforeDeserialization(hook.withResponse(response));
        }
        return response;
    }

    @Override
    public void readBeforeDeserialization(ResponseHook<?, ?, ?, ?> hook) {
        for (var interceptor : interceptors) {
            interceptor.readBeforeDeserialization(hook);
        }
    }

    @Override
    public void readAfterDeserialization(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
        for (var interceptor : interceptors) {
            interceptor.readAfterDeserialization(hook, error);
        }
    }

    @Override
    public <O extends SerializableStruct> O modifyBeforeAttemptCompletion(
            OutputHook<?, O, ?, ?> hook,
            RuntimeException error
    ) {
        var output = hook.output();
        for (var interceptor : interceptors) {
            output = interceptor.modifyBeforeAttemptCompletion(hook.withOutput(output), error);
        }
        return output;
    }

    @Override
    public void readAfterAttempt(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
        var originalError = error;
        for (var interceptor : interceptors) {
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
        for (var interceptor : interceptors) {
            output = interceptor.modifyBeforeCompletion(hook.withOutput(output), error);
        }
        return output;
    }

    @Override
    public void readAfterExecution(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
        for (var interceptor : interceptors) {
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
