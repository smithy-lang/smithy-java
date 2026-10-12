/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.io.IOException;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;

/** What the generated clients talk to: the in-process stub or a fixture server over HTTPS. */
sealed interface Target extends AutoCloseable permits MockHttpTransport, FixtureServerProcess {

    static Target open(Mode mode) throws IOException {
        return switch (mode) {
            case STUB -> new MockHttpTransport();
            case HTTPS -> FixtureServerProcess.start();
        };
    }

    ClientTransport<HttpRequest, HttpResponse> transport();

    String endpoint();

    /** Makes every following request receive this response. Called once per case, before its first call. */
    void respondWith(CannedResponse response) throws IOException;

    /** Describes the transport for the run metadata. */
    String description();

    @Override
    void close() throws IOException;
}
