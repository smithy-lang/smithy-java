/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.MemberSubsetCodec;
import software.amazon.smithy.java.core.serde.RuntimeCodegenMode;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.event.EventStream;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.ModifiableHttpHeaders;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.QueryStringBuilder;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Generic HTTP binding serializer that delegates to another ShapeSerializer when members are encountered that form
 * a protocol-specific body.
 *
 * <p>This serializer requires that a top-level structure shape is written and will throw an
 * UnsupportedOperationException if any other kind of shape is first written to it.
 */
final class HttpBindingSerializer extends SpecificShapeSerializer implements ShapeSerializer {

    private static final String DEFAULT_BLOB_CONTENT_TYPE = "application/octet-stream";
    private static final String DEFAULT_STRING_CONTENT_TYPE = "text/plain";

    private final Codec payloadCodec;
    // Preserves the concrete struct type for codecs that support member subsets.
    private final MemberSubsetCodec subsetCodec;
    private final String payloadMediaType;
    private final boolean omitEmptyPayload;
    private final boolean isFailure;
    private final boolean allowEmptyStructPayload;
    private final HeaderErrorSerializer headerErrorSerializer;
    private final Context context;
    private ModifiableHttpHeaders headers;
    private QueryStringBuilder queryStringParams;

    // Stashed during {@link #writeStruct} so {@link #getPath} can read label members directly.
    private SerializableStruct outerStruct;
    // Direction-specific Binding[] (request or response), cached at writeStruct time so
    // each per-member dispatch in {@link BindingSerializer} does a single array load
    // instead of a ternary + getter chain.
    private HttpBindingSchemaExtensions.Binding[] activeBindings;
    // Direction-neutral MemberBinding[] indexed by memberIndex, cached at writeStruct
    // time. Used by the BindingSerializer's HEADER and QUERY arms.
    private HttpBindingSchemaExtensions.MemberBinding[] memberBindings;
    // Path serializer cached at writeStruct time for the request direction. Null on the response direction.
    private PathSerializer pathSerializer;
    // Initialized in writeStruct. Empty until writeStruct is called.
    private Set<String> namesFromHttpHeader = Set.of();

    // Body bytes from the pooled codec.serialize() path.
    private ByteBuffer shapeBodyBuffer;
    private DataStream httpPayload;
    private EventStream<? extends SerializableStruct> eventStream;
    private Schema payloadMember;
    private PayloadSerializer payloadSerializer;
    private Schema payloadSchema;
    private int responseStatus;
    private boolean contentTypeHeaderInInput;

    private final boolean isResponse;
    private final RuntimeCodegenMode runtimeCodegen;
    private final HttpBindingSchemaExtensions.OperationBinding operationBinding;

