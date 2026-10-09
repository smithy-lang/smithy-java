/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * In-process HTTP transport that returns a canned response. No sockets, no localhost server.
 *
 * <p>The request body is fully read and the streaming response payload is drained to the end, as a real
 * transport writing bytes and a client consuming them do, so payload-movement cost is included in the
 * measurement (ocs counts reading a streaming payload to the end).
 *
 * <p>Not thread-safe: a benchmark drives one request at a time from one thread.
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

    /**
     * The stub accepts whatever the client sends, so it is the one transport that cannot notice a request whose
     * body disagrees with its own {@code Content-Length}; a real server rejects such a request mid-body. Check
     * it here, where every benchmark passes once before it is timed.
     */
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

    /** Consumes a body the way a transport would and returns the number of bytes it held. */
    static long drain(DataStream body) {
        if (body == null) {
            return 0;
        }
        // Read every byte, as a real transport writing the request and a client reading a streaming response
        // both do. ocs counts "reading a streaming payload to the end"; the asByteBuffer().remaining() shortcut
        // would report the length of an in-memory body without touching it, undercounting large payloads (e.g.
        // GetObject_L) to near-zero. The baseline harness reads fully too, so both sides measure the same work.
        try (var in = body.asInputStream()) {
            return in.transferTo(OutputStream.nullOutputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
