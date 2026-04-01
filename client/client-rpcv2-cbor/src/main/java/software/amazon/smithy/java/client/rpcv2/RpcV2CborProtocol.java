/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rpcv2;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.aws.events.AwsEventDecoderFactory;
import software.amazon.smithy.java.aws.events.AwsEventEncoderFactory;
import software.amazon.smithy.java.aws.events.AwsEventFrame;
import software.amazon.smithy.java.aws.events.RpcEventStreamsUtil;
import software.amazon.smithy.java.cbor.Rpcv2CborCodec;
import software.amazon.smithy.java.client.core.ClientProtocol;
import software.amazon.smithy.java.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.client.core.ProtocolSettings;
import software.amazon.smithy.java.client.http.ErrorTypeUtils;
import software.amazon.smithy.java.client.http.HttpClientProtocol;
import software.amazon.smithy.java.client.http.HttpErrorDeserializer;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.document.DocumentDeserializer;
import software.amazon.smithy.java.core.serde.event.EventDecoderFactory;
import software.amazon.smithy.java.core.serde.event.EventEncoderFactory;
import software.amazon.smithy.java.core.serde.event.EventStreamingException;
import software.amazon.smithy.java.http.api.HeaderNames;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.ByteBufferOutputStream;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.SmithyUri;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.protocol.traits.Rpcv2CborTrait;

/**
 * Implements smithy.protocols#rpcv2Cbor.
 */
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
        this.errorDeserializer = HttpErrorDeserializer.builder()
                .codec(CBOR_CODEC)
                .serviceId(service)
                .errorPayloadParser(RpcV2CborProtocol::extractErrorType)
                .build();
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
            SmithyUri endpoint
    ) {
        var target = "/service/" + service.getName() + "/operation/" + operation.schema().id().getName();
        var builder = HttpRequest.create().setMethod("POST").setUri(endpoint.withConcatPath(target));

        builder.setHttpVersion(HttpVersion.HTTP_2);
        if (operation.inputSchema().hasTrait(TraitKey.UNIT_TYPE_TRAIT)) {
            // Top-level Unit types do not get serialized
            builder.setHeaders(HttpHeaders.of(headersForEmptyBody()))
                    .setBody(DataStream.ofEmpty());
        } else if (operation.inputEventBuilderSupplier() != null) {
            // Event streaming
            var encoderFactory = getEventEncoderFactory(operation);
            var body = RpcEventStreamsUtil.bodyForEventStreaming(encoderFactory, input);
            builder.setHeaders(HttpHeaders.of(headersForEventStreaming()))
                    .setBody(body);
        } else {
            // Regular request
            builder.setHeaders(HttpHeaders.of(headers()))
                    .setBody(getBody(input));
        }
        return builder.toUnmodifiable();
    }

    @Override
    public <I extends SerializableStruct, O extends SerializableStruct> O deserializeResponse(
            ApiOperation<I, O> operation,
            Context context,
            TypeRegistry typeRegistry,
            HttpRequest request,
            HttpResponse response
    ) {
        if (response.statusCode() != 200) {
            throw errorDeserializer.createError(context, operation, typeRegistry, response);
        }

        if (operation.outputEventBuilderSupplier() != null) {
            var eventDecoderFactory = getEventDecoderFactory(operation);
            return RpcEventStreamsUtil.deserializeResponse(eventDecoderFactory, bodyDataStream(response));
        }

        var builder = operation.outputBuilder();
        var content = response.body();
        if (content.contentLength() == 0) {
            return builder.build();
        }

        var bytes = content.asByteBuffer();
        return CBOR_CODEC.deserializeShape(bytes, builder);
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
        return Map.of(HeaderNames.SMITHY_PROTOCOL,
                SMITHY_PROTOCOL,
                HeaderNames.CONTENT_TYPE,
                CONTENT_TYPE,
                HeaderNames.ACCEPT,
                CONTENT_TYPE);
    }

    private Map<String, List<String>> headersForEmptyBody() {
        return Map.of(HeaderNames.SMITHY_PROTOCOL, SMITHY_PROTOCOL, HeaderNames.ACCEPT, CONTENT_TYPE);
    }

    private Map<String, List<String>> headersForEventStreaming() {
        return Map.of(HeaderNames.SMITHY_PROTOCOL,
                SMITHY_PROTOCOL,
                HeaderNames.CONTENT_TYPE,
                List.of("application/vnd.amazon.eventstream"),
                HeaderNames.ACCEPT,
                CONTENT_TYPE);
    }

    private EventEncoderFactory<AwsEventFrame> getEventEncoderFactory(ApiOperation<?, ?> operation) {
        return AwsEventEncoderFactory.forInputStream(operation,
                payloadCodec(),
                PAYLOAD_MEDIA_TYPE,
                (e) -> new EventStreamingException("InternalServerException", "Internal Server Error"));
    }

    private EventDecoderFactory<AwsEventFrame> getEventDecoderFactory(ApiOperation<?, ?> operation) {
        return AwsEventDecoderFactory.forOutputStream(operation, payloadCodec(), f -> f);
    }

    private static ShapeId extractErrorType(Document document, String namespace) {
        return DocumentDeserializer.parseDiscriminator(
                ErrorTypeUtils.removeUri(ErrorTypeUtils.readType(document)),
                namespace);
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
