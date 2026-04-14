/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.hpack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * HPACK decoder test suite using test vectors from http2jp/hpack-test-case.
 *
 * <p>These test vectors are from the nghttp2 implementation and cover various
 * HPACK encoding scenarios including Huffman encoding, dynamic table operations,
 * and indexed headers.
 *
 * @see <a href="https://github.com/http2jp/hpack-test-case">hpack-test-case</a>
 */
class HpackTestSuiteTest {

    // Cache decoders per story file to maintain dynamic table state across cases
    private static final Map<String, HpackDecoder> DECODERS = new ConcurrentHashMap<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static Stream<Arguments> hpackTestCases() throws IOException {
        List<Arguments> args = new ArrayList<>();

        for (int i = 0; i <= 31; i++) {
            String filename = String.format("hpack-test-case/story_%02d.json", i);
            InputStream is = HpackTestSuiteTest.class.getClassLoader().getResourceAsStream(filename);
            if (is == null) {
                continue;
            }

            JsonNode root = MAPPER.readTree(is);
            JsonNode cases = root.get("cases");
            if (cases == null) {
                continue;
            }

            for (JsonNode testCase : cases) {
                int seqno = testCase.get("seqno").asInt();
                String wire = testCase.get("wire").asText();
                JsonNode headers = testCase.get("headers");

                List<String[]> expectedHeaders = new ArrayList<>();
                for (JsonNode header : headers) {
                    var fields = header.fields();
                    while (fields.hasNext()) {
                        var field = fields.next();
                        expectedHeaders.add(new String[] {field.getKey(), field.getValue().asText()});
                    }
                }

                args.add(Arguments.of(filename, seqno, wire, expectedHeaders));
            }
        }

        return args.stream();
    }

    @ParameterizedTest(name = "{0} case {1}")
    @MethodSource("hpackTestCases")
    void decodeTestCase(String filename, int seqno, String wireHex, List<String[]> expectedHeaders)
            throws IOException {
        // Each story uses a shared decoder to maintain dynamic table state across cases
        HpackDecoder decoder = getDecoderForStory(filename);

        // Decode this case's wire bytes
        byte[] wireBytes = hexToBytes(wireHex);
        List<String> result = decoder.decode(wireBytes);

        // Verify the decoded headers match expected (result is flat: name0, value0, name1, value1, ...)
        assertEquals(expectedHeaders.size(),
                result.size() / 2,
                "Header count mismatch for " + filename + " case " + seqno);

        for (int i = 0; i < expectedHeaders.size(); i++) {
            String[] expected = expectedHeaders.get(i);
            String actualName = result.get(i * 2);
            String actualValue = result.get(i * 2 + 1);

            assertEquals(expected[0],
                    actualName,
                    "Header name mismatch at index " + i + " for " + filename + " case " + seqno);
            assertEquals(expected[1],
                    actualValue,
                    "Header value mismatch at index " + i + " for " + filename + " case " + seqno);
        }
    }

    private HpackDecoder getDecoderForStory(String filename) {
        return DECODERS.computeIfAbsent(filename, k -> new HpackDecoder(4096));
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
