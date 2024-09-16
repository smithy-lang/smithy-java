/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.io.uri;

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
     *
     * @param source The raw string to encode. Note that any existing percent-encoding will be encoded again.
     * @param sink   Where to write the percent-encoded string.
     */

    public static void encodeUnreserved(String source, StringBuilder sink) {
        encodeUnreserved(source, sink, false);
    }

    public static void encodeUnreserved(String source, StringBuilder sink, boolean ignoreSlashes) {
        // Encode the path segment and undo some of the assumption of URLEncoder to make it with unreserved.
        String result = java.net.URLEncoder.encode(source, StandardCharsets.UTF_8);
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            switch (c) {
                case '+' -> sink.append("%20");
                case '*' -> sink.append("%2A");
                case '%' -> {
                    if (i >= result.length() - 1) {
                        sink.append(c);
                    }
                    switch (result.charAt(i + 1)) {
                        case '7' -> {
                            if (result.charAt(i + 2) == 'E') {
                                sink.append('~');
                                i += 2;
                            } else {
                                sink.append(c);
                            }
                        }
                        case '2' -> {
                            switch (result.charAt(i + 2)) {
                                case 'F' -> {
                                    if (ignoreSlashes) {
                                        sink.append('/');
                                        i += 2;
                                    } else {
                                        sink.append(c);
                                    }
                                }
                                case '5' -> {
                                    sink.append('%');
                                    i += 2;
                                }
                                default -> sink.append(c);
                            }
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
     * @return Returns the encoded string.
     */
    public static String encodeUnreserved(String source) {
        StringBuilder result = new StringBuilder();
        encodeUnreserved(source, result);
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
