/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

/**
 * HTTP header utilities.
 */
public final class HeaderUtils {
    // Header name lookup table: 0=invalid, 1=valid lower, 2=valid upper.
    private static final byte[] HEADER_NAME_TABLE = new byte[128];

    static {
        for (char c = 'a'; c <= 'z'; c++) {
            HEADER_NAME_TABLE[c] = 1;
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            HEADER_NAME_TABLE[c] = 2;
        }
        String validChars = "!#$%&'*+-.0123456789^_`|~";
        for (int i = 0; i < validChars.length(); i++) {
            HEADER_NAME_TABLE[validChars.charAt(i)] = 1;
        }
    }

    private HeaderUtils() {}

    /**
     * Normalizes an HTTP header name by trimming OWS (SP/HTAB), converting ASCII uppercase to lowercase, and
     * validating per RFC 7230 token rules.
     *
     * @param name the header name to normalize
     * @return the normalized header name, or the original instance if already normalized
     * @throws IllegalArgumentException if the name is empty, whitespace-only, or contains invalid characters
     */
    static String normalizeName(String name) {
        // Simulate String.trim(), but we only want to trim leading and trailing ' ' and '\t'.
        int len = name.length();
        int end = len - 1;
        int start = trimStart(name, end);
        end = trimEnd(name, start);
        if (start > end) {
            throw new IllegalArgumentException("Header name is empty or whitespace-only");
        }

        // Ensure each character is valid and see if any are uppercase.
        boolean needsLower = false;
        for (int i = start; i <= end; i++) {
            char c = name.charAt(i);
            if (c >= 128) {
                throw invalidHeaderNameChar(name);
            }
            byte b = HEADER_NAME_TABLE[c];
            if (b == 0) {
                throw invalidHeaderNameChar(name);
            } else if (b == 2) {
                needsLower = true;
            }
        }

        // No lower casing needed means trim or return as-is.
        if (!needsLower) {
            return start == 0 && end == len - 1
                    ? name
                    : name.substring(start, end + 1);
        }

        // needs lowercasing and possible trimming.
        char[] chars = new char[end - start + 1];
        for (int src = start, dst = 0; src <= end; src++, dst++) {
            char c = name.charAt(src);
            chars[dst] = HEADER_NAME_TABLE[c] == 2 ? (char) (c + 32) : c;
        }

        return new String(chars);
    }

    /**
     * Normalizes an HTTP header value by trimming leading/trailing OWS (SP/HTAB) and validating per RFC 7230
     * field-content rules.
     *
     * <p>Interior SP/HTAB are allowed. CR, LF, and other control characters are rejected. Empty values are permitted
     * per RFC 7230.
     *
     * @param value the header value to normalize
     * @return the normalized header value, or the original instance if already normalized
     * @throws IllegalArgumentException if the value contains invalid characters
     */
    public static String normalizeValue(String value) {
        int len = value.length();
        if (len == 0) {
            return value;
        }

        // Peek at the boundaries to detect whether trimming is needed.
        char first = value.charAt(0);
        char last = value.charAt(len - 1);
        boolean trimNeeded = first == ' ' || first == '\t' || last == ' ' || last == '\t';

        // Ensure valid chars match obs-text
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c > 0xFF || (c < 0x20 && c != '\t') || c == 0x7F) {
                throw invalidHeaderValueChar(value);
            }
        }

        if (!trimNeeded) {
            return value;
        }

        int end = len - 1;
        int start = trimStart(value, end);
        end = trimEnd(value, start);
        if (start > end) {
            return "";
        }

        return value.substring(start, end + 1);
    }

    private static int trimStart(String s, int end) {
        for (int start = 0; start <= end; start++) {
            char c = s.charAt(start);
            if (c != ' ' && c != '\t') {
                return start;
            }
        }
        return end + 1;
    }

    private static int trimEnd(String s, int start) {
        for (int end = s.length() - 1; end >= start; end--) {
            char c = s.charAt(end);
            if (c != ' ' && c != '\t') {
                return end;
            }
        }
        return start - 1;
    }

    private static IllegalArgumentException invalidHeaderNameChar(String value) {
        return new IllegalArgumentException("Invalid header name: " + value);
    }

    private static IllegalArgumentException invalidHeaderValueChar(String value) {
        return new IllegalArgumentException("Invalid header value: " + value);
    }
}
