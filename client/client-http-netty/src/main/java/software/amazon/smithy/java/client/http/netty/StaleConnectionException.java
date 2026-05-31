/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import java.io.IOException;

/**
 * Thrown when a request fails on a connection that was <em>reused</em> from the pool and the failure
 * occurred <em>before any response was received</em> — the hallmark of a keep-alive connection that
 * the server had already closed (idle timeout, max-requests) but which still looked active when it
 * was handed out.
 *
 * <p>Because no response byte was ever observed, the request was fully buffered client-side and
 * never acknowledged by a responding server, so it is safe to retry on a fresh connection — even
 * for non-idempotent operations. {@link NettyHttpClientTransport#send} catches this internally and
 * retries once on a guaranteed-fresh connection when the request body is replayable. It is never
 * surfaced to callers.
 */
final class StaleConnectionException extends IOException {
    StaleConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    StaleConnectionException(String message) {
        super(message);
    }
}
