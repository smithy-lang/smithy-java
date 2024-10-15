/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.event.EventDecoderFactory;
import software.amazon.smithy.java.runtime.core.serde.event.Frame;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;

/**
 * Deserializes the HTTP response of an operation that uses HTTP bindings into a builder.
 */
public final class ResponseDeserializer {

    private final HttpBindingDeserializer.Builder deserBuilder = HttpBindingDeserializer.builder();
    private ShapeBuilder<?> outputShapeBuilder;
    private ShapeBuilder<? extends ModeledApiException> errorShapeBuilder;
    private final ConcurrentMap<Schema, BindingMatcher> bindingCache;

    ResponseDeserializer(ConcurrentMap<Schema, BindingMatcher> bindingCache) {
        this.bindingCache = bindingCache;
    }

    /**
     * Codec to use in the payload of responses.
     *
     * @param payloadCodec Payload codec.
     * @return Returns the deserializer.
     */
    public ResponseDeserializer payloadCodec(Codec payloadCodec) {
        deserBuilder.payloadCodec(payloadCodec);
        return this;
    }

    /**
     * Set the expected media type to be used when a payload is deserialized.
     *
     * <p>If a media type is provided, then this deserializer will validate that the media type on the wire matches
     * the expected media type. If no media type is provided, then this deserializer will perform no validation
     * prior to attempting to parse the response payload with the codec.
     *
     * @param payloadMediaType Media type of the payload.
     * @return the deserializer.
     */
    public ResponseDeserializer payloadMediaType(String payloadMediaType) {
        deserBuilder.payloadMediaType(payloadMediaType);
        return this;
    }

    /**
     * HTTP response to deserialize.
     *
     * @param response Response to deserialize into the builder.
     * @return Returns the deserializer.
     */
    public ResponseDeserializer response(SmithyHttpResponse response) {
        DataStream bodyDataStream = bodyDataStream(response);
        deserBuilder.headers(response.headers())
            .responseStatus(response.statusCode())
            .body(bodyDataStream);
        return this;
    }

    private DataStream bodyDataStream(SmithyHttpResponse response) {
        var contentType = response.headers().firstValue("content-type").orElse(null);
        var contentLength = response.headers().firstValue("content-length").map(Long::valueOf).orElse(-1L);
        return DataStream.ofPublisher(response.body(), contentType, contentLength);
    }

    /**
     * Output shape builder to populate from the response.
     *
     * @param outputShapeBuilder Output shape builder.
     * @return Returns the deserializer.
     */
    public ResponseDeserializer outputShapeBuilder(ShapeBuilder<?> outputShapeBuilder) {
        this.outputShapeBuilder = outputShapeBuilder;
        errorShapeBuilder = null;
        return this;
    }

    /**
     * Enables output event decoding.
     *
     * @param eventDecoderFactory event decoding support
     * @return Returns the deserializer.
     */
    public <F extends Frame<?>> ResponseDeserializer eventDecoderFactory(EventDecoderFactory<F> eventDecoderFactory) {
        deserBuilder.eventDecoderFactory(eventDecoderFactory);
        return this;
    }

    /**
     * Error shape builder to populate from the response.
     *
     * @param errorShapeBuilder Error shape builder.
     * @return Returns the deserializer.
     */
    public ResponseDeserializer errorShapeBuilder(ShapeBuilder<? extends ModeledApiException> errorShapeBuilder) {
        this.errorShapeBuilder = errorShapeBuilder;
        outputShapeBuilder = null;
        return this;
    }

    /**
     * Finish setting up and deserialize the response into the builder.
     */
    public CompletableFuture<Void> deserialize() {
        Schema schema;
        if (outputShapeBuilder != null) {
            schema = outputShapeBuilder.schema();
        } else if (errorShapeBuilder != null) {
            schema = errorShapeBuilder.schema();
        } else {
            throw new IllegalStateException("Either errorShapeBuilder or outputShapeBuilder must be set");
        }

        var matcher = bindingCache.computeIfAbsent(schema, BindingMatcher::responseMatcher);
        deserBuilder.bindingMatcher(matcher);

        HttpBindingDeserializer deserializer = deserBuilder.build();

        if (outputShapeBuilder != null) {
            outputShapeBuilder.deserialize(deserializer);
        } else {
            errorShapeBuilder.deserialize(deserializer);
        }

        return deserializer.completeBodyDeserialization();
    }
}
