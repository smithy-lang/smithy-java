/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.TraitKey;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.event.EventDecoderFactory;
import software.amazon.smithy.java.runtime.core.serde.event.EventStreamFrameDecodingProcessor;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;
import software.amazon.smithy.java.runtime.io.uri.QueryStringParser;
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

    private final Codec payloadCodec;
    private final HttpHeaders headers;
    private final Map<String, List<String>> queryStringParameters;
    private final int responseStatus;
    private final Map<String, String> requestPathLabels;
    private final BindingMatcher bindingMatcher;
    private final DataStream body;
    private final EventDecoderFactory<?> eventDecoderFactory;
    private CompletableFuture<Void> bodyDeserializationCf;
    private final String payloadMediaType;

    private HttpBindingDeserializer(Builder builder) {
        this.payloadCodec = Objects.requireNonNull(builder.payloadCodec, "payloadSerializer not set");
        this.headers = Objects.requireNonNull(builder.headers, "headers not set");
        this.bindingMatcher = Objects.requireNonNull(builder.bindingMatcher, "bindingMatcher not set");
        this.eventDecoderFactory = builder.eventDecoderFactory;
        this.body = builder.body == null ? DataStream.ofEmpty() : builder.body;
        this.queryStringParameters = QueryStringParser.parse(builder.requestRawQueryString);
        this.responseStatus = builder.responseStatus;
        this.requestPathLabels = builder.requestPathLabels;
        this.payloadMediaType = builder.payloadMediaType;
    }

    static Builder builder() {
        return new Builder();
    }

    @Override
    protected RuntimeException throwForInvalidState(Schema schema) {
        throw new IllegalStateException("Expected to parse a structure for HTTP bindings, but found " + schema);
    }

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> structMemberConsumer) {
        BodyMembersList<T> bodyMembers = new BodyMembersList<>(state, structMemberConsumer);

        // First parse members in the framing.
        for (Schema member : schema.members()) {
            BindingMatcher.Binding bindingLoc = bindingMatcher.match(member);
            switch (bindingLoc) {
                case LABEL -> {
                    var labelValue = requestPathLabels.get(member.memberName());
                    if (labelValue == null) {
                        throw new IllegalStateException(
                            "Expected a label value for " + member.memberName()
                                + " but it was null."
                        );
                    }
                    structMemberConsumer.accept(
                        state,
                        member,
                        new HttpPathLabelDeserializer(labelValue)
                    );
                }
                case QUERY -> {
                    var paramValue = queryStringParameters.get(
                        member.expectTrait(TraitKey.HTTP_QUERY_TRAIT).getValue()
                    );
                    if (paramValue != null) {
                        structMemberConsumer.accept(state, member, new HttpQueryStringDeserializer(paramValue));
                    }
                }
                case QUERY_PARAMS ->
                    structMemberConsumer.accept(state, member, new HttpQueryParamsDeserializer(queryStringParameters));
                case HEADER -> {
                    var header = member.expectTrait(TraitKey.HTTP_HEADER_TRAIT).getValue();
                    if (member.type() == ShapeType.LIST) {
                        var values = headers.allValues(header);
                        if (!values.isEmpty()) {
                            structMemberConsumer.accept(state, member, new HttpHeaderListDeserializer(member, values));
                        }
                    } else {
                        headers.firstValue(header)
                            .ifPresent(
                                headerValue -> structMemberConsumer.accept(
                                    state,
                                    member,
                                    new HttpHeaderDeserializer(headerValue)
                                )
                            );
                    }
                }
                case PREFIX_HEADERS ->
                    structMemberConsumer.accept(state, member, new HttpPrefixHeadersDeserializer(headers));
                case BODY -> bodyMembers.add(member.memberName());
                case PAYLOAD -> {
                    if (member.type() == ShapeType.STRUCTURE) {
                        // Read the payload into a byte buffer to deserialize a shape in the body.
                        bodyDeserializationCf = bodyAsByteBuffer().thenAccept(bb -> {
                            structMemberConsumer.accept(state, member, payloadCodec.createDeserializer(bb));
                        }).toCompletableFuture();
                    } else if (isEventStream(member)) {
                        structMemberConsumer.accept(state, member, new SpecificShapeDeserializer() {
                            @Override
                            public Flow.Publisher<? extends SerializableStruct> readEventStream(Schema schema) {
                                return EventStreamFrameDecodingProcessor.create(body, eventDecoderFactory);
                            }
                        });
                    } else if (member.hasTrait(TraitKey.STREAMING_TRAIT)) {
                        // Set the payload on shape builder directly. This will fail for misconfigured shapes.
                        structMemberConsumer.accept(state, member, new SpecificShapeDeserializer() {
                            @Override
                            public DataStream readDataStream(Schema schema) {
                                return body;
                            }
                        });
                    } else if (body != null && body.contentLength() > 0) {
                        structMemberConsumer.accept(state, member, new PayloadDeserializer(payloadCodec, body));
                    }
                }
                case STATUS -> {
                    structMemberConsumer.accept(state, member, new ResponseStatusDeserializer(responseStatus));
                }
                default -> throw new UnsupportedOperationException(bindingLoc + " not supported");
            }
        }

        // Now parse members in the payload of body.
        if (!bodyMembers.isEmpty()) {
            validateMediaType();
            // Need to read the entire payload into a byte buffer to deserialize via a codec.
            bodyDeserializationCf = bodyAsByteBuffer().thenAccept(bb -> {
                payloadCodec.createDeserializer(bb).readStruct(schema, bodyMembers, (body, m, de) -> {
                    if (body.contains(m.memberName())) {
                        body.structMemberConsumer.accept(body.state, m, de);
                    }
                });
            });
        }
    }

    private static boolean isEventStream(Schema member) {
        return member.type() == ShapeType.UNION && member.hasTrait(TraitKey.STREAMING_TRAIT);
    }

    // TODO: Should there be a configurable limit on the client/server for how much can be read in memory?
    private CompletableFuture<ByteBuffer> bodyAsByteBuffer() {
        return body.asByteBuffer();
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
        if (payloadMediaType != null && headers.firstValue("content-type").isPresent()) {
            // Validate the media-type matches the codec.
            String contentType = headers.firstValue("content-type").get();
            if (!contentType.equals(payloadMediaType)) {
                throw new SerializationException(
                    "Unexpected Content-Type '" + contentType + "' for protocol " + payloadCodec
                );
            }
        }
    }

    static final class Builder implements SmithyBuilder<HttpBindingDeserializer> {
        private Map<String, String> requestPathLabels;
        private Codec payloadCodec;
        private HttpHeaders headers;
        private String requestRawQueryString;
        private DataStream body;
        private int responseStatus;
        private EventDecoderFactory<?> eventDecoderFactory;
        private String payloadMediaType;
        private BindingMatcher bindingMatcher;

        private Builder() {}

        @Override
        public HttpBindingDeserializer build() {
            return new HttpBindingDeserializer(this);
        }

        /**
         * Set the captured, already percent-decoded, labels for the operation.
         *
         * <p>This builder assumes an operation has already been matched by the framework, which means HTTP label
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
         * Set the HTTP status code of a response.
         *
         * @param responseStatus Status to set.
         * @return Returns the builder.
         */
        Builder responseStatus(int responseStatus) {
            this.responseStatus = responseStatus;
            return this;
        }

        Builder eventDecoderFactory(EventDecoderFactory<?> eventDecoderFactory) {
            this.eventDecoderFactory = eventDecoderFactory;
            return this;
        }

        Builder payloadMediaType(String payloadMediaType) {
            this.payloadMediaType = payloadMediaType;
            return this;
        }

        Builder bindingMatcher(BindingMatcher bindingMatcher) {
            this.bindingMatcher = bindingMatcher;
            return this;
        }
    }
}
