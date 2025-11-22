/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.h2;

import software.amazon.smithy.java.client.core.error.TransportException;

/**
 * A marker exception used to propagate an HTTP2 connection terminating event.
 */
final class Http2ConnectionTerminatingException extends TransportException {
    public Http2ConnectionTerminatingException(String message, Throwable cause) {
        super(message, cause);
    }
}
