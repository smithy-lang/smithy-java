/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.interceptors;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.utils.TriConsumer;

final class ClientInterceptorChain implements ClientInterceptor {

    private static final System.Logger LOGGER = System.getLogger(ClientInterceptorChain.class.getName());
    private final List<ClientInterceptor> interceptors;

    public ClientInterceptorChain(List<ClientInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    @Override
    public void readBeforeExecution(InputHook<?> hook) {
        applyToEachThrowLastError(ClientInterceptor::readBeforeExecution, hook);
    }

    // Many interceptors require running each hook, logging errors, and throwing the last.
    private <T> void applyToEachThrowLastError(BiConsumer<ClientInterceptor, T> consumer, T hook) {
        RuntimeException error = null;
        for (var interceptor : interceptors) {
            try {
                consumer.accept(interceptor, hook);
            } catch (RuntimeException e) {
                if (error != null) {
                    LOGGER.log(System.Logger.Level.ERROR, e);
                }
                error = e;
            }
        }

        if (error != null) {
            throw error;
        }
    }

    @Override
    public <I extends SerializableStruct> I modifyBeforeSerialization(InputHook<I> hook) {
        var input = hook.input();
        for (var interceptor : interceptors) {
            input = interceptor.modifyBeforeSerialization(hook.withInput(input));
        }
        return input;
    }

    @Override
    public void readBeforeSerialization(InputHook<?> hook) {
        for (var interceptor : interceptors) {
            interceptor.readBeforeSerialization(hook);
        }
    }

    @Override
    public void readAfterSerialization(RequestHook<?, ?> hook) {
        for (var interceptor : interceptors) {
            interceptor.readAfterSerialization(hook);
        }
    }

    @Override
    public <RequestT> RequestT modifyBeforeRetryLoop(RequestHook<?, RequestT> hook) {
        return modifyRequestHook(ClientInterceptor::modifyBeforeRetryLoop, hook);
    }

    private <I extends SerializableStruct, RequestT> RequestT modifyRequestHook(
        BiFunction<ClientInterceptor, RequestHook<I, RequestT>, RequestT> mapper,
        RequestHook<I, RequestT> hook
    ) {
        var request = hook.request();
        for (var interceptor : interceptors) {
            request = mapper.apply(interceptor, hook.withRequest(request));
        }
        return request;
    }

    @Override
    public void readBeforeAttempt(RequestHook<?, ?> hook) {
        applyToEachThrowLastError(ClientInterceptor::readBeforeAttempt, hook);
    }

    @Override
    public <RequestT> RequestT modifyBeforeSigning(RequestHook<?, RequestT> hook) {
        return modifyRequestHook(ClientInterceptor::modifyBeforeSigning, hook);
    }

    @Override
    public void readBeforeSigning(RequestHook<?, ?> hook) {
        for (var interceptor : interceptors) {
            interceptor.readBeforeSigning(hook);
        }
    }

    @Override
    public void readAfterSigning(RequestHook<?, ?> hook) {
        for (var interceptor : interceptors) {
            interceptor.readAfterSigning(hook);
        }
    }

    @Override
    public <RequestT> RequestT modifyBeforeTransmit(RequestHook<?, RequestT> hook) {
        return modifyRequestHook(ClientInterceptor::modifyBeforeTransmit, hook);
    }

    @Override
    public void readBeforeTransmit(RequestHook<?, ?> hook) {
        for (var interceptor : interceptors) {
            interceptor.readBeforeTransmit(hook);
        }
    }

    @Override
    public void readAfterTransmit(ResponseHook<?, ?, ?> hook) {
        for (var interceptor : interceptors) {
            interceptor.readAfterTransmit(hook);
        }
    }

    @Override
    public <ResponseT> ResponseT modifyBeforeDeserialization(ResponseHook<?, ?, ResponseT> hook) {
        var response = hook.response();
        for (var interceptor : interceptors) {
            response = interceptor.modifyBeforeDeserialization(hook.withResponse(response));
        }
        return response;
    }

    @Override
    public void readBeforeDeserialization(ResponseHook<?, ?, ?> hook) {
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
        OutputHook<?, ?, ?, O> hook,
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
        applyToEachThrowLastError(ClientInterceptor::readAfterAttempt, hook, error);
    }

    private <T> void applyToEachThrowLastError(
        TriConsumer<ClientInterceptor, T, RuntimeException> consumer,
        T hook,
        RuntimeException error
    ) {
        for (var interceptor : interceptors) {
            try {
                consumer.accept(interceptor, hook, error);
            } catch (RuntimeException e) {
                if (error != null) {
                    LOGGER.log(System.Logger.Level.ERROR, error);
                }
                error = e;
            }
        }

        if (error != null) {
            throw error;
        }
    }

    @Override
    public <O extends SerializableStruct> O modifyBeforeCompletion(
        OutputHook<?, ?, ?, O> hook,
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
        applyToEachThrowLastError(ClientInterceptor::readAfterExecution, hook, error);
    }
}
