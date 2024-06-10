/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.net.http.HttpHeaders;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeDeserializer;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyBuilder;

/**
 * Generic HTTP binding deserializer that delegates to another ShapeDeserializer when members are encountered that
 * form a protocol-specific body.
 *
 * <p>This deserializer requires that a top-level structure shape is deserialized and will throw an
 * UnsupportedOperationException if any other kind of shape is first read from it.
 */
final class HttpBindingDeserializer extends SpecificShapeDeserializer implements ShapeDeserializer {

    private static final System.Logger LOGGER = System.getLogger(HttpBindingDeserializer.class.getName());
    private final Codec payloadCodec;
    private final HttpHeaders headers;
    private final String requestRawQueryString;
    private final int responseStatus;
    private final String requestPath;
    private final Map<String, String> requestPathLabels;
    private final BindingMatcher bindingMatcher;
    private final DataStream body;
    private final SdkShapeBuilder<?> shapeBuilder;
    private CompletableFuture<Void> bodyDeserializationCf;

    private HttpBindingDeserializer(Builder builder) {
        this.shapeBuilder = Objects.requireNonNull(builder.shapeBuilder, "shapeBuilder not set");
        this.payloadCodec = Objects.requireNonNull(builder.payloadCodec, "payloadSerializer not set");
        this.headers = Objects.requireNonNull(builder.headers, "headers not set");
        this.bindingMatcher = builder.isRequest ? BindingMatcher.requestMatcher() : BindingMatcher.responseMatcher();
        this.body = builder.body == null ? DataStream.ofEmpty() : builder.body;
        this.requestPath = builder.requestPath;
        this.requestRawQueryString = builder.requestRawQueryString;
        this.responseStatus = builder.responseStatus;
        this.requestPathLabels = builder.requestPathLabels;
    }

    static Builder builder() {
        return new Builder();
    }

    @Override
    protected RuntimeException throwForInvalidState(SdkSchema schema) {
        throw new IllegalStateException("Expected to parse a structure for HTTP bindings, but found " + schema);
    }

    @Override
    public <T> void readStruct(SdkSchema schema, T state, StructMemberConsumer<T> structMemberConsumer) {
        BodyMembersList<T> bodyMembers = new BodyMembersList<>(state, structMemberConsumer);

        // First parse members in the framing.
        for (SdkSchema member : schema.members()) {
            switch (bindingMatcher.match(member)) {
                case LABEL -> throw new UnsupportedOperationException("httpLabel binding not supported yet");
                case QUERY -> throw new UnsupportedOperationException("httpQuery binding not supported yet");
                case HEADER -> {
                    var headerValue = headers.firstValue(bindingMatcher.header()).orElse(null);
                    if (headerValue != null) {
                        structMemberConsumer.accept(state, member, new HttpHeaderDeserializer(headerValue));
                    }
                }
                case BODY -> bodyMembers.add(member.id().getName());
                case PAYLOAD -> {
                    if (member.memberTarget().type() == ShapeType.STRUCTURE) {
                        // Read the payload into a byte buffer to deserialize a shape in the body.
                        LOGGER.log(
                            System.Logger.Level.TRACE,
                            () -> "Reading " + schema + " body to bytes for structured payload"
                        );
                        bodyDeserializationCf = bodyAsBytes().thenAccept(bytes -> {
                            LOGGER.log(
                                System.Logger.Level.TRACE,
                                () -> "Deserializing the payload of " + schema + " via " + payloadCodec.getMediaType()
                            );
                            structMemberConsumer.accept(state, member, payloadCodec.createDeserializer(bytes));
                        }).toCompletableFuture();
                    } else if (member.memberTarget().type() == ShapeType.BLOB) {
                        // Set the payload on shape builder directly. This will fail for misconfigured shapes.
                        shapeBuilder.setDataStream(body);
                    } else {
                        // TODO: shapeBuilder.setEventStream(EventStream.of(body));
                        throw new UnsupportedOperationException("Not yet supported");
                    }
                }
            }
        }

        // Now parse members in the payload of body.
        if (!bodyMembers.isEmpty()) {
            validateMediaType();
            // Need to read the entire payload into a byte buffer to deserialize via a codec.
            bodyDeserializationCf = bodyAsBytes().thenAccept(bytes -> {
                LOGGER.log(
                    System.Logger.Level.TRACE,
                    () -> "Deserializing the structured body of " + schema + " via " + payloadCodec.getMediaType()
                );
                payloadCodec.createDeserializer(bytes).readStruct(schema, bodyMembers, (body, m, de) -> {
                    if (!body.contains(m.id().getName())) {
                        body.structMemberConsumer.accept(body.state, m, de);
                    }
                });
            }).toCompletableFuture();
        }
    }

