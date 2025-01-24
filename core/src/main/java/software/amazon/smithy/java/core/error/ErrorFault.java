/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.error;

/**
 * The party that is at fault for the error, if any.
 *
 * <p>This kind of enum is used rather than top-level client/server errors that services extend from because
 * services generate their own dedicated error hierarchies that all extend from a common base-class for the
 * service.
 */
public enum ErrorFault {
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
    public static ErrorFault ofHttpStatusCode(int statusCode) {
        if (statusCode >= 400 && statusCode <= 499) {
            return ErrorFault.CLIENT;
        } else if (statusCode >= 500 && statusCode <= 599) {
            return ErrorFault.SERVER;
        } else {
            return ErrorFault.OTHER;
        }
    }
}
