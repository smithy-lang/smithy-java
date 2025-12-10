/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.client.UnsyncBufferedInputStream;
import software.amazon.smithy.java.http.client.UnsyncBufferedOutputStream;

class H2FrameCodecTest {

    // Test helper record that simulates the old Frame record for testing
    record TestFrame(int type, int flags, int streamId, byte[] payload, int length, H2FrameCodec codec) {
        boolean hasFlag(int flag) {
            return (flags & flag) != 0;
        }

        int payloadLength() {
            return length;
        }

        int[] parseSettings() throws H2Exception {
            return codec.parseSettings(payload, length);
        }

        int[] parseGoaway() throws H2Exception {
            return codec.parseGoaway(payload, length);
        }

        int parseWindowUpdate() throws H2Exception {
            return codec.parseWindowUpdate(payload, length);
        }

        int parseRstStream() throws H2Exception {
            return codec.parseRstStream(payload, length);
        }
    }

    // Write helper methods
    @Test
    void writeSettings() throws IOException {
        var out = new ByteArrayOutputStream();
        var c = codec(out);
        c.writeSettings(1, 4096, 3, 100);
        c.flush();
        var frame = decode(out);
        int[] s = frame.parseSettings();

        assertEquals(4, s.length);
        assertEquals(1, s[0]);
        assertEquals(4096, s[1]);
        assertEquals(3, s[2]);
        assertEquals(100, s[3]);
    }

    @Test
    void writeSettingsAck() throws IOException {
        var out = new ByteArrayOutputStream();
        var c = codec(out);
        c.writeSettingsAck();
        c.flush();
        var frame = decode(out);

        assertEquals(4, frame.type());
        assertEquals(1, frame.flags());
        assertEquals(0, frame.payloadLength());
        assertEquals(0, frame.streamId());
    }

    @Test
    void writeGoaway() throws IOException {
        var out = new ByteArrayOutputStream();
        var c = codec(out);
        c.writeGoaway(5, 2, "debug");
        c.flush();
        var frame = decode(out);
        int[] g = frame.parseGoaway();

        assertEquals(5, g[0]);
        assertEquals(2, g[1]);
    }

    @Test
    void writeGoawayNullDebug() throws IOException {
        var out = new ByteArrayOutputStream();
        var c = codec(out);
        c.writeGoaway(1, 0, null);
        c.flush();
        var frame = decode(out);

        assertEquals(7, frame.type());
        assertEquals(0, frame.streamId());
        assertEquals(8, frame.payloadLength());
    }

    @Test
    void writeWindowUpdate() throws IOException {
        var out = new ByteArrayOutputStream();
        var c = codec(out);
        c.writeWindowUpdate(1, 65535);
        c.flush();
        var frame = decode(out);

        assertEquals(65535, frame.parseWindowUpdate());
    }

    @Test
    void writeRstStream() throws IOException {
        var out = new ByteArrayOutputStream();
        var c = codec(out);
        c.writeRstStream(1, 8);
        c.flush();
        var frame = decode(out);

        assertEquals(8, frame.parseRstStream());
    }

    @Test
    void writeHeadersWithContinuation() throws IOException {
        var out = new ByteArrayOutputStream();
        var codec = new H2FrameCodec(wrapIn(new byte[0]), wrapOut(out), 16);
        byte[] block = new byte[50];
        codec.writeHeaders(1, block, 0, 50, true);
        codec.flush();

        var readCodec = new H2FrameCodec(wrapIn(out.toByteArray()), wrapOut(new ByteArrayOutputStream()), 16384);
        int type = readCodec.nextFrame();
        assertEquals(1, type); // HEADERS
        int streamId = readCodec.frameStreamId();
        int length = readCodec.framePayloadLength();
        byte[] payload = new byte[length];
        readCodec.readPayloadInto(payload, 0, length);
        readCodec.readHeaderBlock(streamId, payload, length);

        // Zero-copy: use headerBlockSize() for valid length
        assertEquals(50, readCodec.headerBlockSize());
    }

