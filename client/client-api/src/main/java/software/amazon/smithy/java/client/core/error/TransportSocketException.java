/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.error;

import java.net.SocketException;

/**
 * Thrown to indicate that there is an error creating or accessing a Socket.
 *
 * <p>This exception is similar to {@link SocketException}.
 */
public class TransportSocketException extends TransportException {
    public TransportSocketException(Throwable cause) {
        super(cause);
    }

    public TransportSocketException(String message) {
        super(message);
    }

    public TransportSocketException(String message, Throwable cause) {
        super(message, cause);
    }
}
