/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.java.server.core.HttpResponseSerializer.SerializedResponse;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Tests for {@link HttpResponseSerializer}, covering the success path
 * (status + headers + body), the failure path (500 with empty body),
 * the no-payload path (no body), and the header-precedence rules
 * (framework-set content-type/length wins over the serialized
 * payload's defaults).
 */
public class HttpResponseSerializerTest {

    private static final ServerProtocol STUB_PROTOCOL = new StubProtocol();

    private static HttpJob jobWithBody(byte[] body, String contentType) {
        Operation<?, ?> op = TestStructs.createMockOperation("Op");
        var request = new HttpRequest(HttpHeaders.ofModifiable(), URI.create("http://localhost/"), "GET");
        var response = new HttpResponse(HttpHeaders.ofModifiable());
        response.setSerializedValue(DataStream.ofBytes(body, contentType));
        @SuppressWarnings({"rawtypes", "unchecked"})
        HttpJob job = new HttpJob((Operation) op, STUB_PROTOCOL, request, response);
        return job;
    }

    private static final class StubProtocol extends ServerProtocol {
        StubProtocol() {
            super(List.of());
        }

        @Override
        public ShapeId getProtocolId() {
            return ShapeId.from("test#Stub");
        }

        @Override
        public ServiceProtocolResolutionResult resolveOperation(
                ServiceProtocolResolutionRequest request,
                List<Service> candidates
        ) {
            return null;
        }

        @Override
        public CompletableFuture<Void> deserializeInput(Job job) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        protected CompletableFuture<Void> serializeOutput(Job job, SerializableStruct output, boolean isError) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static byte[] readBody(SerializedResponse sr) {
        if (sr.body() == null) {
            return new byte[0];
        }
        var bb = sr.body().asByteBuffer();
        byte[] out = new byte[bb.remaining()];
        bb.get(out);
        return out;
    }

    @Test
    public void failureProducesFiveHundredWithNoBody() {
        var sr = HttpResponseSerializer.from(jobWithBody("hello".getBytes(StandardCharsets.UTF_8), "text/plain"),
                new RuntimeException("boom"));
        assertThat(sr.statusCode()).isEqualTo(500);
        assertThat(sr.body()).isNull();
        assertThat(sr.headers().map()).isEmpty();
    }

    @Test
    public void successWithBodyEmitsStatusAndContentTypeAndLength() {
        var job = jobWithBody("hello".getBytes(StandardCharsets.UTF_8), "text/plain");
        job.response().setStatusCode(200);

        var sr = HttpResponseSerializer.from(job, null);
        assertThat(sr.statusCode()).isEqualTo(200);
        assertThat(new String(readBody(sr), StandardCharsets.UTF_8)).isEqualTo("hello");
        assertThat(sr.headers().firstValue("content-type")).isEqualTo("text/plain");
        assertThat(sr.headers().firstValue("content-length")).isEqualTo("5");
    }

    @Test
    public void successWithoutStatusDefaultsTo200() {
        var job = jobWithBody(new byte[0], null);

        var sr = HttpResponseSerializer.from(job, null);
        assertThat(sr.statusCode()).isEqualTo(200);
    }

    @Test
    public void successWithNoSerializedValueProducesNullBodyAndPreservesStatus() {
        Operation<?, ?> op = TestStructs.createMockOperation("Op");
        var request = new HttpRequest(HttpHeaders.ofModifiable(), URI.create("http://localhost/"), "GET");
        var response = new HttpResponse(HttpHeaders.ofModifiable());
        response.setStatusCode(204);
        @SuppressWarnings({"rawtypes", "unchecked"})
        HttpJob job = new HttpJob((Operation) op, STUB_PROTOCOL, request, response);

        var sr = HttpResponseSerializer.from(job, null);
        assertThat(sr.statusCode()).isEqualTo(204);
        assertThat(sr.body()).isNull();
        assertThat(sr.headers().firstValue("content-length")).isNull();
    }

    @Test
    public void frameworkSetContentTypeWins() {
        // A handler that pre-set a different content-type must not be
        // overwritten by the serialized payload's media type.
        var job = jobWithBody("hello".getBytes(StandardCharsets.UTF_8), "text/plain");
        job.response().setStatusCode(200);
        job.response().headers().setHeader("content-type", "application/cbor");

        var sr = HttpResponseSerializer.from(job, null);
        assertThat(sr.headers().firstValue("content-type")).isEqualTo("application/cbor");
    }

    @Test
    public void frameworkSetContentLengthWins() {
        // A handler that pre-set content-length must not be
        // overwritten by the serialized payload's byte length.
        var job = jobWithBody("hello".getBytes(StandardCharsets.UTF_8), "text/plain");
        job.response().setStatusCode(200);
        job.response().headers().setHeader("content-length", "100");

        var sr = HttpResponseSerializer.from(job, null);
        assertThat(sr.headers().firstValue("content-length")).isEqualTo("100");
    }
}
