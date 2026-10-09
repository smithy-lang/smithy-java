/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;

/**
 * A transport the harness can observe: the in-process stub or an instrumented real transport.
 */
interface CountingTransport extends ClientTransport<HttpRequest, HttpResponse> {

    /** Points the transport at a benchmark: the stub installs its canned response, a real transport records what it must see. */
    void prepare(BenchmarkCase benchmarkCase);

    void resetCounters();

    /** Requests sent since the last reset. */
    long requests();

    /** Request body bytes produced since the last reset. */
    long requestBodyBytes();

    /** The most recently sent request. */
    HttpRequest lastRequest();

    /** Status of the most recent response, or -1 before any. */
    int lastResponseStatus();

    /** Declared length of the most recent response body, or -1 if unknown. */
    long lastResponseLength();

    /** Wire HTTP version of the most recent response, or {@code "unknown"}. */
    String lastHttpVersion();

    /** Short description for results metadata. */
    String description();

    /**
     * Checks the most recent exchange against the benchmark's fixture: same status and body length. The stub always
     * matches; a fixture server serves one fixture per run, so a mismatch means the server was started for a
     * different benchmark.
     */
    default void validateLast(BenchmarkCase benchmarkCase) {
        var expected = benchmarkCase.response();
        if (lastResponseStatus() != expected.statusCode()) {
            throw new IllegalStateException(benchmarkCase.id() + ": expected response status " + expected.statusCode()
                    + " but the transport saw " + lastResponseStatus()
                    + ". A fixture server serves one fixture per run; start it with `export-fixture "
                    + benchmarkCase.id() + "` output.");
        }
        if (lastResponseLength() >= 0 && lastResponseLength() != expected.bodyLength()) {
            throw new IllegalStateException(benchmarkCase.id() + ": expected a " + expected.bodyLength()
                    + "-byte response body but the transport saw " + lastResponseLength()
                    + " bytes. The fixture server is serving a different fixture.");
        }
    }
}
