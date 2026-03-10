/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.error;

/**
 * Indicates that the connection was unexpectedly closed.
 *
 * <p>This is a best-effort exception that may not always be possible to throw based on the underlying transport.
 */
public class ConnectionClosedException extends TransportException {
    public ConnectionClosedException(String message) {
        super(message);
    }

    public ConnectionClosedException(String message, Throwable cause) {
        super(message, cause);
    }
}