    @Test
    void writeHeadersSingleFrame() throws IOException {
        var out = new ByteArrayOutputStream();
        var c = codec(out);
        c.writeHeaders(1, new byte[] {1, 2, 3}, 0, 3, false);
        c.flush();
        var frame = decode(out);

        assertTrue(frame.hasFlag(0x04)); // END_HEADERS
    }

    // Validation
    @Test
    void throwsOnNegativeStreamId() {
        assertThrows(IllegalArgumentException.class,
                () -> codec(new ByteArrayOutputStream()).writeFrame(0, 0, -1, new byte[0]));
    }

    // Note: We no longer validate outbound frame size in writeFrame() because
    // the peer's MAX_FRAME_SIZE setting may be larger than our receive limit.
    // The caller (H2Exchange.writeData) is responsible for chunking according
    // to the peer's advertised MAX_FRAME_SIZE.

    @Test
    void throwsOnWindowUpdateZero() {
        assertThrows(IllegalArgumentException.class, () -> codec(new ByteArrayOutputStream()).writeWindowUpdate(1, 0));
    }

    @Test
    void throwsOnOddSettingsCount() {
        assertThrows(IllegalArgumentException.class, () -> codec(new ByteArrayOutputStream()).writeSettings(1, 2, 3));
    }

    // readHeaderBlock
    @Test
    void readHeaderBlockWithContinuation() throws IOException {
        var out = new ByteArrayOutputStream();
        out.write(buildFrame(1, 0, 1, new byte[] {1, 2})); // HEADERS no END_HEADERS
        out.write(buildFrame(9, 0x04, 1, new byte[] {3, 4})); // CONTINUATION with END_HEADERS

        var codec = new H2FrameCodec(wrapIn(out.toByteArray()), wrapOut(new ByteArrayOutputStream()), 16384);
        int type = codec.nextFrame();
        assertEquals(1, type); // HEADERS
        int streamId = codec.frameStreamId();
        int length = codec.framePayloadLength();
        byte[] payload = new byte[length];
        codec.readPayloadInto(payload, 0, length);
        byte[] block = codec.readHeaderBlock(streamId, payload, length);
        int blockSize = codec.headerBlockSize();

        // Zero-copy: block is a view into internal buffer, use headerBlockSize() for valid length
        assertArrayEquals(new byte[] {1, 2, 3, 4}, Arrays.copyOf(block, blockSize));
    }

    @Test
    void throwsOnContinuationWrongStream() throws IOException {
        var out = new ByteArrayOutputStream();
        out.write(buildFrame(1, 0, 1, new byte[] {1}));
        out.write(buildFrame(9, 0x04, 2, new byte[] {2})); // wrong stream
        var codec = new H2FrameCodec(wrapIn(out.toByteArray()), wrapOut(new ByteArrayOutputStream()), 16384);

        assertThrows(H2Exception.class, () -> {
            int type = codec.nextFrame();
            int streamId = codec.frameStreamId();
            int length = codec.framePayloadLength();
            byte[] payload = new byte[length];
            codec.readPayloadInto(payload, 0, length);
            codec.readHeaderBlock(streamId, payload, length);
        });
    }

    @Test
    void throwsOnNonContinuationInterrupt() throws IOException {
        var out = new ByteArrayOutputStream();
        out.write(buildFrame(1, 0, 1, new byte[] {1}));
        out.write(buildFrame(0, 0, 1, new byte[] {2})); // DATA interrupts
        var codec = new H2FrameCodec(wrapIn(out.toByteArray()), wrapOut(new ByteArrayOutputStream()), 16384);

        assertThrows(H2Exception.class, () -> {
            int type = codec.nextFrame();
            int streamId = codec.frameStreamId();
            int length = codec.framePayloadLength();
            byte[] payload = new byte[length];
            codec.readPayloadInto(payload, 0, length);
            codec.readHeaderBlock(streamId, payload, length);
        });
    }

