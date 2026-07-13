/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.SpecificShapeDeserializer;
import software.amazon.smithy.java.core.serde.event.EventDecoderFactory;
import software.amazon.smithy.java.core.serde.event.EventStreamReader;
import software.amazon.smithy.java.core.serde.event.ProtocolEventStreamReader;
import software.amazon.smithy.java.http.api.HeaderName;
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
    private final boolean isResponse;
    private final DataStream body;
    private final EventDecoderFactory<?> eventDecoderFactory;
    private final String payloadMediaType;

    private HttpBindingDeserializer(Builder builder) {
        this.payloadCodec = Objects.requireNonNull(builder.payloadCodec, "payloadSerializer not set");
        this.headers = Objects.requireNonNull(builder.headers, "headers not set");
        this.isResponse = builder.isResponse;
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
        var ext = schema.getExtension(HttpBindingSchemaExtensions.KEY);
        if (!(ext instanceof HttpBindingSchemaExtensions.StructBindings sb)) {
            // Schema is not a structure / union — fall back to the legacy
            // generic path so we still throw the same UnsupportedOperationException.
            throw new IllegalStateException("Expected to parse a structure for HTTP bindings, but found " + schema);
        }

        if (isResponse) {
            readResponseStruct(schema, state, structMemberConsumer, sb.response());
        } else {
            readRequestStruct(schema, state, structMemberConsumer, sb.request());
        }
    }

    private <T> void readRequestStruct(
            Schema schema,
            T state,
            StructMemberConsumer<T> structMemberConsumer,
            HttpBindingSchemaExtensions.RequestBinding rb
    ) {
        readHeaderBindings(
                state,
                structMemberConsumer,
                rb.listHeaderMembers,
                rb.listHeaderNames,
                rb.scalarHeadersByName,
                rb.prefixHeadersMembers);

        for (Schema member : rb.labelMembers) {
            var labelValue = requestPathLabels.get(member.memberName());
            if (labelValue == null) {
                throw new IllegalStateException(
                        "Expected a label value for " + member.memberName() + " but it was null.");
            }
            structMemberConsumer.accept(state, member, new HttpPathLabelDeserializer(labelValue));
        }

        for (int i = 0; i < rb.queryMembers.length; i++) {
            var paramValue = queryStringParameters.get(rb.queryWireNames[i]);
            if (paramValue != null) {
                Schema member = rb.queryMembers[i];
                structMemberConsumer.accept(state, member, new HttpQueryStringDeserializer(paramValue));
            }
        }

        for (Schema member : rb.queryParamsMembers) {
            structMemberConsumer.accept(state, member, new HttpQueryParamsDeserializer(queryStringParameters));
        }

        readPayloadAndBody(schema, state, structMemberConsumer, rb.payloadMember, rb.hasBody, rb.bindings);
    }

    private <T> void readResponseStruct(
            Schema schema,
            T state,
            StructMemberConsumer<T> structMemberConsumer,
            HttpBindingSchemaExtensions.ResponseBinding rb
    ) {
        readHeaderBindings(
                state,
                structMemberConsumer,
                rb.listHeaderMembers,
                rb.listHeaderNames,
                rb.scalarHeadersByName,
                rb.prefixHeadersMembers);

        if (rb.statusMember != null) {
            structMemberConsumer.accept(state, rb.statusMember, new ResponseStatusDeserializer(responseStatus));
        }

        readPayloadAndBody(schema, state, structMemberConsumer, rb.payloadMember, rb.hasBody, rb.bindings);
    }

    /**
     * Process the three header-binding kinds (list, scalar, prefix). Common to both
     * request and response directions — both bindings expose the same field shapes.
     */
    private <T> void readHeaderBindings(
            T state,
            StructMemberConsumer<T> structMemberConsumer,
            Schema[] listHeaderMembers,
            HeaderName[] listHeaderNames,
            Map<String, Schema> scalarHeadersByName,
            Schema[] prefixHeadersMembers
    ) {
        for (int i = 0; i < listHeaderMembers.length; i++) {
            var values = headers.allValues(listHeaderNames[i]);
            if (!values.isEmpty()) {
                Schema member = listHeaderMembers[i];
                structMemberConsumer.accept(state, member, new HttpHeaderListDeserializer(member, values));
            }
        }

        if (!scalarHeadersByName.isEmpty()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            HeaderDispatchContext ctx = new HeaderDispatchContext(
                    scalarHeadersByName,
                    state,
                    (StructMemberConsumer) structMemberConsumer);
            headers.forEachEntry(ctx, HEADER_DISPATCHER);
        }

        for (Schema member : prefixHeadersMembers) {
            structMemberConsumer.accept(state, member, new HttpPrefixHeadersDeserializer(headers));
        }
    }

    /**
     * Process the @httpPayload member (if any) and the codec-driven body. Common to
     * both request and response directions.
     */
    private <T> void readPayloadAndBody(
            Schema schema,
            T state,
            StructMemberConsumer<T> structMemberConsumer,
            Schema payloadMember,
            boolean hasBody,
            HttpBindingSchemaExtensions.Binding[] bindings
    ) {
        if (payloadMember != null) {
            handlePayload(payloadMember, state, structMemberConsumer);
        }
        if (hasBody) {
            readBody(schema, state, structMemberConsumer, bindings);
        }
    }

    private <T> void readBody(
            Schema schema,
            T state,
            StructMemberConsumer<T> structMemberConsumer,
            HttpBindingSchemaExtensions.Binding[] bindings
    ) {
        validateMediaType();
        ByteBuffer bb = bodyAsByteBuffer();
        // The codec's readStruct callback receives every member; filter to body members using the direction-specific
        // bindings array (which was already chosen by the caller).
        payloadCodec.createDeserializer(bb).readStruct(schema, bindings, (body, m, de) -> {
            if (body[m.memberIndex()] == HttpBindingSchemaExtensions.Binding.BODY) {
                structMemberConsumer.accept(state, m, de);
            }
        });
    }

    private <T> void handlePayload(Schema member, T state, StructMemberConsumer<T> structMemberConsumer) {
        if (isEventStream(member)) {
            structMemberConsumer.accept(state, member, new SpecificShapeDeserializer() {
                @Override
                public EventStreamReader<? extends SerializableStruct> readEventStream(Schema schema) {
                    return ProtocolEventStreamReader.newReader(body, eventDecoderFactory, false);
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
        } else if (member.type() == ShapeType.STRUCTURE || member.type() == ShapeType.UNION
                || member.type() == ShapeType.LIST) {
            // Read the payload into a byte buffer to deserialize a shape in the body.
            ByteBuffer bb = bodyAsByteBuffer();
            if (bb.remaining() > 0) {
                structMemberConsumer.accept(state, member, payloadCodec.createDeserializer(bb));
            }
        } else if (body != null && body.contentLength() != 0) {
            structMemberConsumer.accept(state, member, new PayloadDeserializer(payloadCodec, body));
        }
    }

    private static boolean isEventStream(Schema member) {
        return member.type() == ShapeType.UNION && member.hasTrait(TraitKey.STREAMING_TRAIT);
    }

    // TODO: Should there be a configurable limit on the client/server for how much can be read in memory?
    private ByteBuffer bodyAsByteBuffer() {
        return body.asByteBuffer();
    }

    private void validateMediaType() {
        if (compareMediaType(headers.contentType(), payloadMediaType) == -1) {
            throw new SerializationException(
                    "Unexpected Content-Type '" + headers.contentType() + "' for protocol " + payloadCodec);
        }
    }

    /**
     * Compares an actual media type against an expected media type.
     *
     * <p>Media type comparison is case-insensitive. The actual media type is considered a match if it
     * starts with the expected media type and is either an exact match or is followed by a semicolon
     * (parameter separator) or whitespace.
     *
     * @param actual   the actual media type received (e.g., from a Content-Type header), or null
     * @param expected the expected media type, or null to skip validation
     * @return 1 if the media types match or no validation is needed because `expected` is null,
     * 0 if actual is null but expected is not (missing Content-Type),
     * -1 if the media types do not match
     */
    static int compareMediaType(String actual, String expected) {
        if (expected == null) {
            return 1; // No validation needed
        } else if (actual == null) {
            return 0; // Expected something, got nothing. Accepts by default in HttpBindingDeserializer.
        }

        // Check if actual media type starts with expected, optionally followed by parameters
        // Media types are essentially ASCII (RFC 6838#section-4.2), so locale-dependent case folding with
        // regionMatches is safe.
        int len = expected.length();
        if (actual.regionMatches(true, 0, expected, 0, len)) {
            int actualLen = actual.length();
            if (actualLen == len) {
                return 1;
            }
            // Check if followed by parameter separator or whitespace
            char next = actual.charAt(len);
            if (next == ';' || Character.isWhitespace(next)) {
                return 1;
            }
        }

        return -1;
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
        private boolean isResponse;

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

        Builder isResponse(boolean isResponse) {
            this.isResponse = isResponse;
            return this;
        }
    }

    /**
     * Carrier passed to {@link HttpHeaders#forEachEntry(Object, HttpHeaders.HeaderWithValueConsumer)}
     * so the per-pair callback doesn't allocate a capturing lambda per {@code readStruct} call. The fields hold
     * everything the dispatcher needs to route a matching header value.
     */
    private record HeaderDispatchContext(
            Map<String, Schema> byName,
            Object state,
            StructMemberConsumer<Object> consumer) {}

    /**
     * Stateless dispatcher: looks up a header name in the context's name->schema map and emits a
     * {@link HttpHeaderDeserializer} for the matching member.
     * <p>
     * Allocated once and reused across all {@code readStruct} invocations.
     */
    private static final HttpHeaders.HeaderWithValueConsumer<HeaderDispatchContext> HEADER_DISPATCHER =
            (ctx, name, value) -> {
                Schema member = ctx.byName.get(name);
                if (member != null) {
                    ctx.consumer.accept(ctx.state, member, new HttpHeaderDeserializer(value));
                }
            };
}
