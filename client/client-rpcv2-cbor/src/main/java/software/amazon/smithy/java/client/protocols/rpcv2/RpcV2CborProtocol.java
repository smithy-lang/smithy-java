/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.protocols.rpcv2;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.cbor.Rpcv2CborCodec;
import software.amazon.smithy.java.client.core.ClientProtocol;
import software.amazon.smithy.java.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.client.core.ProtocolSettings;
import software.amazon.smithy.java.client.http.HttpClientProtocol;
import software.amazon.smithy.java.client.http.HttpErrorDeserializer;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.Unit;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.ByteBufferOutputStream;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.protocol.traits.Rpcv2CborTrait;

public final class RpcV2CborProtocol extends HttpClientProtocol {
    private static final Codec CBOR_CODEC = Rpcv2CborCodec.builder().build();
    private static final List<String> CONTENT_TYPE = List.of("application/cbor");
    private static final List<String> SMITHY_PROTOCOL = List.of("rpc-v2-cbor");

    private final ShapeId service;
    private final HttpErrorDeserializer errorDeserializer;

    public RpcV2CborProtocol(ShapeId service) {
        super(Rpcv2CborTrait.ID);
        this.service = service;
        this.errorDeserializer = HttpErrorDeserializer.builder()
                .codec(CBOR_CODEC)
                .serviceId(service)
                .build();
    }

    @Override
    public <I extends SerializableStruct, O extends SerializableStruct> HttpRequest createRequest(
            ApiOperation<I, O> operation,
            I input,
            Context context,
            URI endpoint
    ) {
        var target = "/service/" + service.getName() + "/operation/" + operation.schema().id().getName();

        Map<String, List<String>> headers;
        DataStream body;
        if (Unit.ID.equals(operation.inputSchema().id())) {
            // Top-level Unit types do not get serialized
            headers = Map.of(
                    "smithy-protocol",
                    SMITHY_PROTOCOL,
                    "Accept",
                    CONTENT_TYPE);
            body = DataStream.ofEmpty();
        } else {
            var sink = new ByteBufferOutputStream();
            try (var serializer = CBOR_CODEC.createSerializer(sink)) {
                input.serialize(serializer);
            }
            headers = Map.of(
                    "Content-Type",
                    CONTENT_TYPE,
                    "smithy-protocol",
                    SMITHY_PROTOCOL,
                    "Accept",
                    CONTENT_TYPE);
            body = DataStream.ofByteBuffer(sink.toByteBuffer(), "application/cbor");
        }

        return HttpRequest.builder()
                .method("POST")
                .uri(endpoint.resolve(target))
                .headers(HttpHeaders.of(headers))
                .body(body)
                .build();
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

        var builder = operation.outputBuilder();
        var content = response.body();
        if (content.contentLength() == 0) {
            return CompletableFuture.completedFuture(builder.build());
        }

        return content.asByteBuffer()
                .thenApply(bytes -> CBOR_CODEC.deserializeShape(bytes, builder))
                .toCompletableFuture();
    }

    public static final class Factory implements ClientProtocolFactory<Rpcv2CborTrait> {
        @Override
        public ShapeId id() {
            return Rpcv2CborTrait.ID;
        }

        @Override
        public ClientProtocol<?, ?> createProtocol(ProtocolSettings settings, Rpcv2CborTrait trait) {
            return new RpcV2CborProtocol(
                    Objects.requireNonNull(
                            settings.service(),
                            "service is a required protocol setting"));
        }
    }
}
