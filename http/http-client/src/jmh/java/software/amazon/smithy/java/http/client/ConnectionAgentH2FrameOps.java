/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

final class ConnectionAgentH2FrameOps {

    private ConnectionAgentH2FrameOps() {}

    static void validateFrameSize(int type, int flags, int length) throws ConnectionAgentH2Exception {
        switch (type) {
            case ConnectionAgentH2Constants.FRAME_TYPE_PING:
                if (length != 8) {
                    throw new ConnectionAgentH2Exception(0x6, "PING frame must have 8-byte payload, got " + length);
                }
                break;
            case ConnectionAgentH2Constants.FRAME_TYPE_SETTINGS:
                if ((flags & ConnectionAgentH2Constants.FLAG_ACK) != 0 && length != 0) {
                    throw new ConnectionAgentH2Exception(0x6,
                            "SETTINGS ACK frame must have empty payload, got " + length);
                }
                break;
            case ConnectionAgentH2Constants.FRAME_TYPE_WINDOW_UPDATE:
            case ConnectionAgentH2Constants.FRAME_TYPE_RST_STREAM:
                if (length != 4) {
                    throw new ConnectionAgentH2Exception(0x6,
                            ConnectionAgentH2Constants.frameTypeName(type)
                                    + " frame must have 4-byte payload, got " + length);
                }
                break;
            case ConnectionAgentH2Constants.FRAME_TYPE_GOAWAY:
                if (length < 8) {
                    throw new ConnectionAgentH2Exception(0x6, "GOAWAY frame must have at least 8-byte payload, got " + length);
                }
                break;
            default:
                break;
        }
    }

    static void validateStreamId(int type, int streamId) throws ConnectionAgentH2Exception {
        switch (type) {
            case ConnectionAgentH2Constants.FRAME_TYPE_DATA:
            case ConnectionAgentH2Constants.FRAME_TYPE_HEADERS:
            case ConnectionAgentH2Constants.FRAME_TYPE_RST_STREAM:
                if (streamId == 0) {
                    throw new ConnectionAgentH2Exception(0x1,
                            ConnectionAgentH2Constants.frameTypeName(type) + " frame must have non-zero stream ID");
                }
                break;
            case ConnectionAgentH2Constants.FRAME_TYPE_SETTINGS:
            case ConnectionAgentH2Constants.FRAME_TYPE_PING:
            case ConnectionAgentH2Constants.FRAME_TYPE_GOAWAY:
                if (streamId != 0) {
                    throw new ConnectionAgentH2Exception(0x1,
                            ConnectionAgentH2Constants.frameTypeName(type) + " frame must have stream ID 0");
                }
                break;
            default:
                break;
        }
    }
}
