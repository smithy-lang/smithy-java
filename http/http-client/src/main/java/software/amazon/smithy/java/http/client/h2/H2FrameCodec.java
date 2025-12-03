/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static software.amazon.smithy.java.http.client.h2.H2Constants.ERROR_FRAME_SIZE_ERROR;
import static software.amazon.smithy.java.http.client.h2.H2Constants.ERROR_PROTOCOL_ERROR;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FLAG_ACK;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FLAG_END_HEADERS;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FLAG_END_STREAM;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FLAG_PADDED;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FLAG_PRIORITY;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_HEADER_SIZE;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_CONTINUATION;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_DATA;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_GOAWAY;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_HEADERS;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_PING;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_PRIORITY;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_PUSH_PROMISE;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_RST_STREAM;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_SETTINGS;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_WINDOW_UPDATE;
import static software.amazon.smithy.java.http.client.h2.H2Constants.frameTypeName;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * HTTP/2 frame encoding and decoding.
 *
 * <p>HTTP/2 frames have a 9-byte header followed by a variable-length payload:
 * <pre>
 * +-----------------------------------------------+
 * |                 Length (24)                   |
 * +---------------+---------------+---------------+
 * |   Type (8)    |   Flags (8)   |
 * +-+-------------+---------------+-------------------------------+
 * |R|                 Stream Identifier (31)                      |
 * +=+=============================================================+
 * |                   Frame Payload (0...)                      ...
 * +---------------------------------------------------------------+
 * </pre>
 */
final class H2FrameCodec {

    private final InputStream in;
    private final OutputStream out;
    private final int maxFrameSize;

    // Separate buffers for reading and writing to avoid concurrent access issues.
    // The reader thread uses readHeaderBuf, while writer threads use writeHeaderBuf.
    private final byte[] readHeaderBuf = new byte[FRAME_HEADER_SIZE];
    private final byte[] writeHeaderBuf = new byte[FRAME_HEADER_SIZE];

    // Scratch buffer for control frames to avoid allocation on hot path.
    // Covers PING(8), SETTINGS(up to ~10 params = 60), WINDOW_UPDATE(4), RST_STREAM(4),
    // PRIORITY(5), and small GOAWAY frames. Larger payloads fall back to allocation.
    private static final int CONTROL_FRAME_SCRATCH_SIZE = 64;
    private final byte[] controlFrameScratch = new byte[CONTROL_FRAME_SCRATCH_SIZE];

    // Shared empty array for zero-length payloads
    private static final byte[] EMPTY_PAYLOAD = new byte[0];

    H2FrameCodec(InputStream in, OutputStream out, int maxFrameSize) {
        this.in = in;
        this.out = out;
        this.maxFrameSize = maxFrameSize;
    }

