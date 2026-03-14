/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.nio.charset.StandardCharsets;

/**
 * Canonical HTTP header name constants with fast case-insensitive lookup.
 *
 * <p>All constants are pre-allocated lowercase strings. After canonicalization,
 * known headers can be compared with {@code ==} for O(1) lookup.
 *
 * <p>Uses length-based switching for fast lookup with zero per-lookup allocations.
 */
public final class HeaderNames {

    private HeaderNames() {}

    // === HTTP/2 Pseudo-Headers ===
    public static final String PSEUDO_AUTHORITY = ":authority";
    public static final String PSEUDO_METHOD = ":method";
    public static final String PSEUDO_PATH = ":path";
    public static final String PSEUDO_SCHEME = ":scheme";
    public static final String PSEUDO_STATUS = ":status";

    // === Standard Headers (alphabetical) ===
    public static final String ACCEPT = "accept";
    public static final String ACCEPT_CHARSET = "accept-charset";
    public static final String ACCEPT_ENCODING = "accept-encoding";
    public static final String ACCEPT_LANGUAGE = "accept-language";
    public static final String ACCEPT_RANGES = "accept-ranges";
    public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "access-control-allow-origin";
    public static final String AGE = "age";
    public static final String ALLOW = "allow";
    public static final String AUTHORIZATION = "authorization";
    public static final String CACHE_CONTROL = "cache-control";
    public static final String CONNECTION = "connection";
    public static final String CONTENT_DISPOSITION = "content-disposition";
    public static final String CONTENT_ENCODING = "content-encoding";
    public static final String CONTENT_LANGUAGE = "content-language";
    public static final String CONTENT_LENGTH = "content-length";
    public static final String CONTENT_LOCATION = "content-location";
    public static final String CONTENT_RANGE = "content-range";
    public static final String CONTENT_TYPE = "content-type";
    public static final String COOKIE = "cookie";
    public static final String DATE = "date";
    public static final String ETAG = "etag";
    public static final String EXPECT = "expect";
    public static final String EXPIRES = "expires";
    public static final String FROM = "from";
    public static final String HOST = "host";
    public static final String IF_MATCH = "if-match";
    public static final String IF_MODIFIED_SINCE = "if-modified-since";
    public static final String IF_NONE_MATCH = "if-none-match";
    public static final String IF_RANGE = "if-range";
    public static final String IF_UNMODIFIED_SINCE = "if-unmodified-since";
    public static final String KEEP_ALIVE = "keep-alive";
    public static final String LAST_MODIFIED = "last-modified";
    public static final String LINK = "link";
    public static final String LOCATION = "location";
    public static final String MAX_FORWARDS = "max-forwards";
    public static final String PROXY_AUTHENTICATE = "proxy-authenticate";
    public static final String PROXY_AUTHORIZATION = "proxy-authorization";
    public static final String PROXY_CONNECTION = "proxy-connection";
    public static final String RANGE = "range";
    public static final String REFERER = "referer";
    public static final String REFRESH = "refresh";
    public static final String RETRY_AFTER = "retry-after";
    public static final String SERVER = "server";
    public static final String SET_COOKIE = "set-cookie";
    public static final String STRICT_TRANSPORT_SECURITY = "strict-transport-security";
    public static final String TRAILER = "trailer";
    public static final String TRANSFER_ENCODING = "transfer-encoding";
    public static final String UPGRADE = "upgrade";
    public static final String USER_AGENT = "user-agent";
    public static final String VARY = "vary";
    public static final String VIA = "via";
    public static final String WWW_AUTHENTICATE = "www-authenticate";

    // === Amazon-specific Headers ===
    public static final String X_AMZN_REQUESTID = "x-amzn-requestid";
    public static final String X_AMZ_REQUEST_ID = "x-amz-request-id";

    // Groups by length for switch-based lookup
    private static final String[] LEN_3 = {AGE, VIA};
    private static final String[] LEN_4 = {DATE, ETAG, FROM, HOST, LINK, VARY};
    private static final String[] LEN_5 = {ALLOW, RANGE, PSEUDO_PATH};
    private static final String[] LEN_6 = {ACCEPT, COOKIE, EXPECT, SERVER};
    private static final String[] LEN_7 =
            {EXPIRES, REFERER, REFRESH, TRAILER, UPGRADE, PSEUDO_METHOD, PSEUDO_SCHEME, PSEUDO_STATUS};
    private static final String[] LEN_8 = {IF_MATCH, IF_RANGE, LOCATION};
    private static final String[] LEN_10 = {CONNECTION, KEEP_ALIVE, SET_COOKIE, USER_AGENT, PSEUDO_AUTHORITY};
    private static final String[] LEN_11 = {RETRY_AFTER};
    private static final String[] LEN_12 = {CONTENT_TYPE, MAX_FORWARDS};
    private static final String[] LEN_13 =
            {ACCEPT_RANGES, AUTHORIZATION, CACHE_CONTROL, CONTENT_RANGE, IF_NONE_MATCH, LAST_MODIFIED};
    private static final String[] LEN_14 = {CONTENT_LENGTH};
    private static final String[] LEN_15 = {ACCEPT_ENCODING, ACCEPT_LANGUAGE};
    private static final String[] LEN_16 = {CONTENT_ENCODING,
            CONTENT_LANGUAGE,
            CONTENT_LOCATION,
            PROXY_CONNECTION,
            WWW_AUTHENTICATE,
            X_AMZN_REQUESTID,
            X_AMZ_REQUEST_ID};
    private static final String[] LEN_17 = {IF_MODIFIED_SINCE, TRANSFER_ENCODING};
    private static final String[] LEN_18 = {PROXY_AUTHENTICATE};
    private static final String[] LEN_19 = {CONTENT_DISPOSITION, IF_UNMODIFIED_SINCE, PROXY_AUTHORIZATION};
    private static final String[] LEN_25 = {STRICT_TRANSPORT_SECURITY};
    private static final String[] LEN_28 = {ACCESS_CONTROL_ALLOW_ORIGIN};

