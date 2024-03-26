/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import software.amazon.smithy.java.runtime.api.Endpoint;
import software.amazon.smithy.java.runtime.client.core.ClientCall;
import software.amazon.smithy.java.runtime.client.core.ClientProtocol;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.core.schema.ModeledSdkException;
import software.amazon.smithy.java.runtime.core.schema.SdkException;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.uri.URIBuilder;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpClient;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.http.binding.HttpBinding;

/**
 * An abstract HTTP-Based protocol.
 */
public abstract class HttpClientProtocol implements ClientProtocol<SmithyHttpRequest, SmithyHttpResponse> {

    private static final System.Logger LOGGER = System.getLogger(HttpClientProtocol.class.getName());
    private static final String X_AMZN_ERROR_TYPE = "X-Amzn-Errortype";
    private static final Set<String> TEXT_CONTENT_TYPES = Set.of("application/json", "application/xml");

    private final String id;
    private final SmithyHttpClient client;
    private final Codec codec;

    public HttpClientProtocol(String id, SmithyHttpClient client, Codec codec) {
        this.id = id;
        this.client = client;
        this.codec = codec;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Context.Key<SmithyHttpRequest> requestKey() {
        return HttpContext.HTTP_REQUEST;
    }

    @Override
    public Context.Key<SmithyHttpResponse> responseKey() {
        return HttpContext.HTTP_RESPONSE;
    }

    // TODO: Figure out a better name and approach for this.
    @Override
    public SmithyHttpRequest setServiceEndpoint(SmithyHttpRequest request, Endpoint endpoint) {
        var uri = endpoint.uri();
        URIBuilder builder = URIBuilder.of(request.uri());

        if (uri.getScheme() != null) {
            builder.scheme(uri.getScheme());
        }

        if (uri.getHost() != null) {
            builder.host(uri.getHost());
        }

        if (uri.getPort() > -1) {
            builder.port(uri.getPort());
        }

        // If a path is set on the service endpoint, concatenate it with the path of the request.
        if (uri.getRawPath() != null && !uri.getRawPath().isEmpty()) {
            builder.path(uri.getRawPath());
            builder.concatPath(request.uri().getPath());
        }

        // Merge in any HTTP headers found on the endpoint.
        if (endpoint.endpointAttribute(HttpEndpointKeys.HTTP_HEADERS) != null) {
            request = request.withAddedHeaders(endpoint.endpointAttribute(HttpEndpointKeys.HTTP_HEADERS));
        }

        return request.withUri(builder.build());
    }

    /**
     * Create an unsigned HTTP request for the given call.
     *
     * @param call     Call being invoked.s
     * @param codec    Codec used to serialize structured payloads.
     * @param endpoint Where to send the request.
     * @return Returns the serialized request.
     */
    abstract protected SmithyHttpRequest createHttpRequest(ClientCall<?, ?> call, Codec codec, URI endpoint);

    /**
     * Send the HTTP request and return the eventually completed response.
     *
     * @param call    Call being invoked.
     * @param client  Client used to send the request.
     * @param request Request to send.
     * @return the response.
     */
    abstract SmithyHttpResponse sendHttpRequest(ClientCall<?, ?> call,
            SmithyHttpClient client,
            SmithyHttpRequest request);

    /**
     * Deserializes the HTTP response and returns the updated output stream.
     *
     * @param call     Call being invoked.
     * @param codec    Codec used to deserialize structured payloads.
     * @param request  Request that was sent.
     * @param response Response that was received.
     * @param builder  Builder to populate while deserializing.
     * @param <I> Input shape.
     * @param <O> Output shape.
     */
    protected abstract <I extends SerializableShape, O extends SerializableShape> void deserializeHttpResponse(
            ClientCall<I, O> call,
            Codec codec,
            SmithyHttpRequest request,
            SmithyHttpResponse response,
            SdkShapeBuilder<O> builder);

    @Override
    public final SmithyHttpRequest createRequest(ClientCall<?, ?> call, URI endpoint) {
        // Initialize the context with more HTTP information.
        call.context().put(HttpContext.HTTP_PAYLOAD_CODEC, codec);
        var request = createHttpRequest(call, codec, endpoint);
        call.context().put(HttpContext.HTTP_REQUEST, request);
        return request;
    }

    @Override
    public final SmithyHttpResponse sendRequest(ClientCall<?, ?> call, SmithyHttpRequest request) {
        LOGGER.log(System.Logger.Level.TRACE, () -> "Sending HTTP request: " + request.startLine());
        var response = sendHttpRequest(call, client, request);
        LOGGER.log(System.Logger.Level.TRACE, () -> "Got HTTP response: " + response.startLine());
        call.context().put(HttpContext.HTTP_RESPONSE, response);
        return response;
    }

    @Override
    public final <I extends SerializableShape, O extends SerializableShape> O deserializeResponse(ClientCall<I, O> call,
            SmithyHttpRequest request,
            SmithyHttpResponse response) {
        if (isSuccess(call, response)) {
            LOGGER.log(System.Logger.Level.TRACE, "Deserializing successful response with " + getClass().getName());
            var outputBuilder = call.createOutputBuilder(call.context(),
                    call.operation().outputSchema().id().toString());
            deserializeHttpResponse(call, codec, request, response, outputBuilder);
            LOGGER.log(System.Logger.Level.TRACE, "Deserialized HTTP response with " + getClass().getName() + " into "
                    + outputBuilder.getClass().getName());
            O output = outputBuilder.errorCorrection().build();
            // TODO: error handling from the builder.
            LOGGER.log(System.Logger.Level.TRACE,
                    "Successfully built " + output + " from HTTP response with " + getClass().getName());
            return output;
        } else {
            throw createError(call, response);
        }
    }

    private boolean isSuccess(ClientCall<?, ?> call, SmithyHttpResponse response) {
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
                .flatMap(errorId -> call.createExceptionBuilder(call.context(), errorId)
                        .<SdkException>map(error -> createModeledException(response, error)))
                // If no error was matched, then create an error from protocol hints.
                .orElseGet(() -> createErrorFromHints(call, response));
    }

    private ModeledSdkException createModeledException(SmithyHttpResponse response,
            SdkShapeBuilder<ModeledSdkException> error) {
        // Deserialize the error response.
        HttpBinding.responseDeserializer()
                .payloadCodec(codec)
                .errorShapeBuilder(error)
                .response(response)
                .deserialize();
        return error.errorCorrection().build();
    }

    private SdkException createErrorFromHints(ClientCall<?, ?> call, SmithyHttpResponse response) {
        LOGGER.log(System.Logger.Level.WARNING,
                () -> "Unknown " + response.statusCode() + " error response from " + call.operation().schema().id());

        SdkException.Fault fault = determineFault(response.statusCode());
        StringBuilder message = new StringBuilder();
        message.append(switch (fault) {
            case CLIENT -> "Client error ";
            case SERVER -> "Server error ";
            default -> "Unknown error ";
        });

        message.append("encountered from operation ").append(call.operation().schema().id());
        message.append(System.lineSeparator());
        message.append(response.httpVersion()).append(' ').append(response.statusCode()).append(System.lineSeparator());
        writeHeaders(message, response.headers());
        message.append(System.lineSeparator());

        String contentType = response.headers()
                .firstValue("Content-Type")
                .orElse("application/octet-stream")
                .toLowerCase(Locale.ENGLISH);

        if (!isText(contentType)) {
            return new SdkException(message.toString(), fault);
        }

        try {
            message.append(new String(response.body().readNBytes(16384), StandardCharsets.UTF_8));
        } catch (IOException e) {
            // ignore
        }

        return new SdkException(message.toString(), fault);
    }

    private boolean isText(String contentType) {
        return contentType.startsWith("text/") || contentType.contains("charset=utf-8") || contentType.endsWith("+json")
                || contentType.endsWith("+xml") || TEXT_CONTENT_TYPES.contains(contentType);
    }

    private SdkException.Fault determineFault(int statusCode) {
        if (statusCode >= 400 && statusCode <= 499) {
            return SdkException.Fault.CLIENT;
        } else if (statusCode >= 500 && statusCode <= 599) {
            return SdkException.Fault.SERVER;
        } else {
            return SdkException.Fault.OTHER;
        }
    }

    private void writeHeaders(StringBuilder builder, HttpHeaders headers) {
        headers.map().forEach((field, values) -> {
            values.forEach(value -> {
                builder.append(field).append(": ").append(value).append(System.lineSeparator());
            });
        });
    }
}
