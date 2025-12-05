/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FLAG_PADDED;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FLAG_PRIORITY;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * HTTP/2 frame codec test suite using test vectors from http2jp/http2-frame-test-case.
 *
 * <p>The {@link #decodeFrame} test validates decoding against upstream JSON test vectors.
 * The {@link #roundTripFrame} test validates encode-decode identity using our codec both ways.
 *
 * @see <a href="https://github.com/http2jp/http2-frame-test-case">http2-frame-test-case</a>
 */
class H2FrameTestSuiteTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String[] FRAME_TYPES = {
            "data",
            "headers",
            "priority",
            "rst_stream",
            "settings",
            "push_promise",
            "ping",
            "goaway",
            "window_update",
            "continuation"
    };

    // Codec strips PADDED and PRIORITY flags after processing padding/priority fields
    private static final int STRIPPED_FLAGS_MASK = ~(FLAG_PADDED | FLAG_PRIORITY);

    static Stream<Arguments> frameTestCases() throws IOException {
        List<Arguments> args = new ArrayList<>();

        for (String frameType : FRAME_TYPES) {
            String[] files = {"normal.json", "error.json"};
            for (String file : files) {
                String path = "http2-frame-test-case/" + frameType + "/" + file;
                try (InputStream is = H2FrameTestSuiteTest.class.getClassLoader().getResourceAsStream(path)) {
                    if (is == null) {
                        // normal.json must exist; error.json is optional
                        if (file.equals("normal.json")) {
                            throw new IllegalStateException("Missing frame test resource: " + path);
                        }
                        continue;
                    }

                    JsonNode root = MAPPER.readTree(is);
                    String wire = root.get("wire").asText();
                    JsonNode error = root.get("error");
                    JsonNode frame = root.get("frame");
                    String description = root.has("description") ? root.get("description").asText() : file;
                    String testName = frameType + "/" + file + ": " + description;

                    boolean expectError = error != null && !error.isNull();

                    if (!expectError && frame != null) {
                        int type = frame.get("type").asInt();
                        int flags = frame.get("flags").asInt();
                        int streamId = frame.get("stream_identifier").asInt();
                        JsonNode framePayload = frame.get("frame_payload");

                        args.add(Arguments.of(testName, wire, false, type, flags, streamId, framePayload));
                    } else if (expectError) {
                        args.add(Arguments.of(testName, wire, true, 0, 0, 0, null));
                    }
                }
            }
        }

        return args.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("frameTestCases")
    void decodeFrame(
            String description,
            String wireHex,
            boolean expectError,
            int expectedType,
            int expectedFlags,
            int expectedStreamId,
            JsonNode framePayload
    ) throws IOException {

        byte[] wireBytes = hexToBytes(wireHex);
        H2FrameCodec codec = new H2FrameCodec(new ByteArrayInputStream(wireBytes), new ByteArrayOutputStream(), 16384);

        if (expectError) {
            assertThrows(IOException.class, codec::readFrame, "Expected error for: " + description);
        } else {
            H2FrameCodec.Frame frame = codec.readFrame();

            assertNotNull(frame, "Frame should not be null for: " + description);
            assertEquals(expectedType, frame.type(), "Type mismatch for: " + description);
            assertEquals(expectedFlags & STRIPPED_FLAGS_MASK,
                    frame.flags() & STRIPPED_FLAGS_MASK,
                    "Flags mismatch for: " + description);
            assertEquals(expectedStreamId, frame.streamId(), "Stream ID mismatch for: " + description);

            verifyPayload(frame, framePayload, description);
        }
    }

    static Stream<Arguments> roundTripTestCases() throws IOException {
        List<Arguments> args = new ArrayList<>();

        for (String frameType : FRAME_TYPES) {
            String path = "http2-frame-test-case/" + frameType + "/normal.json";
            try (InputStream is = H2FrameTestSuiteTest.class.getClassLoader().getResourceAsStream(path)) {
                if (is == null) {
                    throw new IllegalStateException("Missing frame test resource: " + path);
                }

                JsonNode root = MAPPER.readTree(is);
                String wire = root.get("wire").asText();
                String description = root.has("description") ? root.get("description").asText() : "normal";
                args.add(Arguments.of(frameType + ": " + description, wire));
            }
        }

        return args.stream();
    }

    @ParameterizedTest(name = "roundtrip {0}")
    @MethodSource("roundTripTestCases")
    void roundTripFrame(String description, String wireHex) throws IOException {
        byte[] wireBytes = hexToBytes(wireHex);
        var decodeCodec = new H2FrameCodec(new ByteArrayInputStream(wireBytes), new ByteArrayOutputStream(), 16384);
        H2FrameCodec.Frame original = decodeCodec.readFrame();

        // Re-encode
        var encodeOut = new ByteArrayOutputStream();
        var encodeCodec = new H2FrameCodec(new ByteArrayInputStream(new byte[0]), encodeOut, 16384);
        encodeCodec.writeFrame(
                original.type(),
                original.flags(),
                original.streamId(),
                original.payload(),
                0,
                original.payloadLength());
        encodeCodec.flush();

        // Decode again
        var redecodeCodec = new H2FrameCodec(new ByteArrayInputStream(encodeOut.toByteArray()),
                new ByteArrayOutputStream(),
                16384);
        H2FrameCodec.Frame roundTripped = redecodeCodec.readFrame();

        // Verify matches
        assertEquals(original.type(), roundTripped.type(), "Type mismatch after round-trip: " + description);
        assertEquals(original.flags(), roundTripped.flags(), "Flags mismatch after round-trip: " + description);
        assertEquals(original.streamId(),
                roundTripped.streamId(),
                "StreamId mismatch after round-trip: " + description);
        assertEquals(original.payloadLength(),
                roundTripped.payloadLength(),
                "Length mismatch after round-trip: " + description);

        byte[] origPayload = new byte[original.payloadLength()];
        byte[] rtPayload = new byte[roundTripped.payloadLength()];
        System.arraycopy(original.payload(), 0, origPayload, 0, original.payloadLength());
        System.arraycopy(roundTripped.payload(), 0, rtPayload, 0, roundTripped.payloadLength());
        assertArrayEquals(origPayload, rtPayload, "Payload mismatch after round-trip: " + description);
    }

    private void verifyPayload(H2FrameCodec.Frame frame, JsonNode payload, String description) throws IOException {
        if (payload == null) {
            return;
        }

        switch (frame.type()) {
            case 0 -> verifyDataPayload(frame, payload, description); // DATA
            case 3 -> verifyRstStreamPayload(frame, payload, description); // RST_STREAM
            case 4 -> verifySettingsPayload(frame, payload, description); // SETTINGS
            case 6 -> verifyPingPayload(frame, payload, description); // PING
            case 7 -> verifyGoawayPayload(frame, payload, description); // GOAWAY
            case 8 -> verifyWindowUpdatePayload(frame, payload, description); // WINDOW_UPDATE
            default -> {
                // HEADERS, CONTINUATION, etc. have HPACK-encoded payloads
            }
        }
    }

    private void verifyDataPayload(H2FrameCodec.Frame frame, JsonNode payload, String description) {
        if (payload.has("data")) {
            String expectedData = payload.get("data").asText();
            String actualData = new String(frame.payload(), 0, frame.payloadLength(), StandardCharsets.UTF_8);
            assertEquals(expectedData, actualData, "DATA payload mismatch for: " + description);
        }
    }

    private void verifyRstStreamPayload(H2FrameCodec.Frame frame, JsonNode payload, String description)
            throws IOException {
        if (payload.has("error_code")) {
            int expectedErrorCode = payload.get("error_code").asInt();
            int actualErrorCode = frame.parseRstStream();
            assertEquals(expectedErrorCode, actualErrorCode, "RST_STREAM error_code mismatch for: " + description);
        }
    }

    private void verifySettingsPayload(H2FrameCodec.Frame frame, JsonNode payload, String description)
            throws IOException {
        if (payload.has("settings")) {
            int[] settings = frame.parseSettings();
            JsonNode expectedSettings = payload.get("settings");
            assertEquals(expectedSettings.size() * 2,
                    settings.length,
                    "SETTINGS count mismatch for: " + description);
            for (int i = 0; i < expectedSettings.size(); i++) {
                JsonNode pair = expectedSettings.get(i);
                assertEquals(pair.get(0).asInt(),
                        settings[i * 2],
                        "SETTINGS id mismatch at " + i + " for: " + description);
                assertEquals(pair.get(1).asInt(),
                        settings[i * 2 + 1],
                        "SETTINGS value mismatch at " + i + " for: " + description);
            }
        }
    }

    private void verifyPingPayload(H2FrameCodec.Frame frame, JsonNode payload, String description) {
        if (payload.has("opaque_data")) {
            String expectedStr = payload.get("opaque_data").asText();
            byte[] expected = expectedStr.getBytes(StandardCharsets.US_ASCII);
            byte[] actual = new byte[frame.payloadLength()];
            System.arraycopy(frame.payload(), 0, actual, 0, frame.payloadLength());
            assertArrayEquals(expected, actual, "PING opaque_data mismatch for: " + description);
        }
    }

    private void verifyGoawayPayload(H2FrameCodec.Frame frame, JsonNode payload, String description)
            throws IOException {
        int[] goaway = frame.parseGoaway();
        if (payload.has("last_stream_id")) {
            assertEquals(payload.get("last_stream_id").asInt(),
                    goaway[0],
                    "GOAWAY last_stream_id mismatch for: " + description);
        }
        if (payload.has("error_code")) {
            assertEquals(payload.get("error_code").asInt(),
                    goaway[1],
                    "GOAWAY error_code mismatch for: " + description);
        }
    }

    private void verifyWindowUpdatePayload(H2FrameCodec.Frame frame, JsonNode payload, String description)
            throws IOException {
        if (payload.has("window_size_increment")) {
            int expected = payload.get("window_size_increment").asInt();
            int actual = frame.parseWindowUpdate();
            assertEquals(expected, actual, "WINDOW_UPDATE increment mismatch for: " + description);
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
