/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.error;

import java.time.Duration;
import java.util.Objects;
import software.amazon.smithy.java.retries.api.RetryInfo;
import software.amazon.smithy.java.retries.api.RetrySafety;

/**
 * The top-level exception that should be used to throw application-level errors from clients and servers.
 *
 * <p>This should be used in protocol error deserialization, throwing errors based on protocol-hints, network
 * errors, and shape validation errors. It should not be used for illegal arguments, null argument validation,
 * or other kinds of logic errors sufficiently covered by the Java standard library.
 */
public class CallException extends RuntimeException implements RetryInfo {

    private static final boolean GLOBAL_CAPTURE_STACK_TRACE_ENABLED = Boolean.getBoolean(
            "smithy.java.captureExceptionStackTraces");

    private final ErrorFault errorType;

    // Mutable retryInfo
    private RetrySafety isRetrySafe = RetrySafety.MAYBE;
    private boolean isThrottle = false;
    private Duration retryAfter = null;

    public CallException(String message) {
        this(message, ErrorFault.OTHER, null);
    }

    public CallException(String message, boolean captureStackTrace) {
        this(message, ErrorFault.OTHER, captureStackTrace);
    }

    public CallException(String message, ErrorFault errorType) {
        this(message, errorType, null);
    }

    public CallException(String message, ErrorFault errorType, boolean captureStackTrace) {
        this(message, null, errorType, captureStackTrace);
    }

    protected CallException(String message, ErrorFault errorType, Boolean captureStackTrace) {
        this(message, null, errorType, captureStackTrace);
    }

    public CallException(String message, Throwable cause) {
        this(message, cause, (Boolean) null);
    }

    public CallException(String message, Throwable cause, boolean captureStackTrace) {
        this(message, cause, ErrorFault.OTHER, captureStackTrace);
    }

    protected CallException(String message, Throwable cause, Boolean captureStackTrace) {
        this(message, cause, ErrorFault.OTHER, captureStackTrace);
    }

    public CallException(String message, Throwable cause, ErrorFault errorType) {
        this(message, cause, errorType, false);
    }

    public CallException(String message, Throwable cause, ErrorFault errorType, boolean captureStackTrace) {
        this(message, cause, errorType, (Boolean) captureStackTrace);
    }

    protected CallException(String message, Throwable cause, ErrorFault errorType, Boolean captureStackTrace) {
        super(
                message,
                cause,
                false,
                captureStackTrace != null ? captureStackTrace : GLOBAL_CAPTURE_STACK_TRACE_ENABLED);
        this.errorType = Objects.requireNonNull(errorType);
    }

    /**
     * Get the error type for this exception.
     *
     * @return Returns the error type (e.g., client, server, etc).
     */
    public ErrorFault getFault() {
        return errorType;
    }

    @Override
    public boolean isThrottle() {
        return isThrottle;
    }

    /**
     * Set whether the error is a throttling exception.
     *
     * @param isThrottle Set to true if it's a throttle.
     */
    public void isThrottle(boolean isThrottle) {
        this.isThrottle = isThrottle;
    }

    @Override
    public Duration retryAfter() {
        return retryAfter;
    }

    /**
     * Set a suggested time to wait before retrying.
     *
     * @param retryAfter Retry after time to set, or null to clear.
     */
    public void retryAfter(Duration retryAfter) {
        this.retryAfter = retryAfter;
    }

    @Override
    public RetrySafety isRetrySafe() {
        return isRetrySafe;
    }

    /**
     * Set whether it's safe to retry.
     *
     * @param isRetrySafe Set if it's safe to retry.
     */
    public void isRetrySafe(RetrySafety isRetrySafe) {
        this.isRetrySafe = isRetrySafe;
        if (isRetrySafe == RetrySafety.NO) {
            retryAfter = null;
            isThrottle = false;
        }
    }
}