    @Test
    void throwsOnEofDuringContinuation() throws IOException {
        var out = new ByteArrayOutputStream();
        out.write(buildFrame(1, 0, 1, new byte[] {1}));
        var codec = new H2FrameCodec(wrapIn(out.toByteArray()), wrapOut(new ByteArrayOutputStream()), 16384);

        assertThrows(IOException.class, () -> {
            int type = codec.nextFrame();
            int streamId = codec.frameStreamId();
            int length = codec.framePayloadLength();
            byte[] payload = new byte[length];
            codec.readPayloadInto(payload, 0, length);
            codec.readHeaderBlock(streamId, payload, length);
        });
    }

    @Test
    void readHeaderBlockFromPushPromise() throws IOException {
        byte[] framePayload = {0, 0, 0, 2, 'a', 'b'};
        var codec = new H2FrameCodec(wrapIn(buildFrame(5, 0x04, 1, framePayload)),
                wrapOut(new ByteArrayOutputStream()),
                16384);
        int type = codec.nextFrame();
        assertEquals(5, type); // PUSH_PROMISE
        int streamId = codec.frameStreamId();
        int length = codec.framePayloadLength();
        byte[] payload = new byte[length];
        codec.readPayloadInto(payload, 0, length);
        byte[] block = codec.readHeaderBlock(streamId, payload, length);

        assertArrayEquals(new byte[] {'a', 'b'}, block);
    }

    // Padding validation note: With the stateful API, padding processing is the caller's
    // responsibility. The codec validates minimum payload size for PADDED flag but doesn't
    // read/validate the actual pad length byte since that requires reading the payload.
    // H2Connection.handleDataFrame() handles this validation when processing DATA frames.

    @Test
    void throwsOnPriorityHeadersTooShort() {
        assertThrows(H2Exception.class, () -> decodeAndReadPayload(buildFrame(1, 0x24, 1, new byte[3])));
    }

    // validateFrameSize tests
    @Test
    void throwsOnPingWrongSize() {
        assertThrows(H2Exception.class, () -> decode(buildFrame(6, 0, 0, new byte[4])));
    }

    @Test
    void throwsOnSettingsAckNonEmpty() {
        assertThrows(H2Exception.class, () -> decode(buildFrame(4, 0x01, 0, new byte[6])));
    }

    @Test
    void throwsOnWindowUpdateWrongSize() {
        assertThrows(H2Exception.class, () -> decode(buildFrame(8, 0, 0, new byte[3])));
    }

    @Test
    void throwsOnRstStreamWrongSize() {
        assertThrows(H2Exception.class, () -> decode(buildFrame(3, 0, 1, new byte[3])));
    }

    @Test
    void throwsOnPriorityWrongSize() {
        assertThrows(H2Exception.class, () -> decode(buildFrame(2, 0, 1, new byte[4])));
    }

    @Test
    void throwsOnGoawayTooShort() {
        assertThrows(H2Exception.class, () -> decode(buildFrame(7, 0, 0, new byte[7])));
    }

    @Test
    void throwsOnPushPromiseTooShort() {
        assertThrows(H2Exception.class, () -> decode(buildFrame(5, 0, 1, new byte[3])));
    }

    @Test
    void throwsOnPushPromisePaddedTooShort() {
        assertThrows(H2Exception.class, () -> decode(buildFrame(5, 0x08, 1, new byte[4])));
    }

    // validateStreamId tests
    @Test
    void throwsOnDataStreamIdZero() {
        assertThrows(H2Exception.class, () -> decode(buildFrame(0, 0, 0, new byte[1])));
    }

    @Test
    void throwsOnHeadersStreamIdZero() {
        assertThrows(H2Exception.class, () -> decode(buildFrame(1, 0x04, 0, new byte[1])));
    }

    @Test
    void throwsOnPriorityStreamIdZero() {
        assertThrows(H2Exception.class, () -> decode(buildFrame(2, 0, 0, new byte[5])));
    }

