/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.interceptors;

import java.util.List;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.logging.InternalLogger;

final class ClientInterceptorChain implements ClientInterceptor {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(ClientInterceptorChain.class);
    private final ClientInterceptor[] interceptors;

    public ClientInterceptorChain(List<ClientInterceptor> interceptors) {
        if (interceptors.isEmpty()) {
            throw new IllegalArgumentException("Interceptors cannot be empty");
        }
        this.interceptors = interceptors.toArray(ClientInterceptor[]::new);
    }

    @Override
    public void readBeforeExecution(InputHook<?, ?> hook) {
        RuntimeException error = null;
        for (var interceptor : interceptors) {
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
        for (var interceptor : interceptors) {
            config = interceptor.modifyBeforeCall(hook.withConfig(config));
        }
        return config;
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
        var request = hook.request();
        for (var interceptor : interceptors) {
            request = interceptor.modifyBeforeRetryLoop(hook.withRequest(request));
        }
        return request;
    }

    @Override
    public void readBeforeAttempt(RequestHook<?, ?, ?> hook) {
        RuntimeException error = null;
        for (var interceptor : interceptors) {
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
        for (var interceptor : interceptors) {
            request = interceptor.modifyBeforeSigning(hook.withRequest(request));
        }
        return request;
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
        var request = hook.request();
        for (var interceptor : interceptors) {
            request = interceptor.modifyBeforeTransmit(hook.withRequest(request));
        }
        return request;
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