    HttpBindingSerializer(
            HttpBindingSchemaExtensions.OperationBinding operationBinding,
            Codec payloadCodec,
            String payloadMediaType,
            boolean isResponse,
            boolean omitEmptyPayload,
            boolean isFailure,
            boolean allowEmptyStructPayload,
            HeaderErrorSerializer headerErrorSerializer,
            Context context,
            RuntimeCodegenMode runtimeCodegen
    ) {
        this.operationBinding = operationBinding;
        this.runtimeCodegen = runtimeCodegen;
        responseStatus = operationBinding.defaultResponseStatus();
        this.payloadCodec = payloadCodec;
        this.subsetCodec = payloadCodec instanceof MemberSubsetCodec c ? c : null;
        this.isResponse = isResponse;
        this.payloadMediaType = payloadMediaType;
        this.omitEmptyPayload = omitEmptyPayload;
        this.isFailure = isFailure;
        this.allowEmptyStructPayload = allowEmptyStructPayload;
        this.headerErrorSerializer = headerErrorSerializer;
        this.context = context;
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        var bindings = HttpBindingSchemaExtensions.structBindingsOf(schema);
        outerStruct = struct;

        boolean writeBody;
        boolean hasBodyMembers;
        int headerCount;
        // Generated writers cast to the schema's concrete shape class.
        boolean canGenerate = runtimeCodegen != RuntimeCodegenMode.DISABLED && struct.schema() == schema;
        if (runtimeCodegen == RuntimeCodegenMode.STRICT && !canGenerate) {
            throw new IllegalStateException(
                    "Strict HTTP binding runtime codegen requires the value's schema to be the operation schema: "
                            + struct.schema().id()
                            + " != "
                            + schema.id());
        }
        boolean strictCodegen = runtimeCodegen == RuntimeCodegenMode.STRICT;
        HttpBindingWriter generatedWriter;
        if (isResponse) {
            var resp = bindings.response();
            generatedWriter = canGenerate ? resp.bindingWriter(schema, strictCodegen) : null;
            activeBindings = resp.bindings;
            memberBindings = resp.memberBindings;
            namesFromHttpHeader = resp.headerWireNames;
            payloadMember = resp.payloadMember;
            headerCount = resp.headerCount;
            hasBodyMembers = resp.hasBody;
            if (resp.defaultStatus != -1) {
                responseStatus = resp.defaultStatus;
            }
            writeBody = allowEmptyStructPayload || resp.writeBody(omitEmptyPayload);
            if (writeBody && !hasBodyMembers) {
                // No body members: fixed empty struct bytes, cached on the binding.
                shapeBodyBuffer = resp.emptyBody(payloadCodec, schema);
            }
        } else {
            var req = bindings.request();
            generatedWriter = canGenerate ? req.bindingWriter(schema, strictCodegen) : null;
            activeBindings = req.bindings;
            memberBindings = req.memberBindings;
            namesFromHttpHeader = req.headerWireNames;
            payloadMember = req.payloadMember;
            headerCount = req.headerCount;
            hasBodyMembers = req.hasBody;
            contentTypeHeaderInInput = req.inputContentTypeHeader;
            pathSerializer = req.pathSerializer(operationBinding.httpTrait(), schema);
            writeBody = allowEmptyStructPayload || req.writeBody(omitEmptyPayload);
            if (writeBody && !hasBodyMembers) {
                shapeBodyBuffer = req.emptyBody(payloadCodec, schema);
            }
        }

        headers = HttpHeaders.ofModifiable(headerCount);

        // Append the static @http URI query literals
        String[] qKeys = operationBinding.queryLiteralKeys();
        if (qKeys.length > 0) {
            QueryStringBuilder qsb = queryStringParams();
            String[] qEntries = operationBinding.queryLiteralEntries();
            for (int i = 0; i < qKeys.length; i++) {
                qsb.addPreEncoded(qKeys[i], qEntries[i]);
            }
        }

        if (writeBody) {
            if (hasBodyMembers) {
                shapeBodyBuffer = serializeBody(schema, struct);
            }
            // Empty-body case (shapeBodyBuffer was set above from the cached empty bytes).
            headers.setHeader(HeaderName.CONTENT_TYPE, payloadMediaType);
        }

        if (isFailure) {
            prepareError(schema);
        }

        if (generatedWriter != null) {
            generatedWriter.write(struct, this);
        } else {
            struct.serializeMembers(new BindingSerializer());
        }
        flushPayload();
    }

    void bindHeader(String name, String value) {
        if (value != null) {
            headers.addHeaderCanonical(name, value);
        }
    }

    // Only values produced by the writer may bypass header validation.
    void bindHeaderTrusted(String name, String value) {
        if (value != null) {
            headers.addHeaderTrusted(name, value);
        }
    }

    void bindQuery(String name, String value) {
        queryStringParams().add(name, value);
    }

