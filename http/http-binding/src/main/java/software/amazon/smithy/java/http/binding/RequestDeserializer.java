/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.event.EventDecoderFactory;
import software.amazon.smithy.java.core.serde.event.Frame;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Deserializes the HTTP request of an operation that uses HTTP bindings into a builder.
 */
public final class RequestDeserializer {

    private final HttpBindingDeserializer.Builder deserBuilder = HttpBindingDeserializer.builder();
    private ShapeBuilder<?> inputShapeBuilder;
    private final ConcurrentMap<Schema, BindingMatcher> bindingCache;

    RequestDeserializer(ConcurrentMap<Schema, BindingMatcher> bindingCache) {
        this.bindingCache = bindingCache;
    }

    /**
     * Codec to use in the payload of requests.
     *
     * @param payloadCodec Payload codec.
     * @return Returns the deserializer.
     */
    public RequestDeserializer payloadCodec(Codec payloadCodec) {
        deserBuilder.payloadCodec(payloadCodec);
        return this;
    }

    /**
     * Set the expected media type to be used when a payload is deserialized.
     *
     * <p>If a media type is provided, then this deserializer will validate that the media type on the wire matches
     * the expected media type. If no media type is provided, then this deserializer will perform no validation
     * prior to attempting to parse the request payload with the codec.
     *
     * @param payloadMediaType Media type used with payloads.
     * @return Returns the deserializer.
     */
    public RequestDeserializer payloadMediaType(String payloadMediaType) {
        deserBuilder.payloadMediaType(payloadMediaType);
        return this;
    }

    /**
     * HTTP request to deserialize.
     *
     * @param request Request to deserialize into the builder.
     * @return Returns the deserializer.
     */
    public RequestDeserializer request(HttpRequest request) {
        DataStream bodyDataStream = request.body();
        deserBuilder.headers(request.headers())
                .requestRawQueryString(request.uri().getRawQuery())
                .body(bodyDataStream);
        return this;
    }

    /**
     * Input shape builder to populate from the request.
     *
     * @param inputShapeBuilder Output shape builder.
     * @return Returns the deserializer.
     */
    public RequestDeserializer inputShapeBuilder(ShapeBuilder<?> inputShapeBuilder) {
        this.inputShapeBuilder = inputShapeBuilder;
        return this;
    }

    /**
     * Enables input event decoding.
     *
     * @param eventDecoderFactory event decoding support
     * @return Returns the deserializer.
     */
    public <F extends Frame<?>> RequestDeserializer eventDecoderFactory(EventDecoderFactory<F> eventDecoderFactory) {
        deserBuilder.eventDecoderFactory(eventDecoderFactory);
        return this;
    }

    public RequestDeserializer pathLabelValues(Map<String, String> labelValues) {
        deserBuilder.requestPathLabels(labelValues);
        return this;
    }

    /**
     * Finish setting up and deserialize the response into the builder.
     */
    public CompletableFuture<Void> deserialize() {
        if (inputShapeBuilder == null) {
            throw new IllegalStateException("inputShapeBuilder must be set");
        }

        var matcher = bindingCache.computeIfAbsent(inputShapeBuilder.schema(), BindingMatcher::requestMatcher);
        deserBuilder.bindingMatcher(matcher);
        HttpBindingDeserializer deserializer = deserBuilder.build();

        inputShapeBuilder.deserialize(deserializer);
        return deserializer.completeBodyDeserialization();
    }
}
