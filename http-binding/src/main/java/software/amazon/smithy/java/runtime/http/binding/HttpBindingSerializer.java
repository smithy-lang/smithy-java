/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.concurrent.Flow;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.TraitKey;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.runtime.http.api.HttpHeaders;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;
import software.amazon.smithy.java.runtime.io.uri.QueryStringBuilder;
import software.amazon.smithy.java.runtime.io.uri.URLEncoding;
import software.amazon.smithy.model.pattern.SmithyPattern;
import software.amazon.smithy.model.pattern.UriPattern;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.HttpTrait;

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

    private final ShapeSerializer headerSerializer;
    private final ShapeSerializer querySerializer;
    private final ShapeSerializer labelSerializer;
    private final Codec payloadCodec;
    private final String payloadMediaType;
    private final boolean omitEmptyPayload;

    private final Map<String, String> labels = new LinkedHashMap<>();
    private final Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final QueryStringBuilder queryStringParams = new QueryStringBuilder();

    private ShapeSerializer shapeBodySerializer;
    private ByteArrayOutputStream shapeBodyOutput;
    private DataStream httpPayload;
    private Flow.Publisher<? extends SerializableStruct> eventStream;
    private int responseStatus;

    private final BindingMatcher bindingMatcher;
    private final UriPattern uriPattern;
    private final BiConsumer<String, String> headerConsumer = (field, value) -> headers.computeIfAbsent(
        field,
        f -> new ArrayList<>()
    ).add(value);

    HttpBindingSerializer(
        HttpTrait httpTrait,
        Codec payloadCodec,
        String payloadMediaType,
        BindingMatcher bindingMatcher,
        boolean omitEmptyPayload
    ) {
        uriPattern = httpTrait.getUri();
        responseStatus = httpTrait.getCode();
        this.payloadCodec = payloadCodec;
        this.bindingMatcher = bindingMatcher;
        this.payloadMediaType = payloadMediaType;
        this.omitEmptyPayload = omitEmptyPayload;
        headerSerializer = new HttpHeaderSerializer(headerConsumer);
        querySerializer = new HttpQuerySerializer(queryStringParams::add);
        labelSerializer = new HttpLabelSerializer(labels::put);
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        if (schema.hasTrait(TraitKey.HTTP_ERROR_TRAIT)) {
            responseStatus = schema.expectTrait(TraitKey.HTTP_ERROR_TRAIT).getCode();
        } else if (schema.hasTrait(TraitKey.ERROR_TRAIT)) {
            responseStatus = schema.expectTrait(TraitKey.ERROR_TRAIT).getDefaultHttpStatusCode();
        }

        boolean foundBody = false;
        boolean foundPayload = false;
        for (var member : schema.members()) {
            var bindingLoc = bindingMatcher.match(member);
            if (bindingLoc == BindingMatcher.Binding.BODY) {
                foundBody = true;
                break;
            }
            if (bindingLoc == BindingMatcher.Binding.PAYLOAD) {
                foundPayload = true;
                break;
            }
        }

        if (foundBody || (!omitEmptyPayload && !foundPayload)) {
            shapeBodyOutput = new ByteArrayOutputStream();
            shapeBodySerializer = payloadCodec.createSerializer(shapeBodyOutput);
            // Serialize only the body members to the codec.
            SerializableStruct.filteredMembers(schema, struct, this::bodyBindingPredicate)
                .serialize(shapeBodySerializer);
            headers.put("content-type", List.of(payloadMediaType));
        }

        struct.serializeMembers(new BindingSerializer(this));
    }

    private boolean bodyBindingPredicate(Schema member) {
        return bindingMatcher.match(member) == BindingMatcher.Binding.BODY;
    }

    @Override
    public void flush() {
        if (shapeBodySerializer != null) {
            shapeBodySerializer.flush();
        }
    }

    void setHttpPayload(Schema schema, DataStream value) {
        httpPayload = value;
        if (headers.containsKey("content-type")) {
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
        headers.put("content-type", List.of(contentType));
    }

    HttpHeaders getHeaders() {
        return HttpHeaders.of(headers);
    }

    String getQueryString() {
        return queryStringParams.toString();
    }

    boolean hasQueryString() {
        return !queryStringParams.isEmpty();
    }

    boolean hasBody() {
        return shapeBodyOutput != null || httpPayload != null;
    }

    DataStream getBody() {
        if (httpPayload != null) {
            return httpPayload;
        } else if (shapeBodyOutput != null) {
            return DataStream.ofBytes(shapeBodyOutput.toByteArray(), payloadMediaType);
        } else {
            return DataStream.ofEmpty();
        }
    }

    long getBodyLength() {
        if (shapeBodyOutput != null) {
            return shapeBodyOutput.size();
        } else if (httpPayload != null) {
            return -1;
        } else {
            return 0;
        }
    }

    String getPath() {
        StringJoiner joiner = new StringJoiner("/", "/", "");
        for (SmithyPattern.Segment segment : uriPattern.getSegments()) {
            String content = segment.getContent();
            if (!segment.isLabel() && !segment.isGreedyLabel()) {
                // Append literal labels as-is.
                joiner.add(content);
            } else if (!labels.containsKey(content)) {
                // Labels are inherently required.
                throw new SerializationException("HTTP label not set for `" + content + "`");
            } else {
                String labelValue = labels.get(segment.getContent());
                if (segment.isGreedyLabel()) {
                    String encoded = URLEncoding.encodeUnreserved(labelValue, false);
                    joiner.add(encoded.replace("%2F", "/"));
                } else {
                    joiner.add(URLEncoding.encodeUnreserved(labelValue, false));
                }
            }
        }

        return joiner.toString();
    }

    int getResponseStatus() {
        return responseStatus;
    }

    public Flow.Publisher<? extends SerializableStruct> getEventStream() {
        return eventStream;
    }

    void setEventStream(Flow.Publisher<? extends SerializableStruct> stream) {
        this.eventStream = stream;
    }

    void writePayloadContentType() {
        setContentType(payloadMediaType);
    }

    public void setContentType(String contentType) {
        headers.put("content-type", List.of(contentType));
    }

    private static final class BindingSerializer extends InterceptingSerializer {
        private final HttpBindingSerializer serializer;
        private PayloadSerializer payloadSerializer;

        private BindingSerializer(HttpBindingSerializer serializer) {
            this.serializer = serializer;
        }

        @Override
        protected ShapeSerializer before(Schema schema) {
            return switch (serializer.bindingMatcher.match(schema)) {
                case HEADER -> serializer.headerSerializer;
                case QUERY -> serializer.querySerializer;
                case LABEL -> serializer.labelSerializer;
                case STATUS -> new ResponseStatusSerializer(i -> serializer.responseStatus = i);
                case PREFIX_HEADERS -> new HttpPrefixHeadersSerializer(
                    schema.expectTrait(TraitKey.HTTP_PREFIX_HEADERS_TRAIT).getValue(),
                    serializer.headerConsumer
                );
                case QUERY_PARAMS -> new HttpQueryParamsSerializer(serializer.queryStringParams::add);
                case BODY -> ShapeSerializer.nullSerializer(); // handled in HttpBindingSerializer#writeStruct.
                case PAYLOAD -> {
                    payloadSerializer = new PayloadSerializer(serializer, serializer.payloadCodec);
                    yield payloadSerializer;
                }
            };
        }

        @Override
        protected void after(Schema schema) {
            flush();
            if (payloadSerializer != null && !payloadSerializer.isPayloadWritten()) {
                payloadSerializer.flush();
                serializer.setHttpPayload(
                    schema,
                    DataStream.ofBytes(payloadSerializer.toByteArray())
                );
            }
        }
    }
}
