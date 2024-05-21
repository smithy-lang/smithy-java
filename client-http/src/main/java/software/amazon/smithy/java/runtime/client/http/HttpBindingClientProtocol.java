/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import software.amazon.smithy.java.runtime.client.core.ClientCall;
import software.amazon.smithy.java.runtime.core.schema.ModeledSdkException;
import software.amazon.smithy.java.runtime.core.schema.SdkException;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.http.binding.HttpBinding;

/**
 * An HTTP-based protocol that uses HTTP binding traits.
 *
 * <p>TODO: Allow for a custom matcher to identity errors beyond X-Amzn-Errortype.
 */
public class HttpBindingClientProtocol extends HttpClientProtocol {

    private static final System.Logger LOGGER = System.getLogger(HttpBindingClientProtocol.class.getName());
    private static final String X_AMZN_ERROR_TYPE = "X-Amzn-Errortype";

    private final Codec codec;

    public HttpBindingClientProtocol(String id, Codec codec) {
        super(id);
        this.codec = Objects.requireNonNull(codec, "codec is null");
    }

    @Override
    public SmithyHttpRequest createRequest(ClientCall<?, ?> call, URI endpoint) {
        return HttpBinding.requestSerializer()
            .operation(call.operation().schema())
            .payload(call.requestInputStream().orElse(null))
            .payloadCodec(codec)
            .shapeValue(call.input())
            .endpoint(endpoint)
            .serializeRequest();
    }

    @Override
    public <I extends SerializableStruct, O extends SerializableStruct> O deserializeResponse(
        ClientCall<I, O> call,
        SmithyHttpRequest request,
        SmithyHttpResponse response
    ) {
        if (!isSuccess(response)) {
            throw createError(call, response);
        }

        LOGGER.log(System.Logger.Level.TRACE, "Deserializing successful response with %s", getClass().getName());

        var outputBuilder = call.createOutputBuilder(call.context(), call.operation().outputSchema().id().toString());
        HttpBinding.responseDeserializer()
            .payloadCodec(codec)
            .outputShapeBuilder(outputBuilder)
            .response(response)
            .deserialize();

        O output = outputBuilder.errorCorrection().build();

        // TODO: error handling from the builder.
        LOGGER.log(
            System.Logger.Level.TRACE,
            () -> "Successfully built " + output + " from HTTP response with " + getClass().getName()
        );

        return outputBuilder.errorCorrection().build();
    }

    private boolean isSuccess(SmithyHttpResponse response) {
        // TODO: Better error checking.
        return response.statusCode() >= 200 && response.statusCode() <= 299;
    }

    /**
     * An overrideable error deserializer.
     *
     * @param call     Call being sent.
     * @param response HTTP response to deserialize.
     * @return Returns the deserialized error.
     */
    protected SdkException createError(ClientCall<?, ?> call, SmithyHttpResponse response) {
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
                errorId -> call.createExceptionBuilder(call.context(), errorId)
                    .<SdkException>map(error -> createModeledException(codec, response, error))
            )
            // If no error was matched, then create an error from protocol hints.
            .orElseGet(() -> {
                String operationId = call.operation().schema().id().toString();
                return HttpClientProtocol.createErrorFromHints(operationId, response);
            });
    }

    private ModeledSdkException createModeledException(
        Codec codec,
        SmithyHttpResponse response,
        SdkShapeBuilder<ModeledSdkException> error
    ) {
        HttpBinding.responseDeserializer()
            .payloadCodec(codec)
            .errorShapeBuilder(error)
            .response(response)
            .deserialize();
        return error.errorCorrection().build();
    }
}
