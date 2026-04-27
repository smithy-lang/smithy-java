/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;

final class ConnectionAgentH2Exception extends IOException {

    private final int errorCode;
    private final int streamId;

    ConnectionAgentH2Exception(int errorCode, String message) {
        super(message + " (" + errorCode + ")");
        this.errorCode = errorCode;
        this.streamId = 0;
    }

    ConnectionAgentH2Exception(int errorCode, int streamId, String message) {
        super("Stream " + streamId + ": " + message + " (" + errorCode + ")");
        this.errorCode = errorCode;
        this.streamId = streamId;
    }

    int errorCode() {
        return errorCode;
    }

    int streamId() {
        return streamId;
    }
}
