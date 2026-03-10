/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.error;

import javax.net.ssl.SSLException;

/**
 * Exception thrown when TLS negotiation fails.
 *
 * <p>This exception is similar to {@link SSLException}.
 */
public class TlsException extends TransportProtocolException {
    public TlsException(Throwable cause) {
        super(cause);
    }

    public TlsException(String message) {
        super(message);
    }

    public TlsException(String message, Throwable cause) {
        super(message, cause);
    }
}
