/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.Objects;

/**
 * The top-level exception that should be used to throw application-level errors from clients and servers.
 *
 * <p>This should be used in protocol error deserialization, throwing errors based on protocol-hints, network
 * errors, and shape validation errors. It should not be used for illegal arguments, null argument validation,
 * or other kinds of logic errors sufficiently covered by the Java standard library.
 */
public class ApiException extends RuntimeException {

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

    public ApiException(String message) {
        this(message, Fault.OTHER);
    }

    public ApiException(String message, Fault errorType) {
        super(message);
        this.errorType = Objects.requireNonNull(errorType);
    }

    public ApiException(String message, Throwable cause) {
        this(message, cause, Fault.OTHER);
    }

    public ApiException(String message, Throwable cause, Fault errorType) {
        super(message, cause);
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
}
