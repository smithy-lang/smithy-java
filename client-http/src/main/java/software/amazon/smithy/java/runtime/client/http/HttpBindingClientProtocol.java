/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.runtime.core.schema.ApiException;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.InputEventStreamingApiOperation;
import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.OutputEventStreamingApiOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
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
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * An HTTP-based protocol that uses HTTP binding traits.
 *
 * <p>TODO: Allow for a custom matcher to identity errors beyond X-Amzn-Errortype.
 *
 * @param <F> the framing type for event streams.
 */
public class HttpBindingClientProtocol<F extends Frame<?>> extends HttpClientProtocol {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(HttpBindingClientProtocol.class);
    private static final String X_AMZN_ERROR_TYPE = "X-Amzn-Errortype";

    protected final Codec codec;

    public HttpBindingClientProtocol(String id, Codec codec) {
        super(id);
        this.codec = Objects.requireNonNull(codec, "codec is null");
    }

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
            .payloadCodec(codec)
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
        if (!isSuccess(response)) {
            return createError(operation, context, typeRegistry, response).thenApply(e -> {
                throw e;
            });
        }

        LOGGER.trace("Deserializing successful response with {}", getClass().getName());

        var outputBuilder = operation.outputBuilder();
        ResponseDeserializer deser = HttpBinding.responseDeserializer()
            .payloadCodec(codec)
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

    private boolean isSuccess(SmithyHttpResponse response) {
        // TODO: Better error checking.
        return response.statusCode() >= 200 && response.statusCode() <= 299;
    }

    /**
     * An overrideable error deserializer.
     *
     * @param response HTTP response to deserialize.
     * @return Returns the deserialized error.
     */
    protected <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<? extends ApiException> createError(
        ApiOperation<I, O> operation,
        Context context,
        TypeRegistry typeRegistry,
        SmithyHttpResponse response
    ) {
        return response.headers()
            // Grab the error ID from the header first.
            .firstValue(X_AMZN_ERROR_TYPE)
            // If not in the header, check the payload for __type.
            .or(() -> {
                // TODO: check payload for type.
                return Optional.empty();
            })
            // Attempt to match the extracted error ID to a modeled error type.
            .flatMap(
                errorId -> Optional.ofNullable(
                    typeRegistry.createBuilder(ShapeId.from(errorId), ModeledApiException.class)
                )
                    .<CompletableFuture<? extends ApiException>>map(
                        error -> createModeledException(codec, response, error)
                    )
            )
            // If no error was matched, then create an error from protocol hints.
            .orElseGet(() -> {
                String operationId = operation.schema().id().toString();
                return HttpClientProtocol.createErrorFromHints(operationId, response);
            });
    }

    private CompletableFuture<ModeledApiException> createModeledException(
        Codec codec,
        SmithyHttpResponse response,
        ShapeBuilder<ModeledApiException> error
    ) {
        return HttpBinding.responseDeserializer()
            .payloadCodec(codec)
            .errorShapeBuilder(error)
            .response(response)
            .deserialize()
            .thenApply(ignore -> error.errorCorrection().build());
    }
}
