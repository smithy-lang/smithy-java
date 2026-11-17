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
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
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

    private DataStream shapeBody;
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
                shapeBody = DataStream.ofByteBuffer(resp.emptyBody(payloadCodec, schema), payloadMediaType);
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
                shapeBody = DataStream.ofByteBuffer(req.emptyBody(payloadCodec, schema), payloadMediaType);
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
                shapeBody = serializeBody(schema, struct);
            }
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

    private DataStream serializeBody(Schema schema, SerializableStruct struct) {
        if (isResponse) {
            // Server response bodies have no lease owner. Return an unpooled copy.
            return DataStream.ofByteBuffer(serializeBodyBuffer(schema, struct), payloadMediaType);
        }
        if (subsetCodec != null && struct.schema() == schema) {
            DataStream body =
                    subsetCodec.serializeToDataStream(struct, BodyMemberSubset.of(false), payloadMediaType);
            if (body != null) {
                return body;
            }
        }
        return payloadCodec.serializeToDataStream(new StructBodyProxy(struct, activeBindings), payloadMediaType);
    }

    private ByteBuffer serializeBodyBuffer(Schema schema, SerializableStruct struct) {
        if (subsetCodec != null && struct.schema() == schema) {
            ByteBuffer body = subsetCodec.serialize(struct, BodyMemberSubset.of(true));
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
        return shapeBody != null || httpPayload != null;
    }

    DataStream getBody() {
        if (httpPayload != null) {
            return httpPayload;
        } else if (shapeBody != null) {
            return shapeBody;
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

        @Override
        public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeBooleanList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        s.writeBoolean(memberSchema, v);
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseBooleanList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        if (v == null) {
                            s.writeNull(memberSchema);
                        } else {
                            s.writeBoolean(memberSchema, v);
                        }
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeBooleanMap(Schema schema, Map<String, Boolean> values, Schema keySchema, Schema valueSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeBooleanMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeBoolean(valueSchema, vv));
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeSparseBooleanMap(
                Schema schema,
                Map<String, Boolean> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseBooleanMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> {
                            if (vv == null) {
                                s.writeNull(valueSchema);
                            } else {
                                s.writeBoolean(valueSchema, vv);
                            }
                        });
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeByteList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        s.writeByte(memberSchema, v);
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseByteList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        if (v == null) {
                            s.writeNull(memberSchema);
                        } else {
                            s.writeByte(memberSchema, v);
                        }
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeByteMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeByte(valueSchema, vv));
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeSparseByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseByteMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> {
                            if (vv == null) {
                                s.writeNull(valueSchema);
                            } else {
                                s.writeByte(valueSchema, vv);
                            }
                        });
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeShortList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        s.writeShort(memberSchema, v);
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseShortList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        if (v == null) {
                            s.writeNull(memberSchema);
                        } else {
                            s.writeShort(memberSchema, v);
                        }
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeShortMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeShort(valueSchema, vv));
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeSparseShortMap(
                Schema schema,
                Map<String, Short> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseShortMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> {
                            if (vv == null) {
                                s.writeNull(valueSchema);
                            } else {
                                s.writeShort(valueSchema, vv);
                            }
                        });
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeIntegerList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        s.writeInteger(memberSchema, v);
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseIntegerList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        if (v == null) {
                            s.writeNull(memberSchema);
                        } else {
                            s.writeInteger(memberSchema, v);
                        }
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeIntegerMap(Schema schema, Map<String, Integer> values, Schema keySchema, Schema valueSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeIntegerMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeInteger(valueSchema, vv));
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeSparseIntegerMap(
                Schema schema,
                Map<String, Integer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseIntegerMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> {
                            if (vv == null) {
                                s.writeNull(valueSchema);
                            } else {
                                s.writeInteger(valueSchema, vv);
                            }
                        });
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeLongList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        s.writeLong(memberSchema, v);
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseLongList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        if (v == null) {
                            s.writeNull(memberSchema);
                        } else {
                            s.writeLong(memberSchema, v);
                        }
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeLongMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeLong(valueSchema, vv));
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeSparseLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseLongMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> {
                            if (vv == null) {
                                s.writeNull(valueSchema);
                            } else {
                                s.writeLong(valueSchema, vv);
                            }
                        });
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeFloatList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        s.writeFloat(memberSchema, v);
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseFloatList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        if (v == null) {
                            s.writeNull(memberSchema);
                        } else {
                            s.writeFloat(memberSchema, v);
                        }
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeFloatMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeFloat(valueSchema, vv));
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeSparseFloatMap(
                Schema schema,
                Map<String, Float> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseFloatMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> {
                            if (vv == null) {
                                s.writeNull(valueSchema);
                            } else {
                                s.writeFloat(valueSchema, vv);
                            }
                        });
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeDoubleList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        s.writeDouble(memberSchema, v);
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseDoubleList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        if (v == null) {
                            s.writeNull(memberSchema);
                        } else {
                            s.writeDouble(memberSchema, v);
                        }
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeDoubleMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeDouble(valueSchema, vv));
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeSparseDoubleMap(
                Schema schema,
                Map<String, Double> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseDoubleMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> {
                            if (vv == null) {
                                s.writeNull(valueSchema);
                            } else {
                                s.writeDouble(valueSchema, vv);
                            }
                        });
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeBigIntegerList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        s.writeBigInteger(memberSchema, v);
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseBigIntegerList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        if (v == null) {
                            s.writeNull(memberSchema);
                        } else {
                            s.writeBigInteger(memberSchema, v);
                        }
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeBigIntegerMap(
                Schema schema,
                Map<String, BigInteger> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeBigIntegerMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeBigInteger(valueSchema, vv));
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeSparseBigIntegerMap(
                Schema schema,
                Map<String, BigInteger> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseBigIntegerMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> {
                            if (vv == null) {
                                s.writeNull(valueSchema);
                            } else {
                                s.writeBigInteger(valueSchema, vv);
                            }
                        });
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeBigDecimalList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        s.writeBigDecimal(memberSchema, v);
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseBigDecimalList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        if (v == null) {
                            s.writeNull(memberSchema);
                        } else {
                            s.writeBigDecimal(memberSchema, v);
                        }
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeBigDecimalMap(
                Schema schema,
                Map<String, BigDecimal> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeBigDecimalMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeBigDecimal(valueSchema, vv));
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeSparseBigDecimalMap(
                Schema schema,
                Map<String, BigDecimal> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseBigDecimalMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> {
                            if (vv == null) {
                                s.writeNull(valueSchema);
                            } else {
                                s.writeBigDecimal(valueSchema, vv);
                            }
                        });
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeStringList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        s.writeString(memberSchema, v);
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseStringList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        if (v == null) {
                            s.writeNull(memberSchema);
                        } else {
                            s.writeString(memberSchema, v);
                        }
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeStringMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeString(valueSchema, vv));
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeSparseStringMap(
                Schema schema,
                Map<String, String> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseStringMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> {
                            if (vv == null) {
                                s.writeNull(valueSchema);
                            } else {
                                s.writeString(valueSchema, vv);
                            }
                        });
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeBlobList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        s.writeBlob(memberSchema, v);
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseBlobList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        if (v == null) {
                            s.writeNull(memberSchema);
                        } else {
                            s.writeBlob(memberSchema, v);
                        }
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeBlobMap(Schema schema, Map<String, ByteBuffer> values, Schema keySchema, Schema valueSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeBlobMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeBlob(valueSchema, vv));
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeSparseBlobMap(
                Schema schema,
                Map<String, ByteBuffer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseBlobMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> {
                            if (vv == null) {
                                s.writeNull(valueSchema);
                            } else {
                                s.writeBlob(valueSchema, vv);
                            }
                        });
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeTimestampList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        s.writeTimestamp(memberSchema, v);
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseTimestampList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        if (v == null) {
                            s.writeNull(memberSchema);
                        } else {
                            s.writeTimestamp(memberSchema, v);
                        }
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeTimestampMap(
                Schema schema,
                Map<String, Instant> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeTimestampMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeTimestamp(valueSchema, vv));
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeSparseTimestampMap(
                Schema schema,
                Map<String, Instant> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseTimestampMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> {
                            if (vv == null) {
                                s.writeNull(valueSchema);
                            } else {
                                s.writeTimestamp(valueSchema, vv);
                            }
                        });
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeDocumentList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        s.writeDocument(memberSchema, v);
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeSparseDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseDocumentList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        if (v == null) {
                            s.writeNull(memberSchema);
                        } else {
                            s.writeDocument(memberSchema, v);
                        }
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeDocumentMap(
                Schema schema,
                Map<String, Document> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeDocumentMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeDocument(valueSchema, vv));
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeSparseDocumentMap(
                Schema schema,
                Map<String, Document> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseDocumentMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> {
                            if (vv == null) {
                                s.writeNull(valueSchema);
                            } else {
                                s.writeDocument(valueSchema, vv);
                            }
                        });
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeEnumList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        s.writeString(memberSchema, v.getValue());
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeSparseEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseEnumList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        if (v == null) {
                            s.writeNull(memberSchema);
                        } else {
                            s.writeString(memberSchema, v.getValue());
                        }
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeEnumMap(
                Schema schema,
                Map<String, ? extends SmithyEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeEnumMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeString(valueSchema, vv.getValue()));
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeSparseEnumMap(
                Schema schema,
                Map<String, ? extends SmithyEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseEnumMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> {
                            if (vv == null) {
                                s.writeNull(valueSchema);
                            } else {
                                s.writeString(valueSchema, vv.getValue());
                            }
                        });
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeIntEnumList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        s.writeInteger(memberSchema, v.getValue());
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeSparseIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseIntEnumList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        if (v == null) {
                            s.writeNull(memberSchema);
                        } else {
                            s.writeInteger(memberSchema, v.getValue());
                        }
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeIntEnumMap(
                Schema schema,
                Map<String, ? extends SmithyIntEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeIntEnumMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeInteger(valueSchema, vv.getValue()));
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeSparseIntEnumMap(
                Schema schema,
                Map<String, ? extends SmithyIntEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseIntEnumMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> {
                            if (vv == null) {
                                s.writeNull(valueSchema);
                            } else {
                                s.writeInteger(valueSchema, vv.getValue());
                            }
                        });
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeStructList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        s.writeStruct(memberSchema, v);
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeSparseStructList(
                Schema schema,
                List<? extends SerializableStruct> values,
                Schema memberSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseStructList(schema, values, memberSchema);
                case HEADER, QUERY -> writeList(schema, values, values.size(), (list, s) -> {
                    for (var v : list) {
                        if (v == null) {
                            s.writeNull(memberSchema);
                        } else {
                            s.writeStruct(memberSchema, v);
                        }
                    }
                });
                default -> {
                }
            }
        }

        @Override
        public void writeStructMap(
                Schema schema,
                Map<String, ? extends SerializableStruct> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeStructMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeStruct(valueSchema, vv));
                    }));
                default -> {
                }
            }
        }

        @Override
        public void writeSparseStructMap(
                Schema schema,
                Map<String, ? extends SerializableStruct> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            switch (activeBindings[schema.memberIndex()]) {
                case PAYLOAD -> payload(schema).writeSparseStructMap(schema, values, keySchema, valueSchema);
                case PREFIX_HEADERS, QUERY_PARAMS ->
                    writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                        ms.writeEntry(keySchema, k, v, (vv, s) -> {
                            if (vv == null) {
                                s.writeNull(valueSchema);
                            } else {
                                s.writeStruct(valueSchema, vv);
                            }
                        });
                    }));
                default -> {
                }
            }
        }
    }
}
