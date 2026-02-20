/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.nio.charset.StandardCharsets;

/**
 * HTTP/2 protocol constants from RFC 9113.
 */
final class H2Constants {

    private H2Constants() {}

    // Shared empty byte array to avoid repeated allocations
    static final byte[] EMPTY_BYTES = new byte[0];

    // Our limit for received header list size (not from server SETTINGS)
    static final int DEFAULT_MAX_HEADER_LIST_SIZE = 8192;

    // Connection preface - client must send this first (RFC 9113 Section 3.4)
    static final byte[] CONNECTION_PREFACE =
            "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    // Frame header size is always 9 bytes
    static final int FRAME_HEADER_SIZE = 9;

    // Frame types (RFC 9113 Section 6)
    static final int FRAME_TYPE_DATA = 0x0;
    static final int FRAME_TYPE_HEADERS = 0x1;
    static final int FRAME_TYPE_PRIORITY = 0x2;
    static final int FRAME_TYPE_RST_STREAM = 0x3;
    static final int FRAME_TYPE_SETTINGS = 0x4;
    static final int FRAME_TYPE_PUSH_PROMISE = 0x5;
    static final int FRAME_TYPE_PING = 0x6;
    static final int FRAME_TYPE_GOAWAY = 0x7;
    static final int FRAME_TYPE_WINDOW_UPDATE = 0x8;
    static final int FRAME_TYPE_CONTINUATION = 0x9;

    // Frame flags
    static final int FLAG_END_STREAM = 0x1; // DATA, HEADERS
    static final int FLAG_END_HEADERS = 0x4; // HEADERS, PUSH_PROMISE, CONTINUATION
    static final int FLAG_PADDED = 0x8; // DATA, HEADERS, PUSH_PROMISE
    static final int FLAG_PRIORITY = 0x20; // HEADERS
    static final int FLAG_ACK = 0x1; // SETTINGS, PING

    // Settings identifiers (RFC 9113 Section 6.5.2)
    static final int SETTINGS_HEADER_TABLE_SIZE = 0x1;
    static final int SETTINGS_ENABLE_PUSH = 0x2;
    static final int SETTINGS_MAX_CONCURRENT_STREAMS = 0x3;
    static final int SETTINGS_INITIAL_WINDOW_SIZE = 0x4;
    static final int SETTINGS_MAX_FRAME_SIZE = 0x5;
    static final int SETTINGS_MAX_HEADER_LIST_SIZE = 0x6;

    // Default settings values
    static final int DEFAULT_HEADER_TABLE_SIZE = 4096;
    static final int DEFAULT_MAX_CONCURRENT_STREAMS = Integer.MAX_VALUE;
    static final int DEFAULT_INITIAL_WINDOW_SIZE = 65535;
    static final int DEFAULT_MAX_FRAME_SIZE = 16384;

    // Frame size limits
    static final int MIN_MAX_FRAME_SIZE = 16384; // 2^14
    static final int MAX_MAX_FRAME_SIZE = 16777215; // 2^24 - 1

    // WINDOW_UPDATE threshold: send update when window drops below this fraction of initial size.
    // Using 1/3 (33%) reduces control frame overhead while leaving enough buffer to avoid stalls.
    static final int WINDOW_UPDATE_THRESHOLD_DIVISOR = 3;

    // Error codes (RFC 9113 Section 7)
    static final int ERROR_NO_ERROR = 0x0;
    static final int ERROR_PROTOCOL_ERROR = 0x1;
    static final int ERROR_INTERNAL_ERROR = 0x2;
    static final int ERROR_FLOW_CONTROL_ERROR = 0x3;
    static final int ERROR_SETTINGS_TIMEOUT = 0x4;
    static final int ERROR_STREAM_CLOSED = 0x5;
    static final int ERROR_FRAME_SIZE_ERROR = 0x6;
    static final int ERROR_REFUSED_STREAM = 0x7;
    static final int ERROR_CANCEL = 0x8;
    static final int ERROR_COMPRESSION_ERROR = 0x9;
    static final int ERROR_CONNECT_ERROR = 0xa;
    static final int ERROR_ENHANCE_YOUR_CALM = 0xb;
    static final int ERROR_INADEQUATE_SECURITY = 0xc;
    static final int ERROR_HTTP_1_1_REQUIRED = 0xd;

    // Pseudo-header field names
    static final String PSEUDO_METHOD = ":method";
    static final String PSEUDO_SCHEME = ":scheme";
    static final String PSEUDO_AUTHORITY = ":authority";
    static final String PSEUDO_PATH = ":path";
    static final String PSEUDO_STATUS = ":status";

    /**
     * Get error code name for debugging.
     */
    static String errorCodeName(int code) {
        return switch (code) {
            case ERROR_NO_ERROR -> "NO_ERROR";
            case ERROR_PROTOCOL_ERROR -> "PROTOCOL_ERROR";
            case ERROR_INTERNAL_ERROR -> "INTERNAL_ERROR";
            case ERROR_FLOW_CONTROL_ERROR -> "FLOW_CONTROL_ERROR";
            case ERROR_SETTINGS_TIMEOUT -> "SETTINGS_TIMEOUT";
            case ERROR_STREAM_CLOSED -> "STREAM_CLOSED";
            case ERROR_FRAME_SIZE_ERROR -> "FRAME_SIZE_ERROR";
            case ERROR_REFUSED_STREAM -> "REFUSED_STREAM";
            case ERROR_CANCEL -> "CANCEL";
            case ERROR_COMPRESSION_ERROR -> "COMPRESSION_ERROR";
            case ERROR_CONNECT_ERROR -> "CONNECT_ERROR";
            case ERROR_ENHANCE_YOUR_CALM -> "ENHANCE_YOUR_CALM";
            case ERROR_INADEQUATE_SECURITY -> "INADEQUATE_SECURITY";
            case ERROR_HTTP_1_1_REQUIRED -> "HTTP_1_1_REQUIRED";
            default -> "UNKNOWN(" + code + ")";
        };
    }

    /**
     * Get frame type name for debugging.
     */
    static String frameTypeName(int type) {
        return switch (type) {
            case FRAME_TYPE_DATA -> "DATA";
            case FRAME_TYPE_HEADERS -> "HEADERS";
            case FRAME_TYPE_PRIORITY -> "PRIORITY";
            case FRAME_TYPE_RST_STREAM -> "RST_STREAM";
            case FRAME_TYPE_SETTINGS -> "SETTINGS";
            case FRAME_TYPE_PUSH_PROMISE -> "PUSH_PROMISE";
            case FRAME_TYPE_PING -> "PING";
            case FRAME_TYPE_GOAWAY -> "GOAWAY";
            case FRAME_TYPE_WINDOW_UPDATE -> "WINDOW_UPDATE";
            case FRAME_TYPE_CONTINUATION -> "CONTINUATION";
            default -> "UNKNOWN(" + type + ")";
        };
    }
}