    /**
     * Canonicalize a header name to its canonical lowercase form.
     *
     * <p>Returns a canonical constant if the name matches a known header (case-insensitive).
     * For unknown headers, returns a new lowercased String.
     *
     * @param name the header name to canonicalize
     * @return canonical constant for known headers, lowercased string for unknown
     */
    public static String canonicalize(String name) {
        return switch (name.length()) {
            case 3 -> match(name, LEN_3);
            case 4 -> match(name, LEN_4);
            case 5 -> match(name, LEN_5);
            case 6 -> match(name, LEN_6);
            case 7 -> match(name, LEN_7);
            case 8 -> match(name, LEN_8);
            case 10 -> match(name, LEN_10);
            case 11 -> match(name, LEN_11);
            case 12 -> match(name, LEN_12);
            case 13 -> match(name, LEN_13);
            case 14 -> match(name, LEN_14);
            case 15 -> match(name, LEN_15);
            case 16 -> match(name, LEN_16);
            case 17 -> match(name, LEN_17);
            case 18 -> match(name, LEN_18);
            case 19 -> match(name, LEN_19);
            case 25 -> match(name, LEN_25);
            case 28 -> match(name, LEN_28);
            default -> HeaderUtils.normalizeName(name);
        };
    }

    /**
     * Canonicalize a header name directly from bytes.
     *
     * <p>Zero-copy for known headers - returns canonical constant without allocating
     * an intermediate String. For unknown headers, allocates a new lowercased String.
     *
     * @param buf byte buffer containing header name (ASCII)
     * @param offset start offset in buffer
     * @param length length of header name
     * @return canonical constant for known headers, new lowercased String for unknown
     */
    public static String canonicalize(byte[] buf, int offset, int length) {
        return switch (length) {
            case 3 -> match(buf, offset, length, LEN_3);
            case 4 -> match(buf, offset, length, LEN_4);
            case 5 -> match(buf, offset, length, LEN_5);
            case 6 -> match(buf, offset, length, LEN_6);
            case 7 -> match(buf, offset, length, LEN_7);
            case 8 -> match(buf, offset, length, LEN_8);
            case 10 -> match(buf, offset, length, LEN_10);
            case 11 -> match(buf, offset, length, LEN_11);
            case 12 -> match(buf, offset, length, LEN_12);
            case 13 -> match(buf, offset, length, LEN_13);
            case 14 -> match(buf, offset, length, LEN_14);
            case 15 -> match(buf, offset, length, LEN_15);
            case 16 -> match(buf, offset, length, LEN_16);
            case 17 -> match(buf, offset, length, LEN_17);
            case 18 -> match(buf, offset, length, LEN_18);
            case 19 -> match(buf, offset, length, LEN_19);
            case 25 -> match(buf, offset, length, LEN_25);
            case 28 -> match(buf, offset, length, LEN_28);
            default -> newLowerString(buf, offset, length);
        };
    }

    private static String match(String input, String[] candidates) {
        for (String candidate : candidates) {
            if (equalsIgnoreCase(input, candidate)) {
                return candidate;
            }
        }
        return HeaderUtils.normalizeName(input);
    }

    private static String match(byte[] buf, int offset, int length, String[] candidates) {
        for (String candidate : candidates) {
            if (equalsIgnoreCase(buf, offset, candidate)) {
                return candidate;
            }
        }
        return newLowerString(buf, offset, length);
    }

    private static boolean equalsIgnoreCase(String input, String candidate) {
        for (int i = 0; i < candidate.length(); i++) {
            char c = input.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                c = (char) (c + 32);
            }
            if (c != candidate.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean equalsIgnoreCase(byte[] buf, int offset, String candidate) {
        for (int i = 0; i < candidate.length(); i++) {
            byte b = buf[offset + i];
            if (b >= 'A' && b <= 'Z') {
                b = (byte) (b + 32);
            }
            if (b != candidate.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static String newLowerString(byte[] buf, int offset, int length) {
        boolean needsLower = false;
        for (int i = 0; i < length; i++) {
            byte b = buf[offset + i];
            if (b >= 'A' && b <= 'Z') {
                needsLower = true;
                break;
            }
        }

        if (!needsLower) {
            return new String(buf, offset, length, StandardCharsets.US_ASCII);
        }

        byte[] lower = new byte[length];
        for (int i = 0; i < length; i++) {
            byte b = buf[offset + i];
            lower[i] = (b >= 'A' && b <= 'Z') ? (byte) (b + 32) : b;
        }
        return new String(lower, 0, length, StandardCharsets.US_ASCII);
    }
}