    /**
     * Read a frame from the input stream.
     *
     * <p><b>Important:</b> For control frames (SETTINGS, PING, WINDOW_UPDATE, RST_STREAM,
     * PRIORITY, GOAWAY), the returned Frame's payload may reference a shared scratch buffer.
     * The payload is only valid until the next call to {@code readFrame()}. Callers must
     * parse control frames immediately before reading the next frame.
     *
     * @return the frame, or null if EOF
     * @throws IOException if reading fails or frame is malformed
     */
    Frame readFrame() throws IOException {
        // Read 9-byte header
        int read = readFully(readHeaderBuf, 0, FRAME_HEADER_SIZE);
        if (read < FRAME_HEADER_SIZE) {
            if (read == 0) {
                return null; // EOF
            }
            throw new IOException("Incomplete frame header: read " + read + " bytes");
        }

        // Parse header
        int length = ((readHeaderBuf[0] & 0xFF) << 16) | ((readHeaderBuf[1] & 0xFF) << 8) | (readHeaderBuf[2] & 0xFF);
        int type = readHeaderBuf[3] & 0xFF;
        int flags = readHeaderBuf[4] & 0xFF;
        int streamId = ((readHeaderBuf[5] & 0x7F) << 24) // Mask off reserved bit
                | ((readHeaderBuf[6] & 0xFF) << 16)
                | ((readHeaderBuf[7] & 0xFF) << 8)
                | (readHeaderBuf[8] & 0xFF);

        // Validate frame size
        if (length > maxFrameSize) {
            throw new H2Exception(ERROR_FRAME_SIZE_ERROR, "Frame size " + length + " exceeds " + maxFrameSize);
        }

        // Validate stream ID requirements per RFC 9113
        validateStreamId(type, streamId);

        // Validate fixed-size frame payloads per RFC 9113
        validateFrameSize(type, flags, length);

        // Read payload - use scratch buffer for control frames to avoid allocation
        byte[] payload;
        if (length == 0) {
            payload = EMPTY_PAYLOAD;
        } else if (isControlFrame(type) && length <= CONTROL_FRAME_SCRATCH_SIZE) {
            // Control frames use scratch buffer (valid until next readFrame call)
            read = readFully(controlFrameScratch, 0, length);
            if (read < length) {
                throw new IOException("Incomplete frame payload: expected " + length + ", read " + read);
            }
            payload = controlFrameScratch;
        } else {
            // DATA, HEADERS, CONTINUATION, PUSH_PROMISE, or large control frames
            payload = new byte[length];
            read = readFully(payload, 0, length);
            if (read < length) {
                throw new IOException("Incomplete frame payload: expected " + length + ", read " + read);
            }
        }

        // Handle padding for DATA, HEADERS, and PUSH_PROMISE frames
        if ((flags & FLAG_PADDED) != 0
                && (type == FRAME_TYPE_DATA || type == FRAME_TYPE_HEADERS || type == FRAME_TYPE_PUSH_PROMISE)) {
            payload = removePadding(payload, type);
            length = payload.length; // Update length after stripping padding
            flags &= ~FLAG_PADDED; // Clear padded flag after processing
        }

        // Handle PRIORITY in HEADERS frame
        if (type == FRAME_TYPE_HEADERS && (flags & FLAG_PRIORITY) != 0) {
            payload = removePriority(payload);
            length = payload.length; // Update length after stripping priority
            flags &= ~FLAG_PRIORITY; // Clear priority flag after processing
        }

        return new Frame(type, flags, streamId, payload, length);
    }

    /**
     * Check if frame type is a control frame (processed immediately, payload doesn't escape).
     */
    private static boolean isControlFrame(int type) {
        return type == FRAME_TYPE_SETTINGS
                || type == FRAME_TYPE_PING
                || type == FRAME_TYPE_WINDOW_UPDATE
                || type == FRAME_TYPE_RST_STREAM
                || type == FRAME_TYPE_PRIORITY
                || type == FRAME_TYPE_GOAWAY;
    }

    /**
     * Read a complete header block, handling CONTINUATION frames.
     *
     * <p>Per RFC 9113 Section 4.3, a header block must be transmitted as a contiguous
     * sequence of frames with no interleaved frames of any other type or from any other stream.
     *
     * @param initialFrame the initial HEADERS or PUSH_PROMISE frame
     * @return the complete header block payload
     * @throws IOException if reading fails
     */
    byte[] readHeaderBlock(Frame initialFrame) throws IOException {
        byte[] initialPayload = initialFrame.payload();
        int initialLength = initialFrame.payloadLength();

        // For PUSH_PROMISE, strip the 4-byte promised stream ID to get the header block fragment
        if (initialFrame.type() == FRAME_TYPE_PUSH_PROMISE && initialPayload != null) {
            if (initialLength < 4) {
                throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                        "PUSH_PROMISE frame payload too short for promised stream ID");
            }
            int fragmentLength = initialLength - 4;
            byte[] fragment = new byte[fragmentLength];
            System.arraycopy(initialPayload, 4, fragment, 0, fragmentLength);
            initialPayload = fragment;
            initialLength = fragmentLength;
        }

        if (initialFrame.hasFlag(FLAG_END_HEADERS)) {
            return initialPayload != null ? initialPayload : EMPTY_PAYLOAD;
        }

        // Need to read CONTINUATION frames
        ByteArrayOutputStream headerBlock = new ByteArrayOutputStream(initialLength);
        if (initialPayload != null) {
            headerBlock.write(initialPayload);
        }

