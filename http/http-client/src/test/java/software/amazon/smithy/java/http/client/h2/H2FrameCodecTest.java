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

class H2FrameCodecTest {

    // Write helper methods
    @Test
    void writeSettings() throws IOException {
        var out = new ByteArrayOutputStream();
        codec(out).writeSettings(1, 4096, 3, 100);
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
        codec(out).writeSettingsAck();
        var frame = decode(out);

        assertEquals(4, frame.type());
        assertEquals(1, frame.flags());
        assertEquals(0, frame.payloadLength());
        assertEquals(0, frame.streamId());
    }

    @Test
    void writeGoaway() throws IOException {
        var out = new ByteArrayOutputStream();
        codec(out).writeGoaway(5, 2, "debug");
        var frame = decode(out);
        int[] g = frame.parseGoaway();

        assertEquals(5, g[0]);
        assertEquals(2, g[1]);
    }

    @Test
    void writeGoawayNullDebug() throws IOException {
        var out = new ByteArrayOutputStream();
        codec(out).writeGoaway(1, 0, null);
        var frame = decode(out);

        assertEquals(7, frame.type());
        assertEquals(0, frame.streamId());
        assertEquals(8, frame.payloadLength());
    }

    @Test
    void writeWindowUpdate() throws IOException {
        var out = new ByteArrayOutputStream();
        codec(out).writeWindowUpdate(1, 65535);
        var frame = decode(out);

        assertEquals(65535, frame.parseWindowUpdate());
    }

    @Test
    void writeRstStream() throws IOException {
        var out = new ByteArrayOutputStream();
        codec(out).writeRstStream(1, 8);
        var frame = decode(out);

        assertEquals(8, frame.parseRstStream());
    }

    @Test
    void writeHeadersWithContinuation() throws IOException {
        var out = new ByteArrayOutputStream();
        var codec = new H2FrameCodec(new ByteArrayInputStream(new byte[0]), out, 16);
        byte[] block = new byte[50];
        codec.writeHeaders(1, block, 0, 50, true);
        codec.flush();

        var readCodec =
                new H2FrameCodec(new ByteArrayInputStream(out.toByteArray()), new ByteArrayOutputStream(), 16384);
        var frame = readCodec.readFrame();
        byte[] result = readCodec.readHeaderBlock(frame);

        assertEquals(50, result.length);
    }

    @Test
    void writeHeadersSingleFrame() throws IOException {
        var out = new ByteArrayOutputStream();
        codec(out).writeHeaders(1, new byte[] {1, 2, 3}, 0, 3, false);
        var frame = decode(out);

        assertTrue(frame.hasFlag(0x04)); // END_HEADERS
    }

    // Validation
    @Test
    void throwsOnNegativeStreamId() {
        assertThrows(IllegalArgumentException.class,
                () -> codec(new ByteArrayOutputStream()).writeFrame(0, 0, -1, new byte[0]));
    }

    @Test
    void throwsOnPayloadExceedsMax() {
        var codec = new H2FrameCodec(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), 100);