    @Test
    void throwsOnRstStreamStreamIdZero() {
        assertThrows(H2Exception.class, () -> decode(buildFrame(3, 0, 0, new byte[4])));
    }

    @Test
    void throwsOnContinuationStreamIdZero() {
        assertThrows(H2Exception.class, () -> decode(buildFrame(9, 0, 0, new byte[1])));
    }

    @Test
    void throwsOnSettingsNonZeroStreamId() {
        assertThrows(H2Exception.class, () -> decode(buildFrame(4, 0, 1, new byte[0])));
    }

    @Test
    void throwsOnPingNonZeroStreamId() {
        assertThrows(H2Exception.class, () -> decode(buildFrame(6, 0, 1, new byte[8])));
    }

    @Test
    void throwsOnGoawayNonZeroStreamId() {
        assertThrows(H2Exception.class, () -> decode(buildFrame(7, 0, 1, new byte[8])));
    }

    @Test
    void throwsOnPushPromiseStreamIdZero() {
        assertThrows(H2Exception.class, () -> decode(buildFrame(5, 0x04, 0, new byte[4])));
    }

    // Frame size exceeds max during read
    @Test
    void throwsOnFrameSizeExceedsMax() {
        var codec = new H2FrameCodec(wrapIn(buildFrame(0, 0, 1, new byte[200])),
                wrapOut(new ByteArrayOutputStream()),
                100);

        assertThrows(H2Exception.class, codec::nextFrame);
    }

    // removePadding edge case - empty payload
    @Test
    void throwsOnPaddedEmptyPayload() {
        assertThrows(H2Exception.class, () -> decode(buildFrame(0, 0x08, 1, new byte[0])));
    }

    // PUSH_PROMISE payload too short for promised stream ID (validated at nextFrame() time)
    @Test
    void throwsOnPushPromisePayloadTooShortForStreamId() {
        // Build a PUSH_PROMISE frame with payload too short for stream ID (requires 4 bytes min)
        var codec = new H2FrameCodec(wrapIn(buildFrame(5, 0x04, 1, new byte[2])),
                wrapOut(new ByteArrayOutputStream()),
                16384);
        // Validation now happens at nextFrame() time
        assertThrows(H2Exception.class, codec::nextFrame);
    }

    // Codec.parseSettings edge cases
    @Test
    void parseSettingsPayloadNotMultipleOf6() throws IOException {
        var codec = new H2FrameCodec(wrapIn(new byte[0]), wrapOut(new ByteArrayOutputStream()), 16384);
        byte[] payload = new byte[7]; // 7 bytes, not multiple of 6

        assertThrows(H2Exception.class, () -> codec.parseSettings(payload, 7));
    }

    // Codec.parseGoaway edge cases
    @Test
    void parseGoawayPayloadTooShort() {
        var codec = new H2FrameCodec(wrapIn(new byte[0]), wrapOut(new ByteArrayOutputStream()), 16384);

        assertThrows(H2Exception.class, () -> codec.parseGoaway(new byte[4], 4));
    }

    // Codec.parseWindowUpdate edge cases
    @Test
    void parseWindowUpdateWrongPayloadLength() {
        var codec = new H2FrameCodec(wrapIn(new byte[0]), wrapOut(new ByteArrayOutputStream()), 16384);

        assertThrows(H2Exception.class, () -> codec.parseWindowUpdate(new byte[3], 3));
    }

    @Test
    void parseWindowUpdateZeroIncrement() throws IOException {
        var codec = new H2FrameCodec(wrapIn(new byte[0]), wrapOut(new ByteArrayOutputStream()), 16384);

        assertThrows(H2Exception.class, () -> codec.parseWindowUpdate(new byte[4], 4)); // all zeros = increment 0
    }

