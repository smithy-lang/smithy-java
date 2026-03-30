/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsjson;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.aws.events.AwsEventDecoderFactory;
import software.amazon.smithy.java.aws.events.AwsEventEncoderFactory;
import software.amazon.smithy.java.aws.events.AwsEventFrame;
import software.amazon.smithy.java.aws.events.RpcEventStreamsUtil;
import software.amazon.smithy.java.client.http.AmznErrorHeaderExtractor;
import software.amazon.smithy.java.client.http.HttpClientProtocol;
import software.amazon.smithy.java.client.http.HttpErrorDeserializer;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.core.serde.event.EventDecoderFactory;
import software.amazon.smithy.java.core.serde.event.EventEncoderFactory;
import software.amazon.smithy.java.core.serde.event.EventStreamingException;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.SmithyUri;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Abstract class for RPC style JSON protocols.
 */
abstract sealed class AwsJsonProtocol extends HttpClientProtocol permits AwsJson1Protocol, AwsJson11Protocol {

    private static final byte[] EMPTY_PAYLOAD = "{}".getBytes(StandardCharsets.UTF_8);

    private final ShapeId service;
    private final JsonCodec codec;
    private final HttpErrorDeserializer errorDeserializer;

    /**
     * @param service The service ID used to make X-Amz-Target, and the namespace is used when finding the
     *                discriminator of documents that use relative shape IDs.
     */
    public AwsJsonProtocol(
            ShapeId trait,
            ShapeId service,
            HttpErrorDeserializer.ErrorPayloadParser errorPayloadParser
    ) {
        super(trait);
        this.service = service;
        this.codec = JsonCodec.builder()
                .defaultNamespace(service.getNamespace())
                .useTimestampFormat(true)
                .build();

        this.errorDeserializer = HttpErrorDeserializer.builder()
                .codec(codec)
                .serviceId(service)
                .errorPayloadParser(errorPayloadParser)
                .headerErrorExtractor(new AmznErrorHeaderExtractor())
                .build();
    }

    protected abstract String contentType();

    @Override
    public Codec payloadCodec() {
        return codec;
    }

    @Override
    public <I extends SerializableStruct, O extends SerializableStruct> HttpRequest createRequest(
            ApiOperation<I, O> operation,
            I input,
            Context context,
            SmithyUri endpoint
    ) {
        var target = service.getName() + "." + operation.schema().id().getName();
        var builder = HttpRequest.builder();
        builder.method("POST");
        builder.uri(endpoint);
        if (operation.inputEventBuilderSupplier() != null) {
            // Event streaming
            var encoderFactory = getEventEncoderFactory(operation);
            var body = RpcEventStreamsUtil.bodyForEventStreaming(encoderFactory, input);
            builder.headers(HttpHeaders.of(headersForEventStreaming(target)))
                    .body(body);
        } else {
            builder.headers(
                    HttpHeaders.of(
                            Map.of(
                                    "x-amz-target",
                                    List.of(target),
                                    "content-type",
                                    List.of(contentType()))));
        }
        return builder.body(DataStream.ofByteBuffer(codec.serialize(input), contentType())).build();
    }

    @Override
    public <I extends SerializableStruct, O extends SerializableStruct> O deserializeResponse(
            ApiOperation<I, O> operation,
            Context context,
            TypeRegistry typeRegistry,
            HttpRequest request,
            HttpResponse response
    ) {
        // Is it an error?
        if (response.statusCode() != 200) {
            throw errorDeserializer.createError(context, operation, typeRegistry, response);
        }

        if (operation.outputEventBuilderSupplier() != null) {
            var eventDecoderFactory = getEventDecoderFactory(operation);
            return RpcEventStreamsUtil.deserializeResponse(eventDecoderFactory, bodyDataStream(response));
        }

        var builder = operation.outputBuilder();
        var content = response.body();

        // If the payload is empty, then use "{}" as the default empty payload.
        if (content.contentLength() == 0) {
            return codec.deserializeShape(EMPTY_PAYLOAD, builder);
        }

        var bytes = content.asByteBuffer();
        return codec.deserializeShape(bytes, builder);
    }

    private EventEncoderFactory<AwsEventFrame> getEventEncoderFactory(ApiOperation<?, ?> operation) {
        return AwsEventEncoderFactory.forInputStream(operation,
                payloadCodec(),
                contentType(),
                (e) -> new EventStreamingException("InternalServerException", "Internal Server Error"));
    }

    private EventDecoderFactory<AwsEventFrame> getEventDecoderFactory(ApiOperation<?, ?> operation) {
        return AwsEventDecoderFactory.forOutputStream(operation, payloadCodec(), f -> f);
    }

    private Map<String, List<String>> headersForEventStreaming(String target) {
        return Map.of("x-amz-target",
                List.of(target),
                "content-type",
                List.of("application/vnd.amazon.eventstream"),
                "Accept",
                List.of(contentType()));
    }

    private static DataStream bodyDataStream(HttpResponse response) {
        var contentType = response.headers().contentType();
        var contentLength = response.headers().contentLength();
        return DataStream.withMetadata(response.body(), contentType, contentLength, null);
    }
}