        while (true) {
            Frame cont = readFrame();
            if (cont == null) {
                throw new IOException("EOF while reading CONTINUATION frames");
            }

            // Per RFC 9113 Section 4.3: header block must be contiguous
            // Only CONTINUATION frames for the same stream are allowed
            if (cont.type() != FRAME_TYPE_CONTINUATION) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR,
                        "Header block interrupted by " + frameTypeName(cont.type()) +
                                " frame (RFC 9113 Section 4.3 violation)");
            }

            if (cont.streamId() != initialFrame.streamId()) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR,
                        "CONTINUATION frame stream ID mismatch: expected " +
                                initialFrame.streamId() + ", got " + cont.streamId());
            }

            byte[] contPayload = cont.payload();
            if (contPayload != null) {
                headerBlock.write(contPayload);
            }

            if (cont.hasFlag(FLAG_END_HEADERS)) {
                break;
            }
        }

        return headerBlock.toByteArray();
    }

    private void validateFrameSize(int type, int flags, int length) throws H2Exception {
        switch (type) {
            case FRAME_TYPE_PING:
                // PING frames MUST have exactly 8 bytes payload
                if (length != 8) {
                    throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                            "PING frame must have 8-byte payload, got " + length);
                }
                break;

            case FRAME_TYPE_SETTINGS:
                // SETTINGS with ACK flag MUST have empty payload
                if ((flags & FLAG_ACK) != 0 && length != 0) {
                    throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                            "SETTINGS ACK frame must have empty payload, got " + length);
                }
                // SETTINGS payload must be multiple of 6 (validated in parseSettings)
                break;

            case FRAME_TYPE_WINDOW_UPDATE:
                // WINDOW_UPDATE frames MUST have exactly 4 bytes payload
                if (length != 4) {
                    throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                            "WINDOW_UPDATE frame must have 4-byte payload, got " + length);
                }
                break;

            case FRAME_TYPE_RST_STREAM:
                // RST_STREAM frames MUST have exactly 4 bytes payload
                if (length != 4) {
                    throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                            "RST_STREAM frame must have 4-byte payload, got " + length);
                }
                break;

            case FRAME_TYPE_PRIORITY:
                // PRIORITY frames MUST have exactly 5 bytes payload
                if (length != 5) {
                    throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                            "PRIORITY frame must have 5-byte payload, got " + length);
                }
                break;

            case FRAME_TYPE_GOAWAY:
                // GOAWAY frames MUST have at least 8 bytes payload
                if (length < 8) {
                    throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                            "GOAWAY frame must have at least 8-byte payload, got " + length);
                }
                break;

            case FRAME_TYPE_PUSH_PROMISE:
                // PUSH_PROMISE must have at least 4 bytes for the promised stream ID
                // (plus 1 byte for pad length if PADDED flag is set)
                int minLength = (flags & FLAG_PADDED) != 0 ? 5 : 4;
                if (length < minLength) {
                    throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                            "PUSH_PROMISE frame must have at least " + minLength + "-byte payload, got " + length);
                }
                break;

            default:
                // Other frame types have variable-length payloads
                break;
        }
    }

    /**
     * Validate stream ID requirements per RFC 9113.
     */
    private void validateStreamId(int type, int streamId) throws H2Exception {
        switch (type) {
            case FRAME_TYPE_DATA:
            case FRAME_TYPE_HEADERS:
            case FRAME_TYPE_PRIORITY:
            case FRAME_TYPE_RST_STREAM:
            case FRAME_TYPE_CONTINUATION:
                // These frames MUST be associated with a stream
                if (streamId == 0) {
                    throw new H2Exception(ERROR_PROTOCOL_ERROR,
                            frameTypeName(type) + " frame must have non-zero stream ID");
                }
                break;

            case FRAME_TYPE_SETTINGS:
            case FRAME_TYPE_PING:
            case FRAME_TYPE_GOAWAY:
                // These frames MUST NOT be associated with a stream
                if (streamId != 0) {
                    throw new H2Exception(ERROR_PROTOCOL_ERROR,
                            frameTypeName(type) + " frame must have stream ID 0, got " + streamId);
                }
                break;

            case FRAME_TYPE_WINDOW_UPDATE:
                // Can be on connection (0) or stream (non-zero)
                break;

            case FRAME_TYPE_PUSH_PROMISE:
                // Must be on a stream
                if (streamId == 0) {
                    throw new H2Exception(ERROR_PROTOCOL_ERROR,
                            "PUSH_PROMISE frame must have non-zero stream ID");
                }
                break;

            default:
                // Unknown frame types - ignore per RFC 9113
                break;
        }
    }

    /**
     * Remove padding from a padded frame payload.
     */
    private byte[] removePadding(byte[] payload, int frameType) throws H2Exception {
        if (payload.length < 1) {
            throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                    "Padded " + frameTypeName(frameType) + " frame too short");
        }

        int padLength = payload[0] & 0xFF;
        if (padLength >= payload.length) {
            throw new H2Exception(ERROR_PROTOCOL_ERROR,
                    "Pad length " + padLength + " exceeds payload length " + payload.length);
        }

        int dataLength = payload.length - 1 - padLength;
        byte[] data = new byte[dataLength];
        System.arraycopy(payload, 1, data, 0, dataLength);
        return data;
    }

    /**
     * Remove PRIORITY fields from HEADERS frame payload.
     */
    private byte[] removePriority(byte[] payload) throws H2Exception {
        // PRIORITY adds 5 bytes: 4-byte stream dependency + 1-byte weight
        if (payload.length < 5) {
            throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                    "HEADERS frame with PRIORITY flag too short");
        }

        byte[] data = new byte[payload.length - 5];
        System.arraycopy(payload, 5, data, 0, data.length);
        return data;
    }

    /**
     * Write a frame to the output stream.
     *
     * @param type frame type
     * @param flags frame flags
     * @param streamId stream identifier
     * @param payload frame payload (may be null or empty)
     * @throws IOException if writing fails
     */
    void writeFrame(int type, int flags, int streamId, byte[] payload) throws IOException {
        writeFrame(type, flags, streamId, payload, 0, payload != null ? payload.length : 0);
    }

    /**
     * Write a frame to the output stream.
     *
     * <p>This method is NOT synchronized. Callers must ensure exclusive access
     * to the output stream (e.g., via H2Connection's writer thread).
     *
     * @param type frame type
     * @param flags frame flags
     * @param streamId stream identifier
     * @param payload frame payload buffer
     * @param offset offset in payload buffer
     * @param length number of bytes to write from payload
     * @throws IOException if writing fails
     */
    void writeFrame(
            int type,
            int flags,
            int streamId,
            byte[] payload,
            int offset,
            int length
    ) throws IOException {
        // Validate stream ID is a valid 31-bit unsigned value
        if (streamId < 0) {
            throw new IllegalArgumentException("Invalid stream ID: " + streamId);
        }

        if (length > maxFrameSize) {
            throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                    "Frame payload size " + length + " exceeds maximum " + maxFrameSize);
        }

        // Write header (using writeHeaderBuf - caller must ensure exclusive access)
        writeHeaderBuf[0] = (byte) ((length >> 16) & 0xFF);
        writeHeaderBuf[1] = (byte) ((length >> 8) & 0xFF);
        writeHeaderBuf[2] = (byte) (length & 0xFF);
        writeHeaderBuf[3] = (byte) type;
        writeHeaderBuf[4] = (byte) flags;
        writeHeaderBuf[5] = (byte) ((streamId >> 24) & 0x7F); // Clear reserved bit
        writeHeaderBuf[6] = (byte) ((streamId >> 16) & 0xFF);
        writeHeaderBuf[7] = (byte) ((streamId >> 8) & 0xFF);
        writeHeaderBuf[8] = (byte) (streamId & 0xFF);

        out.write(writeHeaderBuf);

        // Write payload
        if (length > 0 && payload != null) {
            out.write(payload, offset, length);
        }
    }

    /**
     * Write HEADERS frame, splitting into CONTINUATION frames if needed.
     */
    void writeHeaders(int streamId, byte[] headerBlock, int offset, int length, boolean endStream) throws IOException {
        if (length <= maxFrameSize) {
            // Fits in single frame
            int flags = FLAG_END_HEADERS;
            if (endStream) {
                flags |= FLAG_END_STREAM;
            }
            writeFrame(FRAME_TYPE_HEADERS, flags, streamId, headerBlock, offset, length);
        } else {
            // Need to split across HEADERS + CONTINUATION frames
            int pos = offset;
            int end = offset + length;

            // First frame: HEADERS (no END_HEADERS flag)
            int firstFlags = endStream ? FLAG_END_STREAM : 0;
            writeFrame(FRAME_TYPE_HEADERS, firstFlags, streamId, headerBlock, pos, maxFrameSize);
            pos += maxFrameSize;

            // Middle frames: CONTINUATION (no END_HEADERS flag)
            while (pos + maxFrameSize < end) {
                writeFrame(FRAME_TYPE_CONTINUATION, 0, streamId, headerBlock, pos, maxFrameSize);
                pos += maxFrameSize;
            }

            // Last frame: CONTINUATION with END_HEADERS
            int remaining = end - pos;
            writeFrame(FRAME_TYPE_CONTINUATION, FLAG_END_HEADERS, streamId, headerBlock, pos, remaining);
        }
    }

    /**
     * Write SETTINGS frame.
     */
    void writeSettings(int... settings) throws IOException {
        if (settings.length % 2 != 0) {
            throw new IllegalArgumentException("Settings must be id-value pairs");
        }

        // Each pair is 2 ints (id + value) and encodes to 6 bytes (2 + 4)
        byte[] payload = new byte[settings.length * 3];
        int pos = 0;
        for (int i = 0; i < settings.length; i += 2) {
            int id = settings[i];
            int value = settings[i + 1];
            payload[pos++] = (byte) ((id >> 8) & 0xFF);
            payload[pos++] = (byte) (id & 0xFF);
            payload[pos++] = (byte) ((value >> 24) & 0xFF);
            payload[pos++] = (byte) ((value >> 16) & 0xFF);
            payload[pos++] = (byte) ((value >> 8) & 0xFF);
            payload[pos++] = (byte) (value & 0xFF);
        }

        writeFrame(FRAME_TYPE_SETTINGS, 0, 0, payload);
    }

    /**
     * Write SETTINGS acknowledgment.
     */
    void writeSettingsAck() throws IOException {
        writeFrame(FRAME_TYPE_SETTINGS, FLAG_ACK, 0, null);
    }

    /**
     * Write GOAWAY frame.
     */
    void writeGoaway(int lastStreamId, int errorCode, String debugData) throws IOException {
        byte[] debug = debugData != null ? debugData.getBytes(StandardCharsets.UTF_8) : EMPTY_PAYLOAD;
        byte[] payload = new byte[8 + debug.length];

        payload[0] = (byte) ((lastStreamId >> 24) & 0x7F);
        payload[1] = (byte) ((lastStreamId >> 16) & 0xFF);
        payload[2] = (byte) ((lastStreamId >> 8) & 0xFF);
        payload[3] = (byte) (lastStreamId & 0xFF);
        payload[4] = (byte) ((errorCode >> 24) & 0xFF);
        payload[5] = (byte) ((errorCode >> 16) & 0xFF);
        payload[6] = (byte) ((errorCode >> 8) & 0xFF);
        payload[7] = (byte) (errorCode & 0xFF);

        System.arraycopy(debug, 0, payload, 8, debug.length);

        writeFrame(FRAME_TYPE_GOAWAY, 0, 0, payload);
    }

    /**
     * Write WINDOW_UPDATE frame.
     * Uses scratch buffer - caller must have exclusive access (writer thread).
     */
    void writeWindowUpdate(int streamId, int windowSizeIncrement) throws IOException {
        if (windowSizeIncrement <= 0) {
            throw new IllegalArgumentException("Invalid window size increment: " + windowSizeIncrement);
        }

        // Use scratch buffer to avoid allocation
        controlFrameScratch[0] = (byte) ((windowSizeIncrement >> 24) & 0x7F);
        controlFrameScratch[1] = (byte) ((windowSizeIncrement >> 16) & 0xFF);
        controlFrameScratch[2] = (byte) ((windowSizeIncrement >> 8) & 0xFF);
        controlFrameScratch[3] = (byte) (windowSizeIncrement & 0xFF);
        writeFrame(FRAME_TYPE_WINDOW_UPDATE, 0, streamId, controlFrameScratch, 0, 4);
    }

    /**
     * Write RST_STREAM frame.
     * Uses scratch buffer - caller must have exclusive access (writer thread).
     */
    void writeRstStream(int streamId, int errorCode) throws IOException {
        // Use scratch buffer to avoid allocation
        controlFrameScratch[0] = (byte) ((errorCode >> 24) & 0xFF);
        controlFrameScratch[1] = (byte) ((errorCode >> 16) & 0xFF);
        controlFrameScratch[2] = (byte) ((errorCode >> 8) & 0xFF);
        controlFrameScratch[3] = (byte) (errorCode & 0xFF);
        writeFrame(FRAME_TYPE_RST_STREAM, 0, streamId, controlFrameScratch, 0, 4);
    }

    /**
     * Flush the output stream.
     *
     * <p>Caller must ensure exclusive access to the output stream.
     */
    void flush() throws IOException {
        out.flush();
    }

    private int readFully(byte[] buf, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int n = in.read(buf, off + total, len - total);
            if (n < 0) {
                break;
            }
            total += n;
        }
        return total;
    }

    /**
     * Represents an HTTP/2 frame.
     *
     * <p><b>Note:</b> For control frames, the payload array may be a shared scratch buffer
     * that is larger than the actual payload. Always use {@link #payloadLength()} to get
     * the actual payload size, not {@code payload.length}.
     *
     * @param type frame type
     * @param flags frame flags
     * @param streamId stream identifier
     * @param payload payload bytes (may be shared scratch buffer for control frames)
     * @param length actual payload length (may be less than payload.length for scratch buffer)
     */
    record Frame(int type, int flags, int streamId, byte[] payload, int length) {

        boolean hasFlag(int flag) {
            return (flags & flag) != 0;
        }

        int payloadLength() {
            return length;
        }

        /**
         * Parse SETTINGS frame payload.
         *
         * @return array of {id, value} pairs
         * @throws H2Exception if frame is invalid
         */
        int[] parseSettings() throws H2Exception {
            if (type != FRAME_TYPE_SETTINGS) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR,
                        "Expected SETTINGS frame, got " + frameTypeName(type));
            }
            if (payload == null || length == 0) {
                return new int[0];
            }

            // SETTINGS payload MUST be a multiple of 6 bytes (RFC 9113 Section 6.5)
            if (length % 6 != 0) {
                throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                        "SETTINGS frame payload length " + length + " is not a multiple of 6");
            }

            int count = length / 6;
            int[] settings = new int[count * 2];
            int pos = 0;
            for (int i = 0; i < count; i++) {
                int id = ((payload[pos] & 0xFF) << 8) | (payload[pos + 1] & 0xFF);
                int value = ((payload[pos + 2] & 0xFF) << 24)
                        | ((payload[pos + 3] & 0xFF) << 16)
                        | ((payload[pos + 4] & 0xFF) << 8)
                        | (payload[pos + 5] & 0xFF);
                settings[i * 2] = id;
                settings[i * 2 + 1] = value;
                pos += 6;
            }
            return settings;
        }

        /**
         * Parse GOAWAY frame payload.
         *
         * @return {lastStreamId, errorCode}
         * @throws H2Exception if frame is invalid
         */
        int[] parseGoaway() throws H2Exception {
            if (type != FRAME_TYPE_GOAWAY) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR, "Expected GOAWAY frame, got " + frameTypeName(type));
            } else if (payload == null || length < 8) {
                throw new H2Exception(ERROR_FRAME_SIZE_ERROR, "GOAWAY frame payload too short: " + length);
            }

            int lastStreamId = ((payload[0] & 0x7F) << 24)
                    | ((payload[1] & 0xFF) << 16)
                    | ((payload[2] & 0xFF) << 8)
                    | (payload[3] & 0xFF);
            int errorCode = ((payload[4] & 0xFF) << 24)
                    | ((payload[5] & 0xFF) << 16)
                    | ((payload[6] & 0xFF) << 8)
                    | (payload[7] & 0xFF);
            return new int[] {lastStreamId, errorCode};
        }

        /**
         * Parse WINDOW_UPDATE frame payload.
         *
         * @return window size increment
         * @throws H2Exception if frame is invalid or increment is zero
         */
        int parseWindowUpdate() throws H2Exception {
            if (type != FRAME_TYPE_WINDOW_UPDATE) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR,
                        "Expected WINDOW_UPDATE frame, got " + frameTypeName(type));
            }
            if (payload == null || length != 4) {
                throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                        "WINDOW_UPDATE frame must have 4-byte payload, got " + length);
            }

            int increment = ((payload[0] & 0x7F) << 24)
                    | ((payload[1] & 0xFF) << 16)
                    | ((payload[2] & 0xFF) << 8)
                    | (payload[3] & 0xFF);

            if (increment == 0) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR, "WINDOW_UPDATE increment must be non-zero");
            }

            return increment;
        }

        /**
         * Parse RST_STREAM frame payload.
         *
         * @return error code
         * @throws H2Exception if frame is invalid
         */
        int parseRstStream() throws H2Exception {
            if (type != FRAME_TYPE_RST_STREAM) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR,
                        "Expected RST_STREAM frame, got " + frameTypeName(type));
            }
            if (payload == null || length != 4) {
                throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                        "RST_STREAM frame must have 4-byte payload, got " + length);
            }

            return ((payload[0] & 0xFF) << 24)
                    | ((payload[1] & 0xFF) << 16)
                    | ((payload[2] & 0xFF) << 8)
                    | (payload[3] & 0xFF);
        }

        @Override
        public String toString() {
            return String.format("Frame{type=%s, flags=0x%02x, streamId=%d, length=%d}",
                    frameTypeName(type),
                    flags,
                    streamId,
                    length);
        }
    }
}
