/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.io.IOException;
import java.io.UncheckedIOException;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.event.EventDecoderFactory;
import software.amazon.smithy.java.core.serde.event.Frame;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Deserializes the HTTP response of an operation that uses HTTP bindings into a builder.
 */
public final class ResponseDeserializer {

    private final HttpBindingDeserializer.Builder deserBuilder = HttpBindingDeserializer.builder();
    private ShapeBuilder<?> outputShapeBuilder;
    private ShapeBuilder<? extends ModeledException> errorShapeBuilder;
    private DataStream responseBody;

    ResponseDeserializer() {}

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
    public ResponseDeserializer response(HttpResponse response) {
        DataStream bodyDataStream = bodyDataStream(response);
        responseBody = bodyDataStream;
        deserBuilder.headers(response.headers())
                .responseStatus(response.statusCode())
                .body(bodyDataStream);
        return this;
    }

    private DataStream bodyDataStream(HttpResponse response) {
        var contentType = response.headers().contentType();
        var contentLength = response.headers().contentLength();
        return DataStream.withMetadata(response.body(), contentType, contentLength == null ? -1 : contentLength);
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
    public ResponseDeserializer errorShapeBuilder(ShapeBuilder<? extends ModeledException> errorShapeBuilder) {
        this.errorShapeBuilder = errorShapeBuilder;
        outputShapeBuilder = null;
        return this;
    }

    /**
     * Finish setting up and deserialize the response into the builder.
     */
    public void deserialize() {
        if (outputShapeBuilder == null && errorShapeBuilder == null) {
            throw new IllegalStateException("Either errorShapeBuilder or outputShapeBuilder must be set");
        }

        deserBuilder.isResponse(true);

        HttpBindingDeserializer deserializer = deserBuilder.build();
        var target = outputShapeBuilder != null ? outputShapeBuilder : errorShapeBuilder;
        Throwable failure = null;
        try {
            target.deserialize(deserializer);
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            if (!hasStreamingPayload(target.schema())) {
                discardResponseBody(failure);
            }
        }
    }

    private static boolean hasStreamingPayload(Schema schema) {
        var ext = schema.getExtension(HttpBindingSchemaExtensions.KEY);
        if (ext instanceof HttpBindingSchemaExtensions.StructBindings sb) {
            var payloadMember = sb.response().payloadMember;
            return payloadMember != null && payloadMember.hasTrait(TraitKey.STREAMING_TRAIT);
        }
        return false;
    }

    private void discardResponseBody(Throwable failure) {
        if (responseBody != null) {
            try {
                responseBody.discard();
            } catch (IOException e) {
                var wrapped = new UncheckedIOException(e);
                if (failure == null) {
                    throw wrapped;
                }
                failure.addSuppressed(wrapped);
            }
        }
    }
}
