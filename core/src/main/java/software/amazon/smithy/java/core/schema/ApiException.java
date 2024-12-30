/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

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
public class ApiException extends RuntimeException implements RetryInfo {

    private static final boolean GLOBAL_CAPTURE_STACK_TRACE_ENABLED = Boolean.getBoolean(
            "smithy.java.captureExceptionStackTraces");

    /**
     * The party that is at fault for the error, if any.
     *
     * <p>This kind of enum is used rather than top-level client/server errors that services extend from because
     * services generate their own dedicated error hierarchies that all extend from a common base-class for the
     * service.
     */
    public enum Fault {
        /**
         * The client is at fault for this error (e.g., it omitted a required parameter or sent an invalid request).
         */
        CLIENT,

        /**
         * The server is at fault (e.g., it was unable to connect to a database, or other unexpected errors occurred).
         */
        SERVER,

        /**
         * The fault isn't necessarily client or server.
         */
        OTHER;

        /**
         * Create a Fault based on an HTTP status code.
         *
         * @param statusCode HTTP status code to check.
         * @return the created fault.
         */
        public static Fault ofHttpStatusCode(int statusCode) {
            if (statusCode >= 400 && statusCode <= 499) {
                return ApiException.Fault.CLIENT;
            } else if (statusCode >= 500 && statusCode <= 599) {
                return ApiException.Fault.SERVER;
            } else {
                return ApiException.Fault.OTHER;
            }
        }
    }

    private final Fault errorType;

    // Mutable retryInfo
    private RetrySafety isRetrySafe = RetrySafety.MAYBE;
    private boolean isThrottle = false;
    private Duration retryAfter = null;

    public ApiException(String message) {
        this(message, Fault.OTHER, null);
    }

    public ApiException(String message, boolean captureStackTrace) {
        this(message, Fault.OTHER, captureStackTrace);
    }

    public ApiException(String message, Fault errorType) {
        this(message, errorType, null);
    }

    public ApiException(String message, Fault errorType, boolean captureStackTrace) {
        this(message, null, errorType, captureStackTrace);
    }

    protected ApiException(String message, Fault errorType, Boolean captureStackTrace) {
        this(message, null, errorType, captureStackTrace);
    }

    public ApiException(String message, Throwable cause) {
        this(message, cause, (Boolean) null);
    }

    public ApiException(String message, Throwable cause, boolean captureStackTrace) {
        this(message, cause, Fault.OTHER, captureStackTrace);
    }

    protected ApiException(String message, Throwable cause, Boolean captureStackTrace) {
        this(message, cause, Fault.OTHER, captureStackTrace);
    }

    public ApiException(String message, Throwable cause, Fault errorType) {
        this(message, cause, errorType, false);
    }

    public ApiException(String message, Throwable cause, Fault errorType, boolean captureStackTrace) {
        this(message, cause, errorType, (Boolean) captureStackTrace);
    }

    protected ApiException(String message, Throwable cause, Fault errorType, Boolean captureStackTrace) {
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
    public Fault getFault() {
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
