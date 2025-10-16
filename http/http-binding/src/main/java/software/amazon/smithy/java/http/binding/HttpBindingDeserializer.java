/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.SpecificShapeDeserializer;
import software.amazon.smithy.java.core.serde.event.EventDecoderFactory;
import software.amazon.smithy.java.core.serde.event.EventStreamFrameDecodingProcessor;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.QueryStringParser;
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
        // First parse members in the framing.
        for (Schema member : schema.members()) {
            BindingMatcher.Binding bindingLoc = bindingMatcher.match(member);
            switch (bindingLoc) {
                case LABEL -> {
                    var labelValue = requestPathLabels.get(member.memberName());
                    if (labelValue == null) {
                        throw new IllegalStateException(
                                "Expected a label value for " + member.memberName()
                                        + " but it was null.");
                    }
                    structMemberConsumer.accept(
                            state,
                            member,
                            new HttpPathLabelDeserializer(labelValue));
                }
                case QUERY -> {
                    var paramValue = queryStringParameters.get(
                            member.expectTrait(TraitKey.HTTP_QUERY_TRAIT).getValue());
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
                        var headerValue = headers.firstValue(header);
                        if (headerValue != null) {
                            structMemberConsumer.accept(state, member, new HttpHeaderDeserializer(headerValue));
                        }
                    }
                }
                case PREFIX_HEADERS ->
                    structMemberConsumer.accept(state, member, new HttpPrefixHeadersDeserializer(headers));
                case BODY -> {
                } // handled below
                case PAYLOAD -> {
                    if (isEventStream(member)) {
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
                    } else if (member.type() == ShapeType.STRUCTURE || member.type() == ShapeType.UNION) {
                        // Read the payload into a byte buffer to deserialize a shape in the body.
                        ByteBuffer bb = bodyAsByteBuffer();
                        if (bb.remaining() > 0) {
                            structMemberConsumer.accept(state, member, payloadCodec.createDeserializer(bb));
                        }
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
        if (bindingMatcher.hasBody()) {
            validateMediaType();
            // Need to read the entire payload into a byte buffer to deserialize via a codec.
            ByteBuffer bb = bodyAsByteBuffer();
            payloadCodec.createDeserializer(bb).readStruct(schema, bindingMatcher, (body, m, de) -> {
                if (bindingMatcher.match(m) == BindingMatcher.Binding.BODY) {
                    structMemberConsumer.accept(state, m, de);
                }
            });
        }
    }

    private static boolean isEventStream(Schema member) {
        return member.type() == ShapeType.UNION && member.hasTrait(TraitKey.STREAMING_TRAIT);
    }

    // TODO: Should there be a configurable limit on the client/server for how much can be read in memory?
    private ByteBuffer bodyAsByteBuffer() {
        return body.waitForByteBuffer();
    }

    private void validateMediaType() {
        var contentType = headers.contentType();
        if (payloadMediaType != null && contentType != null) {
            // Validate the media-type matches the codec.
            if (!contentType.equals(payloadMediaType)) {
                throw new SerializationException(
                        "Unexpected Content-Type '" + contentType + "' for protocol " + payloadCodec);
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
