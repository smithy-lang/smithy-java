/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.plugins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.client.http.HttpContext;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.RequestCompressionTrait;
import software.amazon.smithy.model.traits.StreamingTrait;

public class RequestCompressionPluginTest {

    private static final String REQUEST_BODY = "THIS IS MY COMPRESSION TEST BODY!";

    @Test
    public void doesNotCompressWhenDisabled() throws Exception {
        var interceptor = new RequestCompressionPlugin.RequestCompressionInterceptor();
        var context = Context.create();
        context.put(HttpContext.DISABLE_REQUEST_COMPRESSION, true);
        context.put(HttpContext.REQUEST_MIN_COMPRESSION_SIZE_BYTES, 1);
        var req = HttpRequest.builder()
                .uri(new URI("/"))
                .method("POST")
                .body(DataStream.ofString(REQUEST_BODY))
                .build();

        var result = interceptor.modifyBeforeRetryLoop(
                new RequestHook<>(createOperationWithCompressionTrait(), context, new TestInput(), req));

        assertThat(result.headers().allValues("Content-Encoding"), empty());
    }

    @Test
    public void compressesWhenBodyMeetsMinSize() throws Exception {
        var interceptor = new RequestCompressionPlugin.RequestCompressionInterceptor();
        var context = Context.create();
        String largeBody = REQUEST_BODY.repeat(10);
        context.put(HttpContext.REQUEST_MIN_COMPRESSION_SIZE_BYTES, 10);
        var req = HttpRequest.builder()
                .uri(new URI("/"))
                .method("POST")
                .body(DataStream.ofString(largeBody))
                .build();

        var result = interceptor.modifyBeforeRetryLoop(
                new RequestHook<>(createOperationWithCompressionTrait(), context, new TestInput(), req));

        assertThat(result.headers().allValues("Content-Encoding"), contains("gzip"));
        String decompressed = decompress(result.body().asByteBuffer().array());
        assertThat(decompressed, equalTo(largeBody));
    }

    @Test
    public void doesNotCompressWhenBodyBelowMinSize() throws Exception {
        var interceptor = new RequestCompressionPlugin.RequestCompressionInterceptor();
        var context = Context.create();
        context.put(HttpContext.REQUEST_MIN_COMPRESSION_SIZE_BYTES, 10000);
        var req = HttpRequest.builder()
                .uri(new URI("/"))
                .method("POST")
                .body(DataStream.ofString(REQUEST_BODY))
                .build();

        var result = interceptor.modifyBeforeRetryLoop(
                new RequestHook<>(createOperationWithCompressionTrait(), context, new TestInput(), req));

        assertThat(result.headers().allValues("Content-Encoding"), empty());
    }

    @Test
    public void usesDefaultMinCompressionSize() throws Exception {
        var interceptor = new RequestCompressionPlugin.RequestCompressionInterceptor();
        var context = Context.create();
        // Body is smaller than default 10240
        var req = HttpRequest.builder()
                .uri(new URI("/"))
                .method("POST")
                .body(DataStream.ofString(REQUEST_BODY))
                .build();

        var result = interceptor.modifyBeforeRetryLoop(
                new RequestHook<>(createOperationWithCompressionTrait(), context, new TestInput(), req));

        assertThat(result.headers().allValues("Content-Encoding"), empty());
    }

    @Test
    public void alwaysCompressesStreamingWithoutKnownLength() throws Exception {
        var interceptor = new RequestCompressionPlugin.RequestCompressionInterceptor();
        var context = Context.create();
        context.put(HttpContext.REQUEST_MIN_COMPRESSION_SIZE_BYTES, 999999);
        var original = "small";
        var streamBody = DataStream.ofInputStream(new ByteArrayInputStream(original.getBytes(StandardCharsets.UTF_8)));
        var req = HttpRequest.builder()
                .uri(new URI("/"))
                .method("POST")
                .body(streamBody)
                .build();

        var result = interceptor.modifyBeforeRetryLoop(
                new RequestHook<>(createOperationWithStreamingInput(), context, new TestInput(), req));

        assertThat(result.headers().allValues("Content-Encoding"), contains("gzip"));
        String decompressed = decompress(result.body().asInputStream().readAllBytes());
        assertThat(decompressed, equalTo(original));
    }

