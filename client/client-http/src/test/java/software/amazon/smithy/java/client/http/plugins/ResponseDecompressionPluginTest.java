/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.plugins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.client.core.interceptors.ResponseHook;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.StreamingTrait;

public class ResponseDecompressionPluginTest {

    private static final String BODY = "{\"hello\":\"world\"}";

    private final ResponseDecompressionPlugin.ResponseDecompressionInterceptor interceptor =
            new ResponseDecompressionPlugin.ResponseDecompressionInterceptor();

    @Test
    public void requestsAndDecodesGzipResponses() throws Exception {
        var operation = createOperation(null);
        var context = Context.create();
        var compressed = gzip(BODY);

        var request =
                interceptor.modifyBeforeRetryLoop(new RequestHook<>(operation, context, new TestInput(), request()));
        var response = interceptor.modifyBeforeDeserialization(new ResponseHook<>(
                operation,
                context,
                new TestInput(),
                request,
                response(Map.of(
                        "Content-Encoding",
                        List.of("gzip"),
                        "Content-Length",
                        List.of(String.valueOf(compressed.length))), compressed)));

        assertThat(request.headers().firstValue("Accept-Encoding"), equalTo("gzip"));
        assertThat(bodyOf(response), equalTo(BODY));
        assertThat(response.headers().firstValue("Content-Encoding"), nullValue());
        assertThat(response.headers().firstValue("Content-Length"), nullValue());
        assertThat(response.body().contentType(), equalTo("application/json"));
    }

    @Test
    public void leavesResponsesThatAreNotCompressedUnchanged() throws Exception {
        var operation = createOperation(null);
        var context = Context.create();
        var plain = response(Map.of(), BODY.getBytes(StandardCharsets.UTF_8));
        var deflated = response(Map.of("Content-Encoding", List.of("deflate")), BODY.getBytes(StandardCharsets.UTF_8));

        var request =
                interceptor.modifyBeforeRetryLoop(new RequestHook<>(operation, context, new TestInput(), request()));

        assertThat(interceptor.modifyBeforeDeserialization(
                new ResponseHook<>(operation, context, new TestInput(), request, plain)), sameInstance(plain));
        assertThat(interceptor.modifyBeforeDeserialization(
                new ResponseHook<>(operation, context, new TestInput(), request, deflated)), sameInstance(deflated));
    }

    @Test
    public void leavesRequestsThatSetAcceptEncodingAlone() throws Exception {
        var operation = createOperation(null);
        var context = Context.create();
        var original = request().toModifiableCopy().setHeader("Accept-Encoding", "br").toUnmodifiable();
        var compressed = response(Map.of("Content-Encoding", List.of("gzip")), gzip(BODY));

        var request =
                interceptor.modifyBeforeRetryLoop(new RequestHook<>(operation, context, new TestInput(), original));

        assertThat(request, sameInstance(original));
        assertThat(interceptor.modifyBeforeDeserialization(
                new ResponseHook<>(operation, context, new TestInput(), request, compressed)),
                sameInstance(compressed));
    }

    @Test
    public void leavesStreamingOutputOperationsAlone() throws Exception {
        var operation = createOperation(Schema.createBlob(ShapeId.from("com.test#StreamBody"), new StreamingTrait()));
        var context = Context.create();
        var original = request();
        var compressed = response(Map.of("Content-Encoding", List.of("gzip")), gzip(BODY));

        var request =
                interceptor.modifyBeforeRetryLoop(new RequestHook<>(operation, context, new TestInput(), original));

        assertThat(request, sameInstance(original));
        assertThat(interceptor.modifyBeforeDeserialization(
                new ResponseHook<>(operation, context, new TestInput(), request, compressed)),
                sameInstance(compressed));
    }

    private static HttpRequest request() throws Exception {
        return HttpRequest.create()
                .setUri(new URI("https://example.com/"))
                .setMethod("POST")
                .toUnmodifiable();
    }

    private static HttpResponse response(Map<String, List<String>> headers, byte[] body) {
        var allHeaders = HttpHeaders.ofModifiable(headers);
        allHeaders.addHeader("Content-Type", "application/json");
        return HttpResponse.of(HttpVersion.HTTP_1_1, 200, allHeaders, DataStream.ofBytes(body, "application/json"));
    }

    private static byte[] gzip(String text) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(out)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    private static String bodyOf(HttpResponse response) {
        return new String(ByteBufferUtils.getBytes(response.body().asByteBuffer()), StandardCharsets.UTF_8);
    }

    private static ApiOperation<SerializableStruct, SerializableStruct> createOperation(Schema outputStreamMember) {
        var schema = Schema.createOperation(ShapeId.from("com.test#TestOp"));
        return new ApiOperation<>() {
            @Override
            public ShapeBuilder<SerializableStruct> inputBuilder() {
                return null;
            }

            @Override
            public ShapeBuilder<SerializableStruct> outputBuilder() {
                return null;
            }

            @Override
            public Schema schema() {
                return schema;
            }

            @Override
            public Schema inputSchema() {
                return null;
            }

            @Override
            public Schema outputSchema() {
                return null;
            }

            @Override
            public ApiService service() {
                return null;
            }

            @Override
            public TypeRegistry errorRegistry() {
                return null;
            }

            @Override
            public List<ShapeId> effectiveAuthSchemes() {
                return List.of();
            }

            @Override
            public List<Schema> errorSchemas() {
                return List.of();
            }

            @Override
            public Schema outputStreamMember() {
                return outputStreamMember;
            }
        };
    }

    private static final class TestInput implements SerializableStruct {
        @Override
        public Schema schema() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }
}
