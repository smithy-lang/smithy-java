/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rpcv2;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.aws.events.AwsEventDecoderFactory;
import software.amazon.smithy.java.aws.events.AwsEventEncoderFactory;
import software.amazon.smithy.java.aws.events.AwsEventFrame;
import software.amazon.smithy.java.aws.events.RpcEventStreamsUtil;
import software.amazon.smithy.java.cbor.Rpcv2CborCodec;
import software.amazon.smithy.java.client.core.ClientProtocol;
import software.amazon.smithy.java.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.client.core.ProtocolSettings;
import software.amazon.smithy.java.client.http.HttpClientProtocol;
import software.amazon.smithy.java.client.http.HttpErrorDeserializer;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.InputEventStreamingApiOperation;
import software.amazon.smithy.java.core.schema.OutputEventStreamingApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.Unit;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.core.serde.event.EventDecoderFactory;
import software.amazon.smithy.java.core.serde.event.EventEncoderFactory;
import software.amazon.smithy.java.core.serde.event.EventStreamingException;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.ByteBufferOutputStream;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.protocol.traits.Rpcv2CborTrait;

public final class RpcV2CborProtocol extends HttpClientProtocol {
    private static final Codec CBOR_CODEC = Rpcv2CborCodec.builder().build();
    private static final String PAYLOAD_MEDIA_TYPE = "application/cbor";
    private static final List<String> CONTENT_TYPE = List.of(PAYLOAD_MEDIA_TYPE);
    private static final List<String> SMITHY_PROTOCOL = List.of("rpc-v2-cbor");

    private final ShapeId service;
    private final HttpErrorDeserializer errorDeserializer;

    public RpcV2CborProtocol(ShapeId service) {
        super(Rpcv2CborTrait.ID);
        this.service = service;
        this.errorDeserializer = HttpErrorDeserializer.builder().codec(CBOR_CODEC).serviceId(service).build();
    }

    @Override
    public Codec payloadCodec() {
        return CBOR_CODEC;
    }

    @Override
    public <I extends SerializableStruct, O extends SerializableStruct> HttpRequest createRequest(
            ApiOperation<I, O> operation,
            I input,
            Context context,
            URI endpoint
    ) {
        var target = "/service/" + service.getName() + "/operation/" + operation.schema().id().getName();
        var builder = HttpRequest.builder().method("POST").uri(endpoint.resolve(target));

        builder.httpVersion(HttpVersion.HTTP_2);
        if (Unit.ID.equals(operation.inputSchema().id())) {
            // Top-level Unit types do not get serialized
            builder.headers(HttpHeaders.of(headersForEmptyBody()))
                    .body(DataStream.ofEmpty());
        } else if (operation instanceof InputEventStreamingApiOperation<?, ?, ?> i) {
            // Event streaming
            var encoderFactory = getEventEncoderFactory(i);
            var body = RpcEventStreamsUtil.bodyForEventStreaming(encoderFactory, input);
            builder.headers(HttpHeaders.of(headersForEventStreaming()))
                    .body(body);
        } else {
            // Regular request
            builder.headers(HttpHeaders.of(headers()))
                    .body(getBody(input));
        }
        return builder.build();
    }

    @Override
    public <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> deserializeResponse(
            ApiOperation<I, O> operation,
            Context context,
            TypeRegistry typeRegistry,
            HttpRequest request,
            HttpResponse response
    ) {
        if (response.statusCode() != 200) {
            return errorDeserializer.createError(context, operation.schema().id(), typeRegistry, response)
                    .thenApply(e -> {
                        throw e;
                    });
        }

        if (operation instanceof OutputEventStreamingApiOperation<I, O, ?> o) {
            var eventDecoderFactory = getEventDecoderFactory(o);
            return RpcEventStreamsUtil.deserializeResponse(eventDecoderFactory, bodyDataStream(response));
        }

        var builder = operation.outputBuilder();
        var content = response.body();
        if (content.contentLength() == 0) {
            return CompletableFuture.completedFuture(builder.build());
        }

        return content.asByteBuffer()
                .thenApply(bytes -> CBOR_CODEC.deserializeShape(bytes, builder))
                .toCompletableFuture();
    }

    private static DataStream bodyDataStream(HttpResponse response) {
        var contentType = response.headers().contentType();
        var contentLength = response.headers().contentLength();
        return DataStream.withMetadata(response.body(), contentType, contentLength, null);
    }

    private DataStream getBody(SerializableStruct input) {
        var sink = new ByteBufferOutputStream();
        try (var serializer = CBOR_CODEC.createSerializer(sink)) {
            input.serialize(serializer);
        }
        return DataStream.ofByteBuffer(sink.toByteBuffer(), PAYLOAD_MEDIA_TYPE);
    }

    private Map<String, List<String>> headers() {
        return Map.of("smithy-protocol", SMITHY_PROTOCOL, "Content-Type", CONTENT_TYPE, "Accept", CONTENT_TYPE);
    }

    private Map<String, List<String>> headersForEmptyBody() {
        return Map.of("smithy-protocol", SMITHY_PROTOCOL, "Accept", CONTENT_TYPE);
    }

    private Map<String, List<String>> headersForEventStreaming() {
        return Map.of("smithy-protocol",
                SMITHY_PROTOCOL,
                "Content-Type",
                List.of("application/vnd.amazon.eventstream"),
                "Accept",
                CONTENT_TYPE);
    }

    private EventEncoderFactory<AwsEventFrame> getEventEncoderFactory(
            InputEventStreamingApiOperation<?, ?, ?> inputOperation
    ) {

        // TODO: this is where you'd plumb through Sigv4 support, another frame transformer?
        return AwsEventEncoderFactory.forInputStream(inputOperation,
                payloadCodec(),
                PAYLOAD_MEDIA_TYPE,
                (e) -> new EventStreamingException("InternalServerException", "Internal Server Error"));
    }

    private EventDecoderFactory<AwsEventFrame> getEventDecoderFactory(
            OutputEventStreamingApiOperation<?, ?, ?> outputOperation
    ) {
        return AwsEventDecoderFactory.forOutputStream(outputOperation,
                payloadCodec(),
                f -> f);
    }

    public static final class Factory implements ClientProtocolFactory<Rpcv2CborTrait> {
        @Override
        public ShapeId id() {
            return Rpcv2CborTrait.ID;
        }

        @Override
        public ClientProtocol<?, ?> createProtocol(ProtocolSettings settings, Rpcv2CborTrait trait) {
            return new RpcV2CborProtocol(
                    Objects.requireNonNull(settings.service(), "service is a required protocol setting"));
        }
    }
}
