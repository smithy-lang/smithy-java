/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rpcv2;

import software.amazon.smithy.java.aws.events.AwsEventDecoderFactory;
import software.amazon.smithy.java.aws.events.AwsEventEncoderFactory;
import software.amazon.smithy.java.aws.events.AwsEventFrame;
import software.amazon.smithy.java.aws.events.RpcEventStreamsUtil;
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
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.ModifiableHttpRequest;
import software.amazon.smithy.java.io.ByteBufferOutputStream;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.SmithyUri;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Shared base for RPC v2 client protocol implementations.
 *
 * <p>Subclasses provide the codec and media type that distinguish the concrete wire
 * format (CBOR vs JSON). All request construction, response deserialization, error
 * extraction, and event-streaming plumbing is handled here.
 */
@SmithyInternalApi
public abstract class AbstractRpcV2ClientProtocol extends HttpClientProtocol {

    private final ShapeId service;
    private final String payloadMediaType;
    private final String smithyProtocolValue;
    private volatile HttpErrorDeserializer errorDeserializer;

    private static final String SMITHY_PROTOCOL_PREFIX = "rpc-v2-";
    // length of "application/"
    private static final int MEDIA_TYPE_PREFIX_LENGTH = 12;

    /**
     * @param protocolId       the Smithy protocol trait shape ID
     * @param service          the service shape ID
     * @param payloadMediaType the media type for request/response payloads (e.g. "application/cbor")
     */
    protected AbstractRpcV2ClientProtocol(
            ShapeId protocolId,
            ShapeId service,
            String payloadMediaType
    ) {
        super(protocolId);
        this.service = service;
        this.payloadMediaType = payloadMediaType;
        this.smithyProtocolValue = SMITHY_PROTOCOL_PREFIX
                + payloadMediaType.substring(MEDIA_TYPE_PREFIX_LENGTH);
    }

    /** Returns the codec used for serialization and deserialization. */
    protected abstract Codec codec();

    protected ShapeId service() {
        return service;
    }

    @Override
    public Codec payloadCodec() {
        return codec();
    }

    private HttpErrorDeserializer errorDeserializer() {
        if (errorDeserializer == null) {
            errorDeserializer = HttpErrorDeserializer.builder()
                    .codec(codec())
                    .serviceId(service)
                    .errorPayloadParser(AbstractRpcV2ClientProtocol::extractErrorType)
                    .build();
        }
        return errorDeserializer;
    }

    /**
     * Hook for subclasses to customize the request builder before headers and body are set.
     * For example, the CBOR protocol uses this to force HTTP/2.
     */
    protected void customizeRequestBuilder(ModifiableHttpRequest builder) {
        // default: no customization
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

        customizeRequestBuilder(builder);

        if (operation.inputSchema().hasTrait(TraitKey.UNIT_TYPE_TRAIT)) {
            builder.addHeader(HeaderName.SMITHY_PROTOCOL, smithyProtocolValue)
                    .addHeader(HeaderName.ACCEPT, payloadMediaType)
                    .setBody(DataStream.ofEmpty());
        } else if (operation.inputEventBuilderSupplier() != null) {
            var encoderFactory = getEventEncoderFactory(operation);
            var body = RpcEventStreamsUtil.bodyForEventStreaming(encoderFactory, input);
            builder.addHeader(HeaderName.SMITHY_PROTOCOL, smithyProtocolValue)
                    .addHeader(HeaderName.CONTENT_TYPE, "application/vnd.amazon.eventstream")
                    .addHeader(HeaderName.ACCEPT, payloadMediaType)
                    .setBody(body);
        } else {
            builder.addHeader(HeaderName.SMITHY_PROTOCOL, smithyProtocolValue)
                    .addHeader(HeaderName.CONTENT_TYPE, payloadMediaType)
                    .addHeader(HeaderName.ACCEPT, payloadMediaType)
                    .setBody(getBody(input));
        }
        return builder;
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
            throw errorDeserializer().createError(context, operation, typeRegistry, response);
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
        return codec().deserializeShape(bytes, builder);
    }

    private static DataStream bodyDataStream(HttpResponse response) {
        var contentType = response.headers().contentType();
        var contentLength = response.headers().contentLength();
        return DataStream.withMetadata(response.body(), contentType, contentLength, null);
    }

    private DataStream getBody(SerializableStruct input) {
        var sink = new ByteBufferOutputStream();
        try (var serializer = codec().createSerializer(sink)) {
            input.serialize(serializer);
        }
        return DataStream.ofByteBuffer(sink.toByteBuffer(), payloadMediaType);
    }

    private EventEncoderFactory<AwsEventFrame> getEventEncoderFactory(ApiOperation<?, ?> operation) {
        return AwsEventEncoderFactory.forInputStream(operation,
                codec(),
                payloadMediaType,
                (e) -> new EventStreamingException("InternalServerException", "Internal Server Error"));
    }

    private EventDecoderFactory<AwsEventFrame> getEventDecoderFactory(ApiOperation<?, ?> operation) {
        return AwsEventDecoderFactory.forOutputStream(operation, codec(), f -> f);
    }

    private static ShapeId extractErrorType(Document document, String namespace) {
        return DocumentDeserializer.parseDiscriminator(
                ErrorTypeUtils.removeUri(ErrorTypeUtils.readType(document)),
                namespace);
    }
}
