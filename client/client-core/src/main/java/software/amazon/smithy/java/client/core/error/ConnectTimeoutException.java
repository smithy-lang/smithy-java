/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.error;

import java.net.ConnectException;
import java.time.Duration;
import software.amazon.smithy.java.retries.api.RetryInfo;
import software.amazon.smithy.java.retries.api.RetrySafety;

/**
 * A connection could not be established within the configured amount of time.
 *
 * <p>This error is always retryable regardless of the type of call.
 *
 * <p>This exception is similar to the built-in {@link ConnectException}.
 */
public class ConnectTimeoutException extends TransportException implements RetryInfo {
    public ConnectTimeoutException(Throwable cause) {
        super(cause);
    }

    public ConnectTimeoutException(String message) {
        super(message);
    }

    public ConnectTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public RetrySafety isRetrySafe() {
        return RetrySafety.NO;
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
