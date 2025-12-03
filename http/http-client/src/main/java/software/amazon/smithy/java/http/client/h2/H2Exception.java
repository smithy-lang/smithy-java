/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static software.amazon.smithy.java.http.client.h2.H2Constants.errorCodeName;

import java.io.IOException;

/**
 * Exception representing an HTTP/2 protocol error.
 *
 * <p>This exception carries an HTTP/2 error code that can be used to send
 * RST_STREAM or GOAWAY frames to the peer.
 */
public final class H2Exception extends IOException {

    private final int errorCode;
    private final int streamId;

    /**
     * Create a connection-level HTTP/2 exception.
     *
     * @param errorCode HTTP/2 error code
     * @param message error message
     */
    public H2Exception(int errorCode, String message) {
        super(message + " (" + errorCodeName(errorCode) + ")");
        this.errorCode = errorCode;
        this.streamId = 0;
    }

    /**
     * Create a stream-level HTTP/2 exception.
     *
     * @param errorCode HTTP/2 error code
     * @param streamId affected stream ID
     * @param message error message
     */
    public H2Exception(int errorCode, int streamId, String message) {
        super("Stream " + streamId + ": " + message + " (" + errorCodeName(errorCode) + ")");
        this.errorCode = errorCode;
        this.streamId = streamId;
    }

    /**
     * Get the HTTP/2 error code.
     *
     * @return error code
     */
    public int errorCode() {
        return errorCode;
    }

    /**
     * Get the affected stream ID.
     *
     * @return stream ID, or 0 for connection-level errors
     */
    public int streamId() {
        return streamId;
    }

    /**
     * Whether this is a connection-level error.
     *
     * @return true if connection-level, false if stream-level
     */
    public boolean isConnectionError() {
        return streamId == 0;
    }
}
