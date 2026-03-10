/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.error;

import java.time.Duration;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.core.error.CallException;
import software.amazon.smithy.java.retries.api.RetryInfo;
import software.amazon.smithy.java.retries.api.RetrySafety;

/**
 * An exception thrown by a {@link ClientTransport}.
 *
 * <p>The base assumption of a transport exception is that it is not retryable. However, subclasses may override the
 * {@link RetryInfo} implementation.
 */
public class TransportException extends CallException {
    public TransportException(String message) {
        super(message);
    }

    public TransportException(String message, Throwable cause) {
        super(message, cause);
    }

    public TransportException(Throwable cause) {
        this(cause.getMessage(), cause);
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
