/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.nio.charset.StandardCharsets;

/**
 * Benchmark transport copy of the production H2 constants so the connection-agent branch can port
 * production protocol logic without taking a dependency on package-private implementation classes.
 */
final class ConnectionAgentH2Constants {

    private ConnectionAgentH2Constants() {}

    static final byte[] CONNECTION_PREFACE =
            "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    static final int FRAME_HEADER_SIZE = 9;

    static final int FRAME_TYPE_DATA = 0x0;
    static final int FRAME_TYPE_HEADERS = 0x1;
    static final int FRAME_TYPE_RST_STREAM = 0x3;
    static final int FRAME_TYPE_SETTINGS = 0x4;
    static final int FRAME_TYPE_PING = 0x6;
    static final int FRAME_TYPE_GOAWAY = 0x7;
    static final int FRAME_TYPE_WINDOW_UPDATE = 0x8;

    static final int FLAG_END_STREAM = 0x1;
    static final int FLAG_END_HEADERS = 0x4;
    static final int FLAG_ACK = 0x1;

    static final int DEFAULT_INITIAL_WINDOW_SIZE = 65535;
    static final int DEFAULT_MAX_FRAME_SIZE = 16384;

    static final String PSEUDO_METHOD = ":method";
    static final String PSEUDO_SCHEME = ":scheme";
    static final String PSEUDO_AUTHORITY = ":authority";
    static final String PSEUDO_PATH = ":path";
    static final String PSEUDO_STATUS = ":status";

    static String frameTypeName(int type) {
        return switch (type) {
            case FRAME_TYPE_DATA -> "DATA";
            case FRAME_TYPE_HEADERS -> "HEADERS";
            case FRAME_TYPE_RST_STREAM -> "RST_STREAM";
            case FRAME_TYPE_SETTINGS -> "SETTINGS";
            case FRAME_TYPE_PING -> "PING";
            case FRAME_TYPE_GOAWAY -> "GOAWAY";
            case FRAME_TYPE_WINDOW_UPDATE -> "WINDOW_UPDATE";
            default -> "UNKNOWN(" + type + ")";
        };
    }
}