    void bindPrefixHeaders(String prefix, Map<?, ?> values) {
        for (var entry : values.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || value == null) {
                bindNull();
            }
            String headerName = prefix + (String) key;
            // A statically named @httpHeader member takes precedence over a prefix-header entry.
            if (!namesFromHttpHeader.contains(headerName)) {
                headers.addHeader(headerName, (String) value);
            }
        }
    }

    void bindQueryParams(Map<?, ?> values) {
        QueryStringBuilder builder = queryStringParams();
        for (var entry : values.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || value == null) {
                bindNull();
            }
            String name = (String) key;
            if (value instanceof List<?> list) {
                for (Object element : list) {
                    if (element == null) {
                        bindNull();
                    }
                    builder.addForQueryParams(name, (String) element);
                }
            } else {
                builder.addForQueryParams(name, (String) value);
            }
        }
    }

    void bindStatus(int status) {
        responseStatus = status;
    }

    void bindPayload(String value) {
        payload(payloadMember).writeString(payloadMember, value);
    }

    void bindPayload(ByteBuffer value) {
        payload(payloadMember).writeBlob(payloadMember, value);
    }

    void bindPayload(byte[] value) {
        payload(payloadMember).writeBlob(payloadMember, value);
    }

    void bindPayload(Instant value) {
        payload(payloadMember).writeTimestamp(payloadMember, value);
    }

    void bindPayload(SerializableStruct value) {
        payload(payloadMember).writeStruct(payloadMember, value);
    }

    void bindPayload(Document value) {
        payload(payloadMember).writeDocument(payloadMember, value);
    }

    void bindPayload(DataStream value) {
        setHttpPayload(payloadMember, value);
    }

    void bindPayload(EventStream<? extends SerializableStruct> value) {
        setEventStream(value);
    }

    void bindNull() {
        throw new IllegalStateException("Unexpected null value written for an HTTP binding");
    }

    private ByteBuffer serializeBody(Schema schema, SerializableStruct struct) {
        if (subsetCodec != null && struct.schema() == schema) {
            ByteBuffer body = subsetCodec.serialize(struct, BodyMemberSubset.of(isResponse));
            if (body != null) {
                return body;
            }
        }
        return payloadCodec.serialize(new StructBodyProxy(struct, activeBindings));
    }

    private void prepareError(Schema schema) {
        responseStatus = ModeledException.getHttpStatusCode(schema);
        // Adapt the ArrayHttpHeaders to the legacy Map<String,List<String>> contract for HeaderErrorSerializer.
        Map<String, List<String>> errorView = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.forEachEntry(errorView, (ev, name, value) -> {
            ev.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        });
        headerErrorSerializer.writeErrorType(schema, errorView, context);
        // Replace the header set with the (possibly modified) view.
        headers.clear();
        headers.placeHeaders(errorView);
    }

    @Override
    public void flush() {
        // Body bytes are produced eagerly by payloadCodec.serialize(); nothing to flush here.
    }

    void setHttpPayload(Schema schema, DataStream value) {
        httpPayload = value;
        if (headers.hasHeader(HeaderName.CONTENT_TYPE) || contentTypeHeaderInInput) {
            return;
        }

        String contentType;
        var mediaType = schema.getTrait(TraitKey.MEDIA_TYPE_TRAIT);
        if (mediaType != null) {
            contentType = mediaType.getValue();
        } else {
            contentType = value.contentType();
            if (contentType == null) {
                contentType = schema.type() == ShapeType.BLOB
                        ? DEFAULT_BLOB_CONTENT_TYPE
                        : DEFAULT_STRING_CONTENT_TYPE;
            }
        }
        headers.setHeader(HeaderName.CONTENT_TYPE, contentType);
    }

    HttpHeaders getHeaders() {
        return headers;
    }

    String getQueryString() {
        return queryStringParams == null ? "" : queryStringParams.toString();
    }

    boolean hasQueryString() {
        return queryStringParams != null && !queryStringParams.isEmpty();
    }

    // Lazy accessor for {@link #queryStringParams}.
    private QueryStringBuilder queryStringParams() {
        if (queryStringParams == null) {
            queryStringParams = new QueryStringBuilder();
        }
        return queryStringParams;
    }

    boolean hasBody() {
        return shapeBodyBuffer != null || httpPayload != null;
    }

    DataStream getBody() {
        if (httpPayload != null) {
            return httpPayload;
        } else if (shapeBodyBuffer != null) {
            return DataStream.ofByteBuffer(shapeBodyBuffer, payloadMediaType);
        } else {
            return DataStream.ofEmpty();
        }
    }

    String getPath() {
        return pathSerializer.serialize(outerStruct);
    }

    int getResponseStatus() {
        return responseStatus;
    }

    public EventStream<? extends SerializableStruct> getEventStream() {
        return eventStream;
    }

    void setEventStream(EventStream<? extends SerializableStruct> stream) {
        this.eventStream = stream;
    }

    void writePayloadContentType() {
        setContentType(payloadMediaType);
    }

    public void setContentType(String contentType) {
        headers.setHeader(HeaderName.CONTENT_TYPE, contentType);
    }

    private PayloadSerializer payload(Schema schema) {
        if (payloadSerializer == null) {
            payloadSerializer = new PayloadSerializer(this, payloadCodec);
            payloadSchema = schema;
        }
        return payloadSerializer;
    }

    private void flushPayload() {
        if (payloadSerializer != null && !payloadSerializer.isPayloadWritten()) {
            payloadSerializer.flush();
            setHttpPayload(payloadSchema, DataStream.ofByteBuffer(payloadSerializer.toByteBuffer()));
        }
    }

    @SuppressWarnings("resource")
    private final class BindingSerializer extends SpecificShapeSerializer {
        @Override
        public void writeBoolean(Schema schema, boolean value) {
            int idx = schema.memberIndex();
            switch (activeBindings[idx]) {
                case HEADER -> writeHeader(memberBindings[idx], Boolean.toString(value));
                case QUERY -> writeQuery(memberBindings[idx], Boolean.toString(value));
                case PAYLOAD -> payload(schema).writeBoolean(schema, value);
                default -> {
                }
            }
        }

        @Override
        public void writeShort(Schema schema, short value) {
            int idx = schema.memberIndex();
            switch (activeBindings[idx]) {
                case HEADER -> writeHeader(memberBindings[idx], Short.toString(value));
                case QUERY -> writeQuery(memberBindings[idx], Short.toString(value));
                case STATUS -> responseStatus = value;
                case PAYLOAD -> payload(schema).writeShort(schema, value);
                default -> {
                }
            }
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            int idx = schema.memberIndex();
            switch (activeBindings[idx]) {
                case HEADER -> writeHeader(memberBindings[idx], Byte.toString(value));
                case QUERY -> writeQuery(memberBindings[idx], Byte.toString(value));
                case STATUS -> responseStatus = value;
                case PAYLOAD -> payload(schema).writeByte(schema, value);
                default -> {
                }
            }
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            int idx = schema.memberIndex();
            switch (activeBindings[idx]) {
                case HEADER -> writeHeader(memberBindings[idx], Integer.toString(value));
                case QUERY -> writeQuery(memberBindings[idx], Integer.toString(value));
                case STATUS -> responseStatus = value;
                case PAYLOAD -> payload(schema).writeInteger(schema, value);
                default -> {
                }
            }
        }

        @Override
        public void writeLong(Schema schema, long value) {
            int idx = schema.memberIndex();
            switch (activeBindings[idx]) {
                case HEADER -> writeHeader(memberBindings[idx], Long.toString(value));
                case QUERY -> writeQuery(memberBindings[idx], Long.toString(value));
                case PAYLOAD -> payload(schema).writeLong(schema, value);
                default -> {
                }
            }
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            int idx = schema.memberIndex();
            switch (activeBindings[idx]) {
                case HEADER -> writeHeader(memberBindings[idx], Float.toString(value));
                case QUERY -> writeQuery(memberBindings[idx], Float.toString(value));
                case PAYLOAD -> payload(schema).writeFloat(schema, value);
                default -> {
                }
            }
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            int idx = schema.memberIndex();
            switch (activeBindings[idx]) {
                case HEADER -> writeHeader(memberBindings[idx], Double.toString(value));
                case QUERY -> writeQuery(memberBindings[idx], Double.toString(value));
                case PAYLOAD -> payload(schema).writeDouble(schema, value);
                default -> {
                }
            }
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            int idx = schema.memberIndex();
            switch (activeBindings[idx]) {
                case HEADER -> writeHeader(memberBindings[idx], value.toString());
                case QUERY -> writeQuery(memberBindings[idx], value.toString());
                case PAYLOAD -> payload(schema).writeBigInteger(schema, value);
                default -> {
                }
            }
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            int idx = schema.memberIndex();
            switch (activeBindings[idx]) {
                case HEADER -> writeHeader(memberBindings[idx], value.toString());
                case QUERY -> writeQuery(memberBindings[idx], value.toString());
                case PAYLOAD -> payload(schema).writeBigDecimal(schema, value);
                default -> {
                }
            }
        }

        @Override
        public void writeString(Schema schema, String value) {
            int idx = schema.memberIndex();
            switch (activeBindings[idx]) {
                case HEADER -> {
                    var binding = memberBindings[idx];
                    var v = binding.hasMediaType()
                            ? Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8))
                            : value;
                    writeHeader(binding, v);
                }
                case QUERY -> writeQuery(memberBindings[idx], value);
                case PAYLOAD -> payload(schema).writeString(schema, value);
                default -> {
                }
            }
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            int idx = schema.memberIndex();
            switch (activeBindings[idx]) {
                case HEADER -> writeHeader(memberBindings[idx], ByteBufferUtils.base64Encode(value));
                case QUERY -> writeQuery(memberBindings[idx], ByteBufferUtils.base64Encode(value));
                case PAYLOAD -> payload(schema).writeBlob(schema, value);
                default -> {
                }
            }
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            int idx = schema.memberIndex();
            switch (activeBindings[idx]) {
                case HEADER -> {
                    var binding = memberBindings[idx];
                    writeHeader(binding, binding.timestampFormatter().writeString(value));
                }
                case QUERY -> {
                    var binding = memberBindings[idx];
                    writeQuery(binding, binding.timestampFormatter().writeString(value));
                }
                case PAYLOAD -> payload(schema).writeTimestamp(schema, value);
                default -> {
                }
            }
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            if (activeBindings[schema.memberIndex()] == HttpBindingSchemaExtensions.Binding.PAYLOAD) {
                payload(schema).writeStruct(schema, struct);
            }
        }

        @Override
        public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
            int idx = schema.memberIndex();
            switch (activeBindings[idx]) {
                case HEADER ->
                    consumer.accept(listState, new HeaderListElementSerializer(memberBindings[idx]));
                case QUERY ->
                    consumer.accept(listState, new QueryListElementSerializer(memberBindings[idx]));
                case PAYLOAD -> payload(schema).writeList(schema, listState, size, consumer);
                default -> {
                }
            }
        }

        @Override
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            int idx = schema.memberIndex();
            switch (activeBindings[idx]) {
                case PREFIX_HEADERS -> {
                    var prefix = memberBindings[idx].wireName();
                    new HttpPrefixHeadersSerializer(prefix, headers::addHeader, namesFromHttpHeader)
                            .writeMap(schema, mapState, size, consumer);
                }
                case QUERY_PARAMS -> new HttpQueryParamsSerializer(queryStringParams()::addForQueryParams)
                        .writeMap(schema, mapState, size, consumer);
                case PAYLOAD -> payload(schema).writeMap(schema, mapState, size, consumer);
                default -> {
                }
            }
        }

        @Override
        public void writeDataStream(Schema schema, DataStream value) {
            if (activeBindings[schema.memberIndex()] == HttpBindingSchemaExtensions.Binding.PAYLOAD) {
                setHttpPayload(schema, value);
            }
        }

        @Override
        public void writeEventStream(Schema schema, EventStream<? extends SerializableStruct> value) {
            if (activeBindings[schema.memberIndex()] == HttpBindingSchemaExtensions.Binding.PAYLOAD) {
                setEventStream(value);
            }
        }

        @Override
        public void writeNull(Schema schema) {
            // Nulls are dropped from the wire for every binding kind; nothing to do.
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            if (activeBindings[schema.memberIndex()] == HttpBindingSchemaExtensions.Binding.PAYLOAD) {
                payload(schema).writeDocument(schema, value);
            }
        }

        private void writeHeader(HttpBindingSchemaExtensions.MemberBinding binding, String value) {
            if (value != null) {
                headers.addHeader(binding.headerName(), value);
            }
        }

        private void writeQuery(HttpBindingSchemaExtensions.MemberBinding binding, String value) {
            queryStringParams().add(binding.wireName(), value);
        }

        /**
         * Per-element serializer for list-typed {@code @httpHeader} members.
         * Each element becomes a separate header value carrying the parent member's canonicalized header name.
         */
        private final class HeaderListElementSerializer extends SpecificShapeSerializer {
            private final HttpBindingSchemaExtensions.MemberBinding binding;

            HeaderListElementSerializer(HttpBindingSchemaExtensions.MemberBinding binding) {
                this.binding = binding;
            }

            @Override
            public void writeBoolean(Schema schema, boolean value) {
                writeHeader(binding, Boolean.toString(value));
            }

            @Override
            public void writeShort(Schema schema, short value) {
                writeHeader(binding, Short.toString(value));
            }

            @Override
            public void writeByte(Schema schema, byte value) {
                writeHeader(binding, Byte.toString(value));
            }

            @Override
            public void writeInteger(Schema schema, int value) {
                writeHeader(binding, Integer.toString(value));
            }

            @Override
            public void writeLong(Schema schema, long value) {
                writeHeader(binding, Long.toString(value));
            }

            @Override
            public void writeFloat(Schema schema, float value) {
                writeHeader(binding, Float.toString(value));
            }

            @Override
            public void writeDouble(Schema schema, double value) {
                writeHeader(binding, Double.toString(value));
            }

            @Override
            public void writeBigInteger(Schema schema, BigInteger value) {
                writeHeader(binding, value.toString());
            }

            @Override
            public void writeBigDecimal(Schema schema, BigDecimal value) {
                writeHeader(binding, value.toString());
            }

            @Override
            public void writeString(Schema schema, String value) {
                var v = binding.hasMediaType()
                        ? Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8))
                        : value;
                writeHeader(binding, v);
            }

            @Override
            public void writeBlob(Schema schema, ByteBuffer value) {
                writeHeader(binding, ByteBufferUtils.base64Encode(value));
            }

            @Override
            public void writeTimestamp(Schema schema, Instant value) {
                writeHeader(binding, binding.timestampFormatter().writeString(value));
            }
        }

        /**
         * Per-element serializer for list-typed {@code @httpQuery} members.
         */
        private final class QueryListElementSerializer extends SpecificShapeSerializer {
            private final HttpBindingSchemaExtensions.MemberBinding binding;

            QueryListElementSerializer(HttpBindingSchemaExtensions.MemberBinding binding) {
                this.binding = binding;
            }

            @Override
            public void writeBoolean(Schema schema, boolean value) {
                writeQuery(binding, Boolean.toString(value));
            }

            @Override
            public void writeShort(Schema schema, short value) {
                writeQuery(binding, Short.toString(value));
            }

            @Override
            public void writeByte(Schema schema, byte value) {
                writeQuery(binding, Byte.toString(value));
            }

            @Override
            public void writeInteger(Schema schema, int value) {
                writeQuery(binding, Integer.toString(value));
            }

            @Override
            public void writeLong(Schema schema, long value) {
                writeQuery(binding, Long.toString(value));
            }

            @Override
            public void writeFloat(Schema schema, float value) {
                writeQuery(binding, Float.toString(value));
            }

            @Override
            public void writeDouble(Schema schema, double value) {
                writeQuery(binding, Double.toString(value));
            }

            @Override
            public void writeBigInteger(Schema schema, BigInteger value) {
                writeQuery(binding, value.toString());
            }

            @Override
            public void writeBigDecimal(Schema schema, BigDecimal value) {
                writeQuery(binding, value.toString());
            }

            @Override
            public void writeString(Schema schema, String value) {
                writeQuery(binding, value);
            }

            @Override
            public void writeTimestamp(Schema schema, Instant value) {
                writeQuery(binding, binding.timestampFormatter().writeString(value));
            }
        }
    }
}
