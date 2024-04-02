/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.interceptors;

import static software.amazon.smithy.java.runtime.core.Context.Value;

import java.util.List;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.core.Either;
import software.amazon.smithy.java.runtime.core.schema.SdkException;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;

final class ClientInterceptorChain implements ClientInterceptor {

    private static final System.Logger LOGGER = System.getLogger(ClientInterceptorChain.class.getName());
    private final List<ClientInterceptor> interceptors;

    public ClientInterceptorChain(List<ClientInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    @Override
    public <I extends SerializableShape> void readBeforeExecution(Context context, I input) {
        applyToEachThrowLastError(interceptor -> interceptor.readBeforeExecution(context, input));
    }

    // Many interceptors require running each hook, logging errors if multiple are encountered, and throwing the last.
    private void applyToEachThrowLastError(Consumer<ClientInterceptor> consumer) {
        RuntimeException error = null;
        for (var interceptor : interceptors) {
            try {
                consumer.accept(interceptor);
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
    public <I extends SerializableShape> I modifyBeforeSerialization(Context context, I input) {
        for (var interceptor : interceptors) {
            input = interceptor.modifyBeforeSerialization(context, input);
        }
        return input;
    }

    @Override
    public <I extends SerializableShape> void readBeforeSerialization(Context context, I input) {
        for (var interceptor : interceptors) {
            interceptor.readBeforeSerialization(context, input);
        }
    }

    @Override
    public <I extends SerializableShape, RequestT> void readAfterSerialization(
        Context context,
        I input,
        Value<RequestT> request
    ) {
        for (var interceptor : interceptors) {
            interceptor.readAfterSerialization(context, input, request);
        }
    }

    @Override
    public <I extends SerializableShape, RequestT> Value<RequestT> modifyBeforeRetryLoop(
        Context context,
        I input,
        Value<RequestT> request
    ) {
        for (var interceptor : interceptors) {
            request = interceptor.modifyBeforeRetryLoop(context, input, request);
        }
        return request;
    }

    @Override
    public <I extends SerializableShape, RequestT> void readBeforeAttempt(
        Context context,
        I input,
        Value<RequestT> request
    ) {
        applyToEachThrowLastError(interceptor -> interceptor.readBeforeAttempt(context, input, request));
    }

    @Override
    public <I extends SerializableShape, RequestT> Value<RequestT> modifyBeforeSigning(
        Context context,
        I input,
        Value<RequestT> request
    ) {
        for (var interceptor : interceptors) {
            request = interceptor.modifyBeforeSigning(context, input, request);
        }
        return request;
    }

    @Override
    public <I extends SerializableShape, RequestT> void readBeforeSigning(
        Context context,
        I input,
        Value<RequestT> request
    ) {
        for (var interceptor : interceptors) {
            interceptor.readBeforeSigning(context, input, request);
        }
    }

    @Override
    public <I extends SerializableShape, RequestT> void readAfterSigning(
        Context context,
        I input,
        Value<RequestT> request
    ) {
        for (var interceptor : interceptors) {
            interceptor.readAfterSigning(context, input, request);
        }
    }

    @Override
    public <I extends SerializableShape, RequestT> Value<RequestT> modifyBeforeTransmit(
        Context context,
        I input,
        Value<RequestT> request
    ) {
        for (var interceptor : interceptors) {
            request = interceptor.modifyBeforeTransmit(context, input, request);
        }
        return request;
    }

    @Override
    public <I extends SerializableShape, RequestT> void readBeforeTransmit(
        Context context,
        I input,
        Value<RequestT> request
    ) {
        for (var interceptor : interceptors) {
            interceptor.readBeforeTransmit(context, input, request);
        }
    }

    @Override
    public <I extends SerializableShape, RequestT, ResponseT> void readAfterTransmit(
        Context context,
        I input,
        Value<RequestT> request,
        Value<ResponseT> response
    ) {
        for (var interceptor : interceptors) {
            interceptor.readAfterTransmit(context, input, request, response);
        }
    }

    @Override
    public <I extends SerializableShape, RequestT, ResponseT> Value<ResponseT> modifyBeforeDeserialization(
        Context context,
        I input,
        Value<RequestT> request,
        Value<ResponseT> response
    ) {
        for (var interceptor : interceptors) {
            response = interceptor.modifyBeforeDeserialization(context, input, request, response);
        }
        return response;
    }

    @Override
    public <I extends SerializableShape, RequestT, ResponseT> void readBeforeDeserialization(
        Context context,
        I input,
        Value<RequestT> request,
        Value<ResponseT> response
    ) {
        for (var interceptor : interceptors) {
            interceptor.readBeforeDeserialization(context, input, request, response);
        }
    }

    @Override
    public <I extends SerializableShape, O extends SerializableShape, RequestT, ResponseT> void readAfterDeserialization(
        Context context,
        I input,
        Value<RequestT> request,
        Value<ResponseT> response,
        Either<SdkException, O> result
    ) {
        for (var interceptor : interceptors) {
            interceptor.readAfterDeserialization(context, input, request, response, result);
        }
    }

    @Override
    public <I extends SerializableShape, O extends SerializableShape, RequestT, ResponseT> Either<SdkException, O> modifyBeforeAttemptCompletion(
        Context context,
        I input,
        Value<RequestT> request,
        Value<ResponseT> response,
        Either<SdkException, O> result
    ) {
        for (var interceptor : interceptors) {
            result = interceptor.modifyBeforeAttemptCompletion(context, input, request, response, result);
        }
        return result;
    }

    @Override
    public <I extends SerializableShape, O extends SerializableShape, RequestT, ResponseT> void readAfterAttempt(
        Context context,
        I input,
        Value<RequestT> request,
        Value<ResponseT> responseIfAvailable,
        Either<SdkException, O> result
    ) {
        applyToEachThrowLastError(
            interceptor -> interceptor.readAfterAttempt(context, input, request, responseIfAvailable, result)
        );
    }

    @Override
    public <I extends SerializableShape, O extends SerializableShape, RequestT, ResponseT> Either<SdkException, O> modifyBeforeCompletion(
        Context context,
        I input,
        Value<RequestT> requestIfAvailable,
        Value<ResponseT> responseIfAvailable,
        Either<SdkException, O> result
    ) {
        for (var interceptor : interceptors) {
            result = interceptor.modifyBeforeCompletion(
                context,
                input,
                requestIfAvailable,
                responseIfAvailable,
                result
            );
        }
        return result;
    }

    @Override
    public <I extends SerializableShape, O extends SerializableShape, RequestT, ResponseT> void readAfterExecution(
        Context context,
        I input,
        Value<RequestT> requestIfAvailable,
        Value<ResponseT> responseIfAvailable,
        Either<SdkException, O> result
    ) {
        applyToEachThrowLastError(
            interceptor -> interceptor.readAfterExecution(
                context,
                input,
                requestIfAvailable,
                responseIfAvailable,
                result
            )
        );
    }
}