    // TODO: Should there be a configurable limit on the client/server for how much can be read in memory?
    private CompletionStage<byte[]> bodyAsBytes() {
        return body.asBytes();
    }

    CompletableFuture<Void> completeBodyDeserialization() {
        if (bodyDeserializationCf == null) {
            return CompletableFuture.completedFuture(null);
        }
        return bodyDeserializationCf;
    }

    /**
     * Data class that contains the set of members that need to be serialized in the body, and the delegated
     * deserializer and state to invoke when found.
     */
    private static final class BodyMembersList<T> extends HashSet<String> {
        private final StructMemberConsumer<T> structMemberConsumer;
        private final T state;

        BodyMembersList(T state, StructMemberConsumer<T> structMemberConsumer) {
            this.state = state;
            this.structMemberConsumer = structMemberConsumer;
        }
    }

    private void validateMediaType() {
        // Validate the media-type matches the codec.
        String contentType = headers.firstValue("content-type").orElse("");
        if (!contentType.equals(payloadCodec.getMediaType())) {
            throw new SdkSerdeException("Unexpected Content-Type '" + contentType + "' for protocol " + payloadCodec);
        }
    }

    static final class Builder implements SmithyBuilder<HttpBindingDeserializer> {
        private Map<String, String> requestPathLabels;
        private Codec payloadCodec;
        private boolean isRequest;
        private HttpHeaders headers;
        private String requestRawQueryString;
        private DataStream body;
        private String requestPath;
        private int responseStatus;
        private SdkShapeBuilder<?> shapeBuilder;

        private Builder() {
        }

        @Override
        public HttpBindingDeserializer build() {
            return new HttpBindingDeserializer(this);
        }

        /**
         * Set the captured, already percent-decoded, labels for the operation.
         *
         * <p>This builder assumes an operation has already been matched by a framework, which means HTTP label
         * bindings have already been extracted.
         *
         * @param requestPathLabels Captured request labels.
         * @return Returns the builder.
         */
        Builder requestPathLabels(Map<String, String> requestPathLabels) {
            this.requestPathLabels = requestPathLabels;
            return this;
        }

        /**
         * Set the codec used to deserialize the body if necessary.
         *
         * @param payloadCodec Payload codec.
         * @return Returns the builder.
         */
        Builder payloadCodec(Codec payloadCodec) {
            this.payloadCodec = payloadCodec;
            return this;
        }

        /**
         * Set HTTP headers to use when deserializing the message.
         *
         * @param headers HTTP headers to set.
         * @return Returns the builder.
         */
        Builder headers(HttpHeaders headers) {
            this.headers = Objects.requireNonNull(headers);
            return this;
        }

        /**
         * Set the raw query string of the request.
         *
         * <p>The query string should be percent-encoded and include any relevant parameters.
         * For example, "foo=bar&baz=bam%20boo".
         *
         * @param requestRawQueryString Query string.
         * @return Returns the builder.
         */
        Builder requestRawQueryString(String requestRawQueryString) {
            if (!isRequest) {
                throw new IllegalStateException("Cannot set rawQueryString for a response");
            }
            this.requestRawQueryString = requestRawQueryString;
            return this;
        }

        /**
         * Set the body of the message, if any.
         *
         * @param body Payload to deserialize.
         * @return Returns the builder.
         */
        Builder body(DataStream body) {
            this.body = body;
            return this;
        }

        /**
         * Set the raw path of a request as received on the wire.
         *
         * @param requestPath Path of the request.
         * @return Returns the path.
         */
        Builder requestPath(String requestPath) {
            if (!isRequest) {
                throw new IllegalStateException("Cannot set path for a response");
            }
            this.requestPath = requestPath;
            return this;
        }

        /**
         * Set the HTTP status code of a response.
         *
         * @param responseStatus Status to set.
         * @return Returns the builder.
         */
        Builder responseStatus(int responseStatus) {
            if (isRequest) {
                throw new IllegalStateException("Cannot set status for a request");
            }
            this.responseStatus = responseStatus;
            return this;
        }

        /**
         * Set to true to honor request bindings.
         *
         * @param isRequest Set to true to use request bindings.
         * @return Returns the builder.
         */
        Builder request(boolean isRequest) {
            this.isRequest = isRequest;
            return this;
        }

        /**
         * Set the shape builder that is being created.
         *
         * @param shapeBuilder Shape builder to create a shape.
         * @return the builder.
         */
        Builder shapeBuilder(SdkShapeBuilder<?> shapeBuilder) {
            this.shapeBuilder = shapeBuilder;
            return this;
        }
    }
}
