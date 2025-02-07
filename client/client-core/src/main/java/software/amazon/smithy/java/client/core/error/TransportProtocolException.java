/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.error;

import java.net.ProtocolException;

/**
 * The client encountered an underlying protocol error (e.g., TCP, HTTP, MQTT, etc).
 *
 * <p>This error is not retryable as the client does not explicitly know if the error occurred after state was changed
 * on the server.
 *
 * <p>This exception is similar to {@link ProtocolException}.
 */
public class TransportProtocolException extends TransportException {
    public TransportProtocolException(Throwable cause) {
        super(cause);
    }

    public TransportProtocolException(String message) {
        super(message);
    }

    public TransportProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