    // Codec.parseRstStream edge cases
    @Test
    void parseRstStreamWrongPayloadLength() throws IOException {
        var codec = new H2FrameCodec(wrapIn(new byte[0]), wrapOut(new ByteArrayOutputStream()), 16384);

        assertThrows(H2Exception.class, () -> codec.parseRstStream(new byte[3], 3));
    }

    // Incomplete payload reads
    @Test
    void throwsOnIncompletePayload() {
        // Build header claiming 8-byte PING payload but only provide 4 bytes
        byte[] truncated = new byte[9 + 4]; // header + partial payload
        truncated[2] = 8; // length = 8
        truncated[3] = 6; // type = PING
        // streamId = 0 (already zeros)
        var codec = new H2FrameCodec(wrapIn(truncated), wrapOut(new ByteArrayOutputStream()), 16384);

        assertThrows(IOException.class, () -> {
            codec.nextFrame();
            byte[] payload = new byte[codec.framePayloadLength()];
            codec.readPayloadInto(payload, 0, payload.length);
        });
    }

    // Helpers
    private static final int BUF_SIZE = 8192;

    private UnsyncBufferedInputStream wrapIn(byte[] data) {
        return new UnsyncBufferedInputStream(new ByteArrayInputStream(data), BUF_SIZE);
    }

    private UnsyncBufferedOutputStream wrapOut(ByteArrayOutputStream out) {
        return new UnsyncBufferedOutputStream(out, BUF_SIZE);
    }

    private H2FrameCodec codec(ByteArrayOutputStream out) {
        return new H2FrameCodec(wrapIn(new byte[0]), wrapOut(out), 16384);
    }

    private TestFrame decode(ByteArrayOutputStream out) throws IOException {
        var codec = new H2FrameCodec(wrapIn(out.toByteArray()), wrapOut(new ByteArrayOutputStream()), 16384);
        int type = codec.nextFrame();
        int flags = codec.frameFlags();
        int streamId = codec.frameStreamId();
        int length = codec.framePayloadLength();
        byte[] payload;
        if (length == 0) {
            payload = new byte[0];
        } else {
            payload = new byte[length];
            codec.readPayloadInto(payload, 0, length);
        }
        return new TestFrame(type, flags, streamId, payload, length, codec);
    }

    private TestFrame decode(byte[] frame) throws IOException {
        var codec = new H2FrameCodec(wrapIn(frame), wrapOut(new ByteArrayOutputStream()), 16384);
        int type = codec.nextFrame();
        int flags = codec.frameFlags();
        int streamId = codec.frameStreamId();
        int length = codec.framePayloadLength();
        byte[] payload;
        if (length == 0) {
            payload = new byte[0];
        } else {
            payload = new byte[length];
            codec.readPayloadInto(payload, 0, length);
        }
        return new TestFrame(type, flags, streamId, payload, length, codec);
    }

    private void decodeAndReadPayload(byte[] frame) throws IOException {
        var codec = new H2FrameCodec(wrapIn(frame), wrapOut(new ByteArrayOutputStream()), 16384);
        int type = codec.nextFrame();
        int length = codec.framePayloadLength();
        if (length > 0) {
            byte[] payload = new byte[length];
            codec.readPayloadInto(payload, 0, length);
        }
    }

    private byte[] buildFrame(int type, int flags, int streamId, byte[] payload) {
        byte[] frame = new byte[9 + payload.length];
        frame[0] = (byte) ((payload.length >> 16) & 0xFF);
        frame[1] = (byte) ((payload.length >> 8) & 0xFF);
        frame[2] = (byte) (payload.length & 0xFF);
        frame[3] = (byte) type;
        frame[4] = (byte) flags;
        frame[5] = (byte) ((streamId >> 24) & 0x7F);
        frame[6] = (byte) ((streamId >> 16) & 0xFF);
        frame[7] = (byte) ((streamId >> 8) & 0xFF);
        frame[8] = (byte) (streamId & 0xFF);
        System.arraycopy(payload, 0, frame, 9, payload.length);
        return frame;
    }
}
