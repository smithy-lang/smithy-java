/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.error;

import java.net.SocketTimeoutException;

/**
 * Exception thrown when a socket used by the transport times out.
 *
 * <p>This exception is similar to {@link SocketTimeoutException}.
 */
public class TransportSocketTimeout extends TransportSocketException {
    public TransportSocketTimeout(Throwable cause) {
        super(cause);
    }

    public TransportSocketTimeout(String message) {
        super(message);
    }

    public TransportSocketTimeout(String message, Throwable cause) {
        super(message, cause);
    }
}
