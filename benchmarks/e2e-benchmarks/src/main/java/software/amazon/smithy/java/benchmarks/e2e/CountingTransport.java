/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;

interface CountingTransport extends ClientTransport<HttpRequest, HttpResponse> {

    void prepare(BenchmarkCase benchmarkCase);

    void resetCounters();

    /** Counts requests since the last reset. */
    long requests();

    /** Counts request body bytes since the last reset. */
    long requestBodyBytes();

    HttpRequest lastRequest();

    /** Returns -1 before the first response. */
    int lastResponseStatus();

    /** Returns -1 if the response body length is unknown. */
    long lastResponseLength();

    /** Returns the wire HTTP version, or "unknown". */
    String lastHttpVersion();

    String description();

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
