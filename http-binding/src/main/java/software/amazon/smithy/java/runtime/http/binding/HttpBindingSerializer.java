/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpHeaders;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.runtime.core.uri.QueryStringBuilder;
import software.amazon.smithy.java.runtime.core.uri.URLEncoding;
import software.amazon.smithy.model.pattern.SmithyPattern;
import software.amazon.smithy.model.pattern.UriPattern;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.MediaTypeTrait;

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

    private final Map<String, String> labels = new LinkedHashMap<>();
    private final Map<String, List<String>> headers = new LinkedHashMap<>();
    private final QueryStringBuilder queryStringParams = new QueryStringBuilder();

    private ShapeSerializer shapeBodySerializer;
    private ByteArrayOutputStream shapeBodyOutput;
    private DataStream httpPayload;
    private int responseStatus;

    private final BindingMatcher bindingMatcher;
    private final UriPattern uriPattern;
    private final BiConsumer<String, String> headerConsumer = (field, value) -> {
        headers.computeIfAbsent(field, f -> new ArrayList<>()).add(value);
    };

    HttpBindingSerializer(
        HttpTrait httpTrait,
        Codec payloadCodec,
        BindingMatcher bindingMatcher,
        DataStream httpPayload
    ) {
        uriPattern = httpTrait.getUri();
        responseStatus = httpTrait.getCode();
        this.payloadCodec = payloadCodec;
        this.bindingMatcher = bindingMatcher;
        headerSerializer = new HttpHeaderSerializer(headerConsumer);
        querySerializer = new HttpQuerySerializer(queryStringParams::put);
        labelSerializer = new HttpLabelSerializer(labels::put);
        this.httpPayload = httpPayload;
    }

    @Override
    public void writeStruct(SdkSchema schema, SerializableStruct struct) {
        boolean foundBody = false;
        for (var member : schema.members()) {
            if (bindingMatcher.match(member) == BindingMatcher.Binding.BODY) {
                foundBody = true;
                break;
            }
        }

        if (foundBody) {
            shapeBodyOutput = new ByteArrayOutputStream();
            shapeBodySerializer = payloadCodec.createSerializer(shapeBodyOutput);
            // Serialize only the body members to the codec.
            SerializableStruct.filteredMembers(schema, struct, this::bodyBindingPredicate)
                .serialize(shapeBodySerializer);
            headers.put("Content-Type", List.of(payloadCodec.getMediaType()));
        }

        // Serialize the bindings that aren't BODY (the derived body structure).
        struct.serializeMembers(new BindingSerializer(this));
    }

    private boolean bodyBindingPredicate(SdkSchema member) {
        return bindingMatcher.match(member) == BindingMatcher.Binding.BODY;
    }

    @Override
    public void flush() {
        if (shapeBodySerializer != null) {
            shapeBodySerializer.flush();
        }
    }

    void setHttpPayload(SdkSchema schema, DataStream value) {
        httpPayload = value;
        String contentType = value.contentType()
            .orElseGet(() -> {
                var mediaType = schema.getTrait(MediaTypeTrait.class);
                if (mediaType != null) {
                    return mediaType.getValue();
                } else if (schema.type() == ShapeType.BLOB) {
                    return DEFAULT_BLOB_CONTENT_TYPE;
                } else {
                    return DEFAULT_STRING_CONTENT_TYPE;
                }
            });
        headers.put("Content-Type", List.of(contentType));
    }

    HttpHeaders getHeaders() {
        return HttpHeaders.of(headers, (k, v) -> true);
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
            return DataStream.ofBytes(shapeBodyOutput.toByteArray(), payloadCodec.getMediaType());
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
                throw new SdkSerdeException("HTTP label not set for `" + content + "`");
            } else {
                String labelValue = labels.get(segment.getContent());
                if (segment.isGreedyLabel()) {
                    String encoded = URLEncoding.encodeUnreserved(labelValue);
                    joiner.add(encoded.replace("%2F", "/"));
                } else {
                    joiner.add(URLEncoding.encodeUnreserved(labelValue));
                }
            }
        }

        return joiner.toString();
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    private static final class BindingSerializer extends InterceptingSerializer {
        private final HttpBindingSerializer serializer;
        private ByteArrayOutputStream structureBytes;

        private BindingSerializer(HttpBindingSerializer serializer) {
            this.serializer = serializer;
        }

        @Override
        protected ShapeSerializer before(SdkSchema schema) {
            return switch (serializer.bindingMatcher.match(schema)) {
                case HEADER -> serializer.headerSerializer;
                case QUERY -> serializer.querySerializer;
                case LABEL -> serializer.labelSerializer;
                case STATUS -> new ResponseStatusSerializer(i -> serializer.responseStatus = i);
                case PREFIX_HEADERS -> new HttpPrefixHeadersSerializer(
                    serializer.bindingMatcher.prefixHeaders(),
                    serializer.headerConsumer
                );
                case QUERY_PARAMS -> new HttpQueryParamsSerializer(serializer.queryStringParams::put);
                case BODY -> ShapeSerializer.nullSerializer(); // handled in HttpBindingSerializer#writeStruct.
                case PAYLOAD -> {
                    if (schema.memberTarget().type() == ShapeType.STRUCTURE) {
                        // Serialize a structure bound to the payload.
                        serializer.headers.put("Content-Type", List.of(serializer.payloadCodec.getMediaType()));
                        structureBytes = new ByteArrayOutputStream();
                        yield serializer.payloadCodec.createSerializer(structureBytes);
                    } else {
                        // httpPayload serialization is handled elsewhere.
                        yield ShapeSerializer.nullSerializer();
                    }
                }
            };
        }

        @Override
        protected void after(SdkSchema schema) {
            if (structureBytes != null) {
                serializer.setHttpPayload(
                    schema,
                    DataStream.ofBytes(
                        structureBytes.toByteArray(),
                        serializer.payloadCodec.getMediaType()
                    )
                );
            }
        }
    }
}
