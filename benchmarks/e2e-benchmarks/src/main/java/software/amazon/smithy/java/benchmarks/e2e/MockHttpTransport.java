/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Returns canned responses and consumes request bodies in process.
 * Supports one request at a time on one thread.
 */
final class MockHttpTransport implements CountingTransport {

    private CannedResponse response;
    private HttpRequest lastRequest;
    private long lastRequestBodyBytes;
    private long requests;
    private long requestBodyBytes;

    @Override
    public void prepare(BenchmarkCase benchmarkCase) {
        response = benchmarkCase.response();
    }

    @Override
    public int lastResponseStatus() {
        return response == null ? -1 : response.statusCode();
    }

    @Override
    public long lastResponseLength() {
        return response == null ? -1 : response.bodyLength();
    }

    @Override
    public String lastHttpVersion() {
        return "stub";
    }

    @Override
    public String description() {
        return RunReport.HTTP_MOCK;
    }

    @Override
    public void resetCounters() {
        requests = 0;
        requestBodyBytes = 0;
    }

    @Override
    public long requests() {
        return requests;
    }

    @Override
    public long requestBodyBytes() {
        return requestBodyBytes;
    }

    @Override
    public HttpRequest lastRequest() {
        return lastRequest;
    }

    @Override
    public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
        return HttpMessageExchange.INSTANCE;
    }

    @Override
    public HttpResponse send(Context context, HttpRequest request) {
        requests++;
        lastRequest = request;
        lastRequestBodyBytes = drain(request.body());
        requestBodyBytes += lastRequestBodyBytes;
        return response.newHttpResponse();
    }

    /** Reject body lengths that a real server would reject before measurement. */
    @Override
    public void validateLast(BenchmarkCase benchmarkCase) {
        CountingTransport.super.validateLast(benchmarkCase);
        Long declared = lastRequest == null ? null : lastRequest.headers().contentLength();
        if (declared != null && declared != lastRequestBodyBytes) {
            throw new IllegalStateException(benchmarkCase.id() + ": the request declares Content-Length " + declared
                    + " but its body held " + lastRequestBodyBytes
                    + " bytes. A real server rejects this; check how the case's payload parameter is decoded.");
        }
    }

    // Copy bytes into a reusable buffer. A null output stream can skip reading an in-memory body.
    private static final ThreadLocal<byte[]> SCRATCH = ThreadLocal.withInitial(() -> new byte[8192]);

    static long drain(DataStream body) {
        if (body == null) {
            return 0;
        }
        byte[] buf = SCRATCH.get();
        long total = 0;
        try (var in = body.asInputStream()) {
            int n;
            while ((n = in.read(buf)) >= 0) {
                total += n;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return total;
    }

    static long consume(ByteBuffer payload) {
        ByteBuffer b = payload.duplicate();
        byte[] buf = SCRATCH.get();
        long total = b.remaining();
        while (b.hasRemaining()) {
            b.get(buf, 0, Math.min(buf.length, b.remaining()));
        }
        return total;
    }
}
