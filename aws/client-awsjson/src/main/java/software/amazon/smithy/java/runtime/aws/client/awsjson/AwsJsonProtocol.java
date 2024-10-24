/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.aws.client.awsjson;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.http.AmznErrorHeaderExtractor;
import software.amazon.smithy.java.runtime.client.http.HttpClientProtocol;
import software.amazon.smithy.java.runtime.client.http.HttpErrorDeserializer;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.TypeRegistry;
import software.amazon.smithy.java.runtime.http.api.HttpHeaders;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;
import software.amazon.smithy.java.runtime.json.JsonCodec;
import software.amazon.smithy.model.shapes.ShapeId;

abstract sealed class AwsJsonProtocol extends HttpClientProtocol permits AwsJson1Protocol, AwsJson11Protocol {

    private static final byte[] EMPTY_PAYLOAD = "{}".getBytes(StandardCharsets.UTF_8);

    private final ShapeId service;
    private final JsonCodec codec = JsonCodec.builder().build();
    private final HttpErrorDeserializer errorDeserializer;

    /**
     * @param service The service ID used to make X-Amz-Target, and the namespace is used when finding the
     *                discriminator of documents that use relative shape IDs.
     */
    public AwsJsonProtocol(ShapeId trait, ShapeId service) {
        super(trait.toString());
        this.service = service;

        this.errorDeserializer = HttpErrorDeserializer.builder()
            .codec(codec)
            .serviceId(service)
            .headerErrorExtractor(new AmznErrorHeaderExtractor())
            .build();
    }

    protected abstract String contentType();

    @Override
    public <I extends SerializableStruct, O extends SerializableStruct> SmithyHttpRequest createRequest(
        ApiOperation<I, O> operation,
        I input,
        Context context,
        URI endpoint
    ) {
        var target = service.getName() + "." + operation.schema().id().getName();
        var builder = SmithyHttpRequest.builder();
        builder.method("POST");
        builder.uri(endpoint);
        builder.headers(
            HttpHeaders.of(
                Map.of(
                    "x-amz-target",
                    List.of(target),
                    "content-type",
                    List.of(contentType())
                )
            )
        );

        return builder.body(DataStream.ofByteBuffer(codec.serialize(input), contentType())).build();
    }

    @Override
    public <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> deserializeResponse(
        ApiOperation<I, O> operation,
        Context context,
        TypeRegistry typeRegistry,
        SmithyHttpRequest request,
        SmithyHttpResponse response
    ) {
        // Is it an error?
        if (response.statusCode() != 200) {
            return errorDeserializer.createError(context, operation.schema().id(), typeRegistry, response)
                .thenApply(e -> {
                    throw e;
                });
        }

        var builder = operation.outputBuilder();
        var content = response.body();

        // If the payload is empty, then use "{}" as the default empty payload.
        if (content.contentLength() == 0) {
            return CompletableFuture.completedFuture(codec.deserializeShape(EMPTY_PAYLOAD, builder));
        }

        return content.asByteBuffer().thenApply(bytes -> codec.deserializeShape(bytes, builder));
    }
}
