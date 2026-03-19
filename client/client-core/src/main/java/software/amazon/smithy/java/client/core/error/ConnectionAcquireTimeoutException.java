/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.error;

import java.time.Duration;
import software.amazon.smithy.java.retries.api.RetryInfo;
import software.amazon.smithy.java.retries.api.RetrySafety;

/**
 * A connection could not be leased from a connection pool after the configured amount of time.
 *
 * <p>This is a best-effort exception. Transports should throw this exception when possible if their connection pooling
 * allows for it.
 */
public class ConnectionAcquireTimeoutException extends TransportException implements RetryInfo {
    public ConnectionAcquireTimeoutException(String message) {
        super(message);
    }

    public ConnectionAcquireTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public RetrySafety isRetrySafe() {
        return RetrySafety.YES;
    }

    @Override
    public boolean isThrottle() {
        return false;
    }

    @Override
    public Duration retryAfter() {
        return null;
    }
}