    @Test
    public void throwsForNegativeMinCompressionSize() throws Exception {
        var interceptor = new RequestCompressionPlugin.RequestCompressionInterceptor();
        var context = Context.create();
        context.put(HttpContext.REQUEST_MIN_COMPRESSION_SIZE_BYTES, -1);
        var largeBody = REQUEST_BODY.repeat(100);
        var req = HttpRequest.builder()
                .uri(new URI("/"))
                .method("POST")
                .body(DataStream.ofString(largeBody))
                .build();

        var hook = new RequestHook<>(createOperationWithCompressionTrait(), context, new TestInput(), req);
        Assertions.assertThrows(IllegalArgumentException.class, () -> interceptor.modifyBeforeRetryLoop(hook));
    }

    @Test
    public void throwsForMinCompressionSizeExceedingCap() throws Exception {
        var interceptor = new RequestCompressionPlugin.RequestCompressionInterceptor();
        var context = Context.create();
        context.put(HttpContext.REQUEST_MIN_COMPRESSION_SIZE_BYTES, 10485761);
        var largeBody = REQUEST_BODY.repeat(100);
        var req = HttpRequest.builder()
                .uri(new URI("/"))
                .method("POST")
                .body(DataStream.ofString(largeBody))
                .build();

        var hook = new RequestHook<>(createOperationWithCompressionTrait(), context, new TestInput(), req);
        Assertions.assertThrows(IllegalArgumentException.class, () -> interceptor.modifyBeforeRetryLoop(hook));
    }

    @Test
    public void doesNotCompressWhenTraitAbsent() throws Exception {
        var interceptor = new RequestCompressionPlugin.RequestCompressionInterceptor();
        var context = Context.create();
        context.put(HttpContext.REQUEST_MIN_COMPRESSION_SIZE_BYTES, 1);
        var req = HttpRequest.builder()
                .uri(new URI("/"))
                .method("POST")
                .body(DataStream.ofString(REQUEST_BODY))
                .build();

        var result = interceptor.modifyBeforeRetryLoop(
                new RequestHook<>(createOperationWithoutCompressionTrait(), context, new TestInput(), req));

        assertThat(result.headers().allValues("Content-Encoding"), empty());
    }

    @Test
    public void appendsGzipToExistingContentEncoding() throws Exception {
        var interceptor = new RequestCompressionPlugin.RequestCompressionInterceptor();
        var context = Context.create();
        context.put(HttpContext.REQUEST_MIN_COMPRESSION_SIZE_BYTES, 10);
        var req = HttpRequest.builder()
                .uri(new URI("/"))
                .method("POST")
                .body(DataStream.ofString(REQUEST_BODY))
                .withAddedHeader("Content-Encoding", "custom")
                .build();

        var result = interceptor.modifyBeforeRetryLoop(
                new RequestHook<>(createOperationWithCompressionTrait(), context, new TestInput(), req));

        var encodings = result.headers().allValues("Content-Encoding");
        assertThat(encodings, contains("custom", "gzip"));
    }

    // Helper: Create operation with @requestCompression trait
    private ApiOperation<SerializableStruct, SerializableStruct> createOperationWithCompressionTrait() {
        var trait = RequestCompressionTrait.builder().addEncoding("gzip").build();
        var schema = Schema.createOperation(ShapeId.from("com.test#TestOp"), trait);
        return createOperation(schema, null);
    }

    // Helper: Create operation without @requestCompression trait
    private ApiOperation<SerializableStruct, SerializableStruct> createOperationWithoutCompressionTrait() {
        var schema = Schema.createOperation(ShapeId.from("com.test#TestOp"));
        return createOperation(schema, null);
    }

    // Helper: Create operation with streaming input
    private ApiOperation<SerializableStruct, SerializableStruct> createOperationWithStreamingInput() {
        var trait = RequestCompressionTrait.builder().addEncoding("gzip").build();
        var schema = Schema.createOperation(ShapeId.from("com.test#TestOp"), trait);
        var blobSchema = Schema.createBlob(ShapeId.from("com.test#StreamBody"), new StreamingTrait());
        return createOperation(schema, blobSchema);
    }

    private ApiOperation<SerializableStruct, SerializableStruct> createOperation(Schema schema, Schema streamMember) {
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
            public Schema inputStreamMember() {
                return streamMember;
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

    private String decompress(byte[] compressed) throws Exception {
        try (var gzipIn = new GZIPInputStream(new ByteArrayInputStream(compressed));
                var out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
