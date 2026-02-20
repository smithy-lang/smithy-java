/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static software.amazon.smithy.java.http.client.h2.H2Constants.CONNECTION_PREFACE;
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

import java.io.IOException;
import software.amazon.smithy.java.http.client.UnsyncBufferedInputStream;
import software.amazon.smithy.java.http.client.UnsyncBufferedOutputStream;
import software.amazon.smithy.java.io.ByteBufferOutputStream;

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

    private final UnsyncBufferedInputStream in;
    private final UnsyncBufferedOutputStream out;
    private final int maxFrameSize;

    // Write header buffer - used by writer thread only.
    private final byte[] writeHeaderBuf = new byte[FRAME_HEADER_SIZE];

    // Scratch buffer for writing control frames - writer thread only.
    private static final int WRITE_SCRATCH_SIZE = 64;
    private final byte[] writeScratch = new byte[WRITE_SCRATCH_SIZE];

    // Reusable buffer for accumulating header blocks when CONTINUATION frames are needed.
    private final ByteBufferOutputStream headerBlockBuffer = new ByteBufferOutputStream(4096);

    // Current frame state (filled by nextFrame()) - stateful parser pattern
    private int currentType;
    private int currentFlags;
    private int currentStreamId;
    private int currentPayloadLength;

    H2FrameCodec(UnsyncBufferedInputStream in, UnsyncBufferedOutputStream out, int maxFrameSize) {
        this.in = in;
        this.out = out;
        this.maxFrameSize = maxFrameSize;
    }

    /**
     * Write the HTTP/2 connection preface (RFC 9113 Section 3.4).
     * This must be sent by the client before any frames.
     */
    void writeConnectionPreface() throws IOException {
        out.write(CONNECTION_PREFACE);
    }

    // ==================== Stateful Parser API ====================

    /**
     * Read the next frame header and store state internally.
     *
     * <p>After this call, use {@link #frameType()}, {@link #frameStreamId()},
     * {@link #frameFlags()}, {@link #framePayloadLength()}, and {@link #hasFrameFlag(int)}
     * to access the current frame's metadata.
     *
     * <p>The payload must be read via {@link #readPayloadInto(byte[], int, int)} or
     * {@link #skipBytes(int)} before calling {@code nextFrame()} again.
     *
     * <p>This method uses zero-copy direct buffer access for frame header parsing,
     * avoiding intermediate copies when possible.
     *
     * @return frame type (0-255), or -1 on EOF
     * @throws IOException if reading fails or frame is malformed
     */
    int nextFrame() throws IOException {
        // Zero-copy: ensure 9 bytes in buffer, then parse directly
        if (!in.ensure(FRAME_HEADER_SIZE)) {
            // EOF or incomplete header
            if (in.buffered() == 0) {
                return -1; // Clean EOF
            }
            throw new IOException("Incomplete frame header: read " + in.buffered() + " bytes");
        }

        // Parse header directly from input buffer (zero-copy)
        byte[] buf = in.buffer();
        int p = in.position();

        currentPayloadLength = ((buf[p] & 0xFF) << 16)
                | ((buf[p + 1] & 0xFF) << 8)
                | (buf[p + 2] & 0xFF);
        currentType = buf[p + 3] & 0xFF;
        currentFlags = buf[p + 4] & 0xFF;
        currentStreamId = ((buf[p + 5] & 0x7F) << 24) // Mask off reserved bit
                | ((buf[p + 6] & 0xFF) << 16)
                | ((buf[p + 7] & 0xFF) << 8)
                | (buf[p + 8] & 0xFF);

        in.consume(FRAME_HEADER_SIZE);

        // Validate frame size
        if (currentPayloadLength > maxFrameSize) {
            throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                    "Frame size " + currentPayloadLength + " exceeds " + maxFrameSize);
        }

        // Validate stream ID requirements per RFC 9113
        validateStreamId(currentType, currentStreamId);

        // Validate fixed-size frame payloads per RFC 9113
        validateFrameSize(currentType, currentFlags, currentPayloadLength);

        return currentType;
    }

    /**
     * Get the current frame's type.
     *
     * @return frame type (e.g., FRAME_TYPE_DATA, FRAME_TYPE_HEADERS)
     */
    int frameType() {
        return currentType;
    }

    /**
     * Get the current frame's flags.
     *
     * @return frame flags byte
     */
    int frameFlags() {
        return currentFlags;
    }

    /**
     * Get the current frame's stream ID.
     *
     * @return stream identifier (0 for connection-level frames)
     */
    int frameStreamId() {
        return currentStreamId;
    }

    /**
     * Get the current frame's payload length.
     *
     * @return payload length in bytes
     */
    int framePayloadLength() {
        return currentPayloadLength;
    }

    /**
     * Check if the current frame has a specific flag set.
     *
     * @param flag the flag to check (e.g., FLAG_END_STREAM)
     * @return true if the flag is set
     */
    boolean hasFrameFlag(int flag) {
        return (currentFlags & flag) != 0;
    }

    /**
     * Check if there is more data buffered in the input stream.
     *
     * <p>This is used for adaptive signaling: when processing DATA frames in a burst,
     * we can defer waking the consumer thread if more frames are already buffered,
     * reducing thread wakeup overhead.
     *
     * @return true if more data is immediately available without blocking
     */
    boolean hasBufferedData() {
        return in.buffered() > 0;
    }

    // ==================== Payload Parsing Methods ====================

    /**
     * Parse SETTINGS frame payload.
     *
     * @param payload the payload buffer
     * @param length the actual payload length
     * @return array of {id, value} pairs
     * @throws H2Exception if payload is invalid
     */
    int[] parseSettings(byte[] payload, int length) throws H2Exception {
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
     * @param payload the payload buffer
     * @param length the actual payload length
     * @return {lastStreamId, errorCode}
     * @throws H2Exception if payload is invalid
     */
    int[] parseGoaway(byte[] payload, int length) throws H2Exception {
        if (payload == null || length < 8) {
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
     * @param payload the payload buffer
     * @param length the actual payload length
     * @return window size increment
     * @throws H2Exception if payload is invalid or increment is zero
     */
    int parseWindowUpdate(byte[] payload, int length) throws H2Exception {
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
     * Read and parse WINDOW_UPDATE frame payload directly from stream.
     *
     * <p>Uses zero-copy direct buffer access when possible. Reader thread only.
     *
     * @return window size increment
     * @throws IOException if reading fails
     * @throws H2Exception if payload is invalid or increment is zero
     */
    int readAndParseWindowUpdate() throws IOException, H2Exception {
        if (currentPayloadLength != 4) {
            throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                    "WINDOW_UPDATE frame must have 4-byte payload, got " + currentPayloadLength);
        }

        // Zero-copy: ensure 4 bytes in buffer, then parse directly
        if (!in.ensure(4)) {
            throw new IOException("Unexpected EOF reading WINDOW_UPDATE payload");
        }

        byte[] buf = in.buffer();
        int p = in.position();

        int increment = ((buf[p] & 0x7F) << 24)
                | ((buf[p + 1] & 0xFF) << 16)
                | ((buf[p + 2] & 0xFF) << 8)
                | (buf[p + 3] & 0xFF);

        in.consume(4);

        if (increment == 0) {
            throw new H2Exception(ERROR_PROTOCOL_ERROR, "WINDOW_UPDATE increment must be non-zero");
        }

        return increment;
    }

    /**
     * Parse RST_STREAM frame payload.
     *
     * @param payload the payload buffer
     * @param length the actual payload length
     * @return error code
     * @throws H2Exception if payload is invalid
     */
    int parseRstStream(byte[] payload, int length) throws H2Exception {
        if (payload == null || length != 4) {
            throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                    "RST_STREAM frame must have 4-byte payload, got " + length);
        }

        return ((payload[0] & 0xFF) << 24)
                | ((payload[1] & 0xFF) << 16)
                | ((payload[2] & 0xFF) << 8)
                | (payload[3] & 0xFF);
    }

    /**
     * Read and parse RST_STREAM frame payload directly from stream.
     *
     * <p>Uses zero-copy direct buffer access when possible. Reader thread only.
     *
     * @return error code
     * @throws IOException if reading fails
     * @throws H2Exception if payload is invalid
     */
    int readAndParseRstStream() throws IOException, H2Exception {
        if (currentPayloadLength != 4) {
            throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                    "RST_STREAM frame must have 4-byte payload, got " + currentPayloadLength);
        }

        // Zero-copy: ensure 4 bytes in buffer, then parse directly
        if (!in.ensure(4)) {
            throw new IOException("Unexpected EOF reading RST_STREAM payload");
        }

        byte[] buf = in.buffer();
        int p = in.position();

        int errorCode = ((buf[p] & 0xFF) << 24)
                | ((buf[p + 1] & 0xFF) << 16)
                | ((buf[p + 2] & 0xFF) << 8)
                | (buf[p + 3] & 0xFF);

        in.consume(4);

        return errorCode;
    }

    /**
     * Read a complete header block, handling CONTINUATION frames.
     *
     * <p>Per RFC 9113 Section 4.3, a header block must be transmitted as a contiguous
     * sequence of frames with no interleaved frames of any other type or from any other stream.
     *
     * <p>This method uses the stateful parser API. The initial frame's header must have
     * already been read via {@link #nextFrame()} and payload via {@link #readPayloadInto}.
     *
     * @param initialStreamId the stream ID from the initial HEADERS/PUSH_PROMISE frame
     * @param initialPayload the payload from the initial frame
     * @param initialLength the actual payload length
     * @return the complete header block payload
     * @throws IOException if reading fails
     */
    byte[] readHeaderBlock(int initialStreamId, byte[] initialPayload, int initialLength) throws IOException {
        // For PUSH_PROMISE, strip the 4-byte promised stream ID to get the header block fragment
        if (currentType == FRAME_TYPE_PUSH_PROMISE && initialPayload != null) {
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

        if (hasFrameFlag(FLAG_END_HEADERS)) {
            return initialPayload != null ? initialPayload : H2Constants.EMPTY_BYTES;
        }

        // Need to read CONTINUATION frames - use reusable buffer
        headerBlockBuffer.reset();
        if (initialPayload != null) {
            headerBlockBuffer.write(initialPayload, 0, initialLength);
        }

        while (true) {
            int type = nextFrame();
            if (type < 0) {
                throw new IOException("EOF while reading CONTINUATION frames");
            }

            // Per RFC 9113 Section 4.3: header block must be contiguous
            // Only CONTINUATION frames for the same stream are allowed
            if (type != FRAME_TYPE_CONTINUATION) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR,
                        "Header block interrupted by " + frameTypeName(type) +
                                " frame (RFC 9113 Section 4.3 violation)");
            }

            if (currentStreamId != initialStreamId) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR,
                        "CONTINUATION frame stream ID mismatch: expected " +
                                initialStreamId + ", got " + currentStreamId);
            }

            int contLength = currentPayloadLength;
            if (contLength > 0) {
                // Read directly into headerBlockBuffer to avoid intermediate allocation
                readPayloadIntoBuffer(contLength);
            }

            if (hasFrameFlag(FLAG_END_HEADERS)) {
                break;
            }
        }

        // Return view into headerBlockBuffer - valid until next readHeaderBlock call
        // Caller must process before next frame read (which is guaranteed since reader thread is single-threaded)
        return headerBlockBuffer.array();
    }

    /**
     * Get the size of the header block data after a readHeaderBlock call that used CONTINUATION frames.
     *
     * <p>When readHeaderBlock returns headerBlockBuffer.array(), use this method to get the valid data length.
     * Only valid when the previous readHeaderBlock result came from headerBlockBuffer (not the input payload).
     *
     * @return size of valid data in the header block buffer
     */
    int headerBlockSize() {
        return headerBlockBuffer.size();
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
                int pushMinLen = (flags & FLAG_PADDED) != 0 ? 5 : 4;
                if (length < pushMinLen) {
                    throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                            "PUSH_PROMISE frame must have at least " + pushMinLen + "-byte payload, got " + length);
                }
                break;

            case FRAME_TYPE_DATA:
                // DATA frame with PADDED flag must have at least 1 byte (pad length)
                if ((flags & FLAG_PADDED) != 0 && length < 1) {
                    throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                            "DATA frame with PADDED flag must have at least 1-byte payload, got " + length);
                }
                break;

            case FRAME_TYPE_HEADERS:
                // HEADERS with PADDED and/or PRIORITY flags need minimum payload sizes
                int headersMinLen = 0;
                if ((flags & FLAG_PADDED) != 0) {
                    headersMinLen += 1; // 1 byte for pad length
                }
                if ((flags & FLAG_PRIORITY) != 0) {
                    headersMinLen += 5; // 5 bytes for priority data
                }
                if (length < headersMinLen) {
                    throw new H2Exception(ERROR_FRAME_SIZE_ERROR,
                            "HEADERS frame with current flags must have at least " + headersMinLen
                                    + "-byte payload, got " + length);
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
     * to the output stream (e.g., via H2Muxer's writer thread).
     *
     * <p>The underlying UnsyncBufferedOutputStream will buffer small writes.
     * For payloads larger than the buffer size, the header is buffered and the
     * payload is written directly to the underlying stream. This is safe because
     * the writer thread has exclusive access - no interleaving can occur.
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

        // Note: For outbound frames, the caller (H2Exchange.writeData) is responsible for
        // chunking data according to the peer's MAX_FRAME_SIZE setting. We don't validate
        // here because maxFrameSize is our receive limit, not the peer's.

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
     *
     * <p>Debug data is written directly using writeAscii() to avoid allocation
     * when the debug string is ASCII (the common case for error messages).
     */
    void writeGoaway(int lastStreamId, int errorCode, String debugData) throws IOException {
        int debugLen = debugData != null ? debugData.length() : 0;
        int payloadLen = 8 + debugLen;

        // Write frame header manually to avoid allocating payload array
        writeHeaderBuf[0] = (byte) ((payloadLen >> 16) & 0xFF);
        writeHeaderBuf[1] = (byte) ((payloadLen >> 8) & 0xFF);
        writeHeaderBuf[2] = (byte) (payloadLen & 0xFF);
        writeHeaderBuf[3] = (byte) FRAME_TYPE_GOAWAY;
        writeHeaderBuf[4] = 0; // flags
        writeHeaderBuf[5] = 0; // stream ID = 0
        writeHeaderBuf[6] = 0;
        writeHeaderBuf[7] = 0;
        writeHeaderBuf[8] = 0;
        out.write(writeHeaderBuf);

        // Write fixed 8-byte GOAWAY payload (lastStreamId + errorCode) using scratch buffer
        writeScratch[0] = (byte) ((lastStreamId >> 24) & 0x7F);
        writeScratch[1] = (byte) ((lastStreamId >> 16) & 0xFF);
        writeScratch[2] = (byte) ((lastStreamId >> 8) & 0xFF);
        writeScratch[3] = (byte) (lastStreamId & 0xFF);
        writeScratch[4] = (byte) ((errorCode >> 24) & 0xFF);
        writeScratch[5] = (byte) ((errorCode >> 16) & 0xFF);
        writeScratch[6] = (byte) ((errorCode >> 8) & 0xFF);
        writeScratch[7] = (byte) (errorCode & 0xFF);
        out.write(writeScratch, 0, 8);

        // Write debug data directly as ASCII (avoids String.getBytes allocation)
        if (debugLen > 0) {
            out.writeAscii(debugData);
        }
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
        writeScratch[0] = (byte) ((windowSizeIncrement >> 24) & 0x7F);
        writeScratch[1] = (byte) ((windowSizeIncrement >> 16) & 0xFF);
        writeScratch[2] = (byte) ((windowSizeIncrement >> 8) & 0xFF);
        writeScratch[3] = (byte) (windowSizeIncrement & 0xFF);
        writeFrame(FRAME_TYPE_WINDOW_UPDATE, 0, streamId, writeScratch, 0, 4);
    }

    /**
     * Write RST_STREAM frame.
     * Uses scratch buffer - caller must have exclusive access (writer thread).
     */
    void writeRstStream(int streamId, int errorCode) throws IOException {
        // Use scratch buffer to avoid allocation
        writeScratch[0] = (byte) ((errorCode >> 24) & 0xFF);
        writeScratch[1] = (byte) ((errorCode >> 16) & 0xFF);
        writeScratch[2] = (byte) ((errorCode >> 8) & 0xFF);
        writeScratch[3] = (byte) (errorCode & 0xFF);
        writeFrame(FRAME_TYPE_RST_STREAM, 0, streamId, writeScratch, 0, 4);
    }

    /**
     * Flush the output stream.
     *
     * <p>Caller must ensure exclusive access to the output stream.
     */
    void flush() throws IOException {
        out.flush();
    }

    /**
     * Read payload bytes directly into a provided buffer.
     *
     * <p>This method is used by the reader thread to read DATA frame payloads
     * directly into an exchange's buffer, avoiding an intermediate allocation.
     *
     * <p>Uses zero-copy when the entire payload is already buffered. When partially
     * buffered, drains the buffer then reads directly from the underlying stream
     * to avoid redundant buffer fill/copy overhead.
     *
     * @param dest the destination buffer
     * @param offset offset in the destination buffer
     * @param length number of bytes to read
     * @throws IOException if reading fails or EOF is reached before all bytes are read
     */
    void readPayloadInto(byte[] dest, int offset, int length) throws IOException {
        // Fast path: if entirely buffered, single arraycopy (zero-copy from network perspective)
        int buffered = in.buffered();
        if (length <= buffered) {
            System.arraycopy(in.buffer(), in.position(), dest, offset, length);
            in.consume(length);
            return;
        }

        // Drain what's buffered first
        if (buffered > 0) {
            System.arraycopy(in.buffer(), in.position(), dest, offset, buffered);
            in.consume(buffered);
            offset += buffered;
            length -= buffered;
        }

        // Read remainder directly from underlying stream (buffer is now empty).
        // Using readDirect avoids the buffer fill/check overhead in read().
        while (length > 0) {
            int n = in.readDirect(dest, offset, length);
            if (n < 0) {
                throw new IOException("Incomplete payload: unexpected EOF");
            }
            offset += n;
            length -= n;
        }
    }

    /**
     * Read payload bytes directly into the headerBlockBuffer.
     *
     * <p>Used when reading CONTINUATION frames to avoid allocating intermediate byte[] arrays.
     *
     * @param length number of bytes to read
     * @throws IOException if reading fails or EOF is reached before all bytes are read
     */
    private void readPayloadIntoBuffer(int length) throws IOException {
        // Fast path: if entirely buffered, write directly to headerBlockBuffer
        int buffered = in.buffered();
        if (length <= buffered) {
            headerBlockBuffer.write(in.buffer(), in.position(), length);
            in.consume(length);
            return;
        }

        // Drain what's buffered first
        if (buffered > 0) {
            headerBlockBuffer.write(in.buffer(), in.position(), buffered);
            in.consume(buffered);
            length -= buffered;
        }

        // Read remainder in chunks using scratch buffer
        while (length > 0) {
            int toRead = Math.min(length, writeScratch.length);
            int totalRead = 0;
            while (totalRead < toRead) {
                int n = in.readDirect(writeScratch, totalRead, toRead - totalRead);
                if (n < 0) {
                    throw new IOException("Incomplete payload: unexpected EOF");
                }
                totalRead += n;
            }
            headerBlockBuffer.write(writeScratch, 0, totalRead);
            length -= totalRead;
        }
    }

    /**
     * Read a single byte from the input stream.
     *
     * <p>Used for reading pad length in padded DATA frames without allocating.
     * Uses zero-copy direct buffer access when possible.
     *
     * @return the byte value (0-255)
     * @throws IOException if reading fails or EOF is reached
     */
    int readByte() throws IOException {
        if (!in.ensure(1)) {
            throw new IOException("Unexpected EOF reading byte");
        }
        int b = in.buffer()[in.position()] & 0xFF;
        in.consume(1);
        return b;
    }

    /**
     * Skip the specified number of bytes in the input stream.
     *
     * <p>Used to skip past padding bytes in DATA frames. Uses direct buffer
     * consume for small skips (common case), falling back to stream skip
     * for larger amounts.
     *
     * @param length number of bytes to skip
     * @throws IOException if skipping fails or EOF is reached before all bytes are skipped
     */
    void skipBytes(int length) throws IOException {
        // Fast path: if entirely buffered, just consume (common for padding)
        int buffered = in.buffered();
        if (length <= buffered) {
            in.consume(length);
            return;
        }

        // Consume what's buffered
        if (buffered > 0) {
            in.consume(buffered);
            length -= buffered;
        }

        // Skip remainder in underlying stream
        long remaining = length;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                throw new IOException("Unexpected EOF while skipping bytes");
            }
            remaining -= skipped;
        }
    }
}
