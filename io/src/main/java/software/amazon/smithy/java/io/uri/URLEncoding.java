/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.uri;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Handles percent-encoding and decoding URLs.
 */
public class URLEncoding {
    /**
     * Encodes characters that are not unreserved into a string builder.
     * <p>
     * <code>
     *     unreserved  = ALPHA / DIGIT / "-" / "." / "_" / "~"
     * </code>
     * Can optionally handle strings which are meant to encode a path (ie include '/' which should NOT be escaped for paths).
     *
     * @param source The raw string to encode. Note that any existing percent-encoding will be encoded again.
     * @param sink   Where to write the percent-encoded string.
     * @param ignoreSlashes  true if the value is intended to represent a path.
     */
    public static void encodeUnreserved(String source, StringBuilder sink, boolean ignoreSlashes) {
        // Encode the path segment and undo some of the assumption of URLEncoder to make it with unreserved.
        String result = java.net.URLEncoder.encode(source, StandardCharsets.UTF_8);
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            switch (c) {
                case '+' -> sink.append("%20");
                case '*' -> sink.append("%2A");
                case '%' -> {
                    switch (result.charAt(i + 1)) {
                        case '7' -> {
                            if (i < result.length() - 1 && result.charAt(i + 2) == 'E') {
                                sink.append('~');
                                i += 2;
                                break;
                            }
                            sink.append(c);
                        }
                        case '2' -> {
                            if (i < result.length() - 1 && result.charAt(i + 2) == 'F' && ignoreSlashes) {
                                sink.append('/');
                                i += 2;
                                break;
                            }
                            sink.append(c);
                        }
                        default -> sink.append(c);
                    }
                }
                default -> sink.append(c);
            }
        }
    }

    /**
     * Encodes characters that are not unreserved into a string result.
     *
     * @param source Value to encode.
     * @param ignoreSlashes true if the value is intended to represent a path.
     * @return Returns the encoded string.
     */
    public static String encodeUnreserved(String source, boolean ignoreSlashes) {
        StringBuilder result = new StringBuilder();
        encodeUnreserved(source, result, ignoreSlashes);
        return result.toString();
    }

    /**
     * Decode a percent-encoded string.
     * <p>
     * Assumes the decoded string is UTF-8 encoded.
     *
     * @param value The string to decode.
     * @return The decoded string.
     */
    public static String urlDecode(String value) {
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
