/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpHeaders;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.StructSerializer;
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

    private StructSerializer delegateStruct;
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
    protected RuntimeException throwForInvalidState(SdkSchema schema) {
        return new UnsupportedOperationException("HTTP bindings must start with a structure. Found " + schema);
    }

    @Override
    public StructSerializer beginStruct(SdkSchema schema) {
        return new HttpBindingStructSerializer(schema);
    }

    @Override
    public void flush() {
        if (shapeBodySerializer != null) {
            try {
                shapeBodySerializer.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    void setHttpPayload(SdkSchema schema, DataStream value) {
        httpPayload = value;
        String contentType = value.contentType()
            .orElseGet(() -> schema.getTrait(MediaTypeTrait.class).map(MediaTypeTrait::getValue).orElseGet(() -> {
                if (schema.type() == ShapeType.BLOB) {
                    return DEFAULT_BLOB_CONTENT_TYPE;
                } else {
                    return DEFAULT_STRING_CONTENT_TYPE;
                }
            }));
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

    private final class HttpBindingStructSerializer implements StructSerializer {

        private final SdkSchema structSchema;

        HttpBindingStructSerializer(SdkSchema structSchema) {
            this.structSchema = structSchema;
        }

        private StructSerializer forward() {
            if (delegateStruct == null) {
                shapeBodyOutput = new ByteArrayOutputStream();
                shapeBodySerializer = payloadCodec.createSerializer(shapeBodyOutput);
                delegateStruct = shapeBodySerializer.beginStruct(structSchema);
                headers.put("Content-Type", List.of(payloadCodec.getMediaType()));
            }
            return delegateStruct;
        }

        @Override
        public void endStruct() {
            if (delegateStruct != null) {
                delegateStruct.endStruct();
            }
        }

        @Override
        public void member(SdkSchema member, Consumer<ShapeSerializer> memberWriter) {
            switch (bindingMatcher.match(member)) {
                case HEADER -> memberWriter.accept(headerSerializer);
                case QUERY -> memberWriter.accept(querySerializer);
                case LABEL -> memberWriter.accept(labelSerializer);
                case PAYLOAD -> handleStructurePayload(member, memberWriter);
                case BODY -> forward().member(member, memberWriter);
                case STATUS -> memberWriter.accept(new ResponseStatusSerializer(i -> responseStatus = i));
                case PREFIX_HEADERS -> memberWriter
                    .accept(new HttpPrefixHeadersSerializer(bindingMatcher.prefixHeaders(), headerConsumer));
                case QUERY_PARAMS -> memberWriter.accept(new HttpQueryParamsSerializer(queryStringParams::put));
            }
        }

        private void handleStructurePayload(SdkSchema member, Consumer<ShapeSerializer> memberWriter) {
            if (member.memberTarget().type() == ShapeType.STRUCTURE) {
                // Deserialize a structure bound to the payload.
                headers.put("Content-Type", List.of(payloadCodec.getMediaType()));
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                memberWriter.accept(payloadCodec.createSerializer(output));
                var byteArray = output.toByteArray();
                setHttpPayload(member, DataStream.ofBytes(byteArray, payloadCodec.getMediaType()));
            }
        }
    }
}
