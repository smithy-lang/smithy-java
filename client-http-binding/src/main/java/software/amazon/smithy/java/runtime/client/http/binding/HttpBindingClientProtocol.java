/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http.binding;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.runtime.client.http.HttpClientProtocol;
import software.amazon.smithy.java.runtime.client.http.HttpErrorDeserializer;
import software.amazon.smithy.java.runtime.core.schema.ApiException;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.InputEventStreamingApiOperation;
import software.amazon.smithy.java.runtime.core.schema.OutputEventStreamingApiOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.TypeRegistry;
import software.amazon.smithy.java.runtime.core.serde.event.EventDecoderFactory;
import software.amazon.smithy.java.runtime.core.serde.event.EventEncoderFactory;
import software.amazon.smithy.java.runtime.core.serde.event.Frame;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.http.binding.HttpBinding;
import software.amazon.smithy.java.runtime.http.binding.RequestSerializer;
import software.amazon.smithy.java.runtime.http.binding.ResponseDeserializer;

/**
 * An HTTP-based protocol that uses HTTP binding traits.
 *
 * @param <F> the framing type for event streams.
 */
public abstract class HttpBindingClientProtocol<F extends Frame<?>> extends HttpClientProtocol {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(HttpBindingClientProtocol.class);

    public HttpBindingClientProtocol(String id) {
        super(id);
    }

    abstract protected Codec codec();

    abstract protected HttpErrorDeserializer getErrorDeserializer(Context context);

    protected EventEncoderFactory<F> getEventEncoderFactory(InputEventStreamingApiOperation<?, ?, ?> inputOperation) {
        throw new UnsupportedOperationException("This protocol does not support event streaming");
    }

    protected EventDecoderFactory<F> getEventDecoderFactory(OutputEventStreamingApiOperation<?, ?, ?> outputOperation) {
        throw new UnsupportedOperationException("This protocol does not support event streaming");
    }

    @Override
    public <I extends SerializableStruct, O extends SerializableStruct> SmithyHttpRequest createRequest(
        ApiOperation<I, O> operation,
        I input,
        Context context,
        URI endpoint
    ) {
        RequestSerializer serializer = HttpBinding.requestSerializer()
            .operation(operation)
            .payloadCodec(codec())
            .shapeValue(input)
            .endpoint(endpoint);

        if (operation instanceof InputEventStreamingApiOperation<?, ?, ?> i) {
            serializer.eventEncoderFactory(getEventEncoderFactory(i));
        }

        return serializer.serializeRequest();
    }

    @Override
    public <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> deserializeResponse(
        ApiOperation<I, O> operation,
        Context context,
        TypeRegistry typeRegistry,
        SmithyHttpRequest request,
        SmithyHttpResponse response
    ) {
        if (!isSuccess(operation, context, response)) {
            return createError(operation, context, typeRegistry, request, response).thenApply(e -> {
                throw e;
            });
        }

        LOGGER.trace("Deserializing successful response with {}", getClass().getName());

        var outputBuilder = operation.outputBuilder();
        ResponseDeserializer deser = HttpBinding.responseDeserializer()
            .payloadCodec(codec())
            .outputShapeBuilder(outputBuilder)
            .response(response);

        if (operation instanceof OutputEventStreamingApiOperation<?, ?, ?> o) {
            deser.eventDecoderFactory(getEventDecoderFactory(o));
        }

        return deser
            .deserialize()
            .thenApply(ignore -> {
                O output = outputBuilder.errorCorrection().build();

                // TODO: error handling from the builder.
                LOGGER.trace("Successfully built {} from HTTP response with {}", output, getClass().getName());

                return output;
            });
    }

    /**
     * Check if the response is a success or failure.
     *
     * @param response Response to check.
     * @return true if it is a success.
     */
    protected boolean isSuccess(ApiOperation<?, ?> operation, Context context, SmithyHttpResponse response) {
        return response.statusCode() >= 200 && response.statusCode() <= 299;
    }

    /**
     * An overrideable error deserializer.
     *
     * <p>Override this class if using {@link HttpErrorDeserializer} is impossible for your protocol.
     *
     * @param operation Operation being called.
     * @param context Call context.
     * @param typeRegistry Registry used to deserialize errors.
     * @param request Request that was sent.
     * @param response HTTP response to deserialize.
     * @return Returns the deserialized error.
     */
    protected <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<? extends ApiException> createError(
        ApiOperation<I, O> operation,
        Context context,
        TypeRegistry typeRegistry,
        SmithyHttpRequest request,
        SmithyHttpResponse response
    ) {
        return getErrorDeserializer(context).createError(context, operation.schema().id(), typeRegistry, response);
    }
}
