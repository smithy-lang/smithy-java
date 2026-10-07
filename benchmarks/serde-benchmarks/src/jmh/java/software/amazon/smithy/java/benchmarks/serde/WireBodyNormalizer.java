/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import software.amazon.smithy.protocoltests.traits.HttpMessageTestCase;

/**
 * Removes formatting whitespace from JSON benchmark bodies, preserving opaque payloads.
 *
 * <p>The byte scanner preserves number lexemes, string escapes, and member order exactly.
 * Parsing and reprinting JSON could change the wire representation being benchmarked.
 * Bodies are assumed to be valid for their media type; this is not a JSON validator.
 */
final class WireBodyNormalizer {

    private WireBodyNormalizer() {}

    static byte[] normalize(byte[] body, HttpMessageTestCase testCase, String defaultContentType) {
        String contentType = testCase.getBodyMediaType()
                .orElseGet(() -> testCase.getHeaders()
                        .entrySet()
                        .stream()
                        .filter(header -> header.getKey().equalsIgnoreCase("Content-Type"))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(defaultContentType));
        return normalize(body, contentType);
    }

    static byte[] normalize(byte[] body, String contentType) {
        String type = contentType.toLowerCase(Locale.ROOT);
        if (type.contains("json")) {
            return minifyJson(body);
        }
        return body;
    }

    static byte[] minifyJson(byte[] body) {
        byte[] out = new byte[body.length];
        int n = 0;
        boolean inString = false;
        for (int i = 0; i < body.length; i++) {
            byte b = body[i];
            if (inString) {
                out[n++] = b;
                if (b == '\\' && i + 1 < body.length) {
                    out[n++] = body[++i];
                } else if (b == '"') {
                    inString = false;
                }
            } else if (b == '"') {
                inString = true;
                out[n++] = b;
            } else if (!isWhitespace(b)) {
                out[n++] = b;
            }
        }
        return n == body.length ? body : Arrays.copyOf(out, n);
    }

    private static boolean isWhitespace(byte b) {
        return b == ' ' || b == '\n' || b == '\r' || b == '\t';
    }
}