        assertThrows(H2Exception.class, () -> codec.writeFrame(0, 0, 1, new byte[200]));
    }

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

        var codec = new H2FrameCodec(new ByteArrayInputStream(out.toByteArray()), new ByteArrayOutputStream(), 16384);
        var frame = codec.readFrame();
        byte[] block = codec.readHeaderBlock(frame);

        assertArrayEquals(new byte[] {1, 2, 3, 4}, block);
    }

    @Test
    void throwsOnContinuationWrongStream() throws IOException {
        var out = new ByteArrayOutputStream();
        out.write(buildFrame(1, 0, 1, new byte[] {1}));
        out.write(buildFrame(9, 0x04, 2, new byte[] {2})); // wrong stream
        var codec = new H2FrameCodec(new ByteArrayInputStream(out.toByteArray()), new ByteArrayOutputStream(), 16384);

        assertThrows(H2Exception.class, () -> codec.readHeaderBlock(codec.readFrame()));
    }

    @Test
    void throwsOnNonContinuationInterrupt() throws IOException {
        var out = new ByteArrayOutputStream();
        out.write(buildFrame(1, 0, 1, new byte[] {1}));
        out.write(buildFrame(0, 0, 1, new byte[] {2})); // DATA interrupts
        var codec = new H2FrameCodec(new ByteArrayInputStream(out.toByteArray()), new ByteArrayOutputStream(), 16384);

        assertThrows(H2Exception.class, () -> codec.readHeaderBlock(codec.readFrame()));
    }

    @Test
    void throwsOnEofDuringContinuation() throws IOException {
        var out = new ByteArrayOutputStream();
        out.write(buildFrame(1, 0, 1, new byte[] {1}));
        var codec = new H2FrameCodec(new ByteArrayInputStream(out.toByteArray()), new ByteArrayOutputStream(), 16384);

        assertThrows(IOException.class, () -> codec.readHeaderBlock(codec.readFrame()));
    }

    @Test
    void readHeaderBlockFromPushPromise() throws IOException {
        byte[] payload = {0, 0, 0, 2, 'a', 'b'};
        var codec = new H2FrameCodec(new ByteArrayInputStream(buildFrame(5, 0x04, 1, payload)),
                new ByteArrayOutputStream(),
                16384);
        byte[] block = codec.readHeaderBlock(codec.readFrame());

        assertArrayEquals(new byte[] {'a', 'b'}, block);
    }

    // Padding/Priority edge cases
    @Test
    void throwsOnPadLengthExceedsPayload() {
        byte[] payload = {10, 'a'};

        assertThrows(H2Exception.class, () -> decode(buildFrame(0, 0x08, 1, payload)));
    }

    @Test
    void throwsOnPriorityHeadersTooShort() {
        assertThrows(H2Exception.class, () -> decode(buildFrame(1, 0x24, 1, new byte[3])));
    }

    @Test
    void removePaddingFromPushPromise() throws IOException {
        // PUSH_PROMISE (type 5) with PADDED flag (0x08), pad=1, promised stream=2, header block='x'
        byte[] payload = {1, 0, 0, 0, 2, 'x', 0}; // padLen=1, promisedId=2, data='x', padding=0
        var codec = new H2FrameCodec(new ByteArrayInputStream(buildFrame(5, 0x0C, 1, payload)),
                new ByteArrayOutputStream(),
                16384);
        var frame = codec.readFrame();

        assertEquals(5, frame.type());
        assertEquals(5, frame.payloadLength()); // promisedId(4) + 'x'(1)
        byte[] headerBlock = codec.readHeaderBlock(frame);
        assertArrayEquals(new byte[] {'x'}, headerBlock);
    }

    @Test
    void removePriorityFromHeaders() throws IOException {
        // HEADERS with PRIORITY flag - 5 bytes priority + header block
        byte[] payload = {0, 0, 0, 1, 16, 'a', 'b'}; // dependency=1, weight=16, headers='ab'
        var frame = decode(buildFrame(1, 0x24, 1, payload)); // PRIORITY | END_HEADERS

        assertEquals(2, frame.payloadLength());
        assertArrayEquals(new byte[] {'a', 'b'}, Arrays.copyOfRange(frame.payload(), 0, frame.payloadLength()));
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
        var codec = new H2FrameCodec(new ByteArrayInputStream(buildFrame(0, 0, 1, new byte[200])),
                new ByteArrayOutputStream(),
                100);

        assertThrows(H2Exception.class, codec::readFrame);
    }

    // removePadding edge case - empty payload
    @Test
    void throwsOnPaddedEmptyPayload() {
        assertThrows(H2Exception.class, () -> decode(buildFrame(0, 0x08, 1, new byte[0])));
    }

    // PUSH_PROMISE payload too short for promised stream ID (in readHeaderBlock)
    @Test
    void throwsOnPushPromisePayloadTooShortForStreamId() throws IOException {
        // Create a Frame directly with short payload to test readHeaderBlock path
        var frame = new H2FrameCodec.Frame(5, 0x04, 1, new byte[2], 2);
        var codec = new H2FrameCodec(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), 16384);

        assertThrows(H2Exception.class, () -> codec.readHeaderBlock(frame));
    }

    // Frame.parseSettings edge cases
    @Test
    void parseSettingsWrongFrameType() throws IOException {
        var frame = decode(buildFrame(0, 0, 1, new byte[1])); // DATA frame
        assertThrows(H2Exception.class, frame::parseSettings);
    }

    @Test
    void parseSettingsPayloadNotMultipleOf6() throws IOException {
        var frame = decode(buildFrame(4, 0, 0, new byte[7])); // 7 bytes, not multiple of 6

        assertThrows(H2Exception.class, frame::parseSettings);
    }

    // Frame.parseGoaway edge cases
    @Test
    void parseGoawayWrongFrameType() throws IOException {
        var frame = decode(buildFrame(0, 0, 1, new byte[1]));

        assertThrows(H2Exception.class, frame::parseGoaway);
    }

    @Test
    void parseGoawayPayloadTooShort() {
        var frame = new H2FrameCodec.Frame(7, 0, 0, new byte[4], 4);

        assertThrows(H2Exception.class, frame::parseGoaway);
    }

    // Frame.parseWindowUpdate edge cases
    @Test
    void parseWindowUpdateWrongFrameType() throws IOException {
        var frame = decode(buildFrame(0, 0, 1, new byte[1]));

        assertThrows(H2Exception.class, frame::parseWindowUpdate);
    }

    @Test
    void parseWindowUpdateWrongPayloadLength() {
        // WINDOW_UPDATE frame with 3-byte payload instead of required 4
        var frame = new H2FrameCodec.Frame(8, 0, 1, new byte[3], 3);

        assertThrows(H2Exception.class, frame::parseWindowUpdate);
    }

    @Test
    void parseWindowUpdateZeroIncrement() throws IOException {
        var frame = decode(buildFrame(8, 0, 1, new byte[4])); // all zeros = increment 0

        assertThrows(H2Exception.class, frame::parseWindowUpdate);
    }

    // Frame.parseRstStream edge cases
    @Test
    void parseRstStreamWrongFrameType() throws IOException {
        var frame = decode(buildFrame(0, 0, 1, new byte[1]));

        assertThrows(H2Exception.class, frame::parseRstStream);
    }

    @Test
    void parseRstStreamWrongPayloadLength() throws IOException {
        var frame = new H2FrameCodec.Frame(3, 0, 1, new byte[3], 3);

        assertThrows(H2Exception.class, frame::parseRstStream);
    }

    // Incomplete payload reads
    @Test
    void throwsOnIncompleteControlFramePayload() {
        // Build header claiming 8-byte PING payload but only provide 4 bytes
        byte[] truncated = new byte[9 + 4]; // header + partial payload
        truncated[2] = 8; // length = 8
        truncated[3] = 6; // type = PING
        // streamId = 0 (already zeros)
        var codec = new H2FrameCodec(new ByteArrayInputStream(truncated), new ByteArrayOutputStream(), 16384);

        assertThrows(IOException.class, codec::readFrame);
    }

    @Test
    void throwsOnIncompleteDataFramePayload() {
        // Build header claiming 100-byte DATA payload but only provide 50 bytes
        byte[] truncated = new byte[9 + 50];
        truncated[2] = 100; // length = 100
        truncated[3] = 0; // type = DATA
        truncated[8] = 1; // streamId = 1
        var codec = new H2FrameCodec(new ByteArrayInputStream(truncated), new ByteArrayOutputStream(), 16384);

        assertThrows(IOException.class, codec::readFrame);
    }

    // Helpers
    private H2FrameCodec codec(ByteArrayOutputStream out) {
        return new H2FrameCodec(new ByteArrayInputStream(new byte[0]), out, 16384);
    }

    private H2FrameCodec.Frame decode(ByteArrayOutputStream out) throws IOException {
        var c = new H2FrameCodec(new ByteArrayInputStream(out.toByteArray()), new ByteArrayOutputStream(), 16384);
        return c.readFrame();
    }

    private H2FrameCodec.Frame decode(byte[] frame) throws IOException {
        return new H2FrameCodec(new ByteArrayInputStream(frame), new ByteArrayOutputStream(), 16384).readFrame();
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
