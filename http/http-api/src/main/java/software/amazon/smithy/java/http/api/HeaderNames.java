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

    // Direct-indexed by header name length. GROUPS[len] is the candidate array for that length, or null.
    private static final String[][] GROUPS = new String[29][];

    static {
        GROUPS[3] = new String[] {AGE, VIA};
        GROUPS[4] = new String[] {DATE, ETAG, FROM, HOST, LINK, VARY};
        GROUPS[5] = new String[] {ALLOW, RANGE, PSEUDO_PATH};
        GROUPS[6] = new String[] {ACCEPT, COOKIE, EXPECT, SERVER};
        GROUPS[7] = new String[] {
                EXPIRES,
                REFERER,
                REFRESH,
                TRAILER,
                UPGRADE,
                PSEUDO_METHOD,
                PSEUDO_SCHEME,
                PSEUDO_STATUS
        };
        GROUPS[8] = new String[] {IF_MATCH, IF_RANGE, LOCATION};
        GROUPS[10] = new String[] {CONNECTION, KEEP_ALIVE, SET_COOKIE, USER_AGENT, PSEUDO_AUTHORITY};
        GROUPS[11] = new String[] {RETRY_AFTER};
        GROUPS[12] = new String[] {CONTENT_TYPE, MAX_FORWARDS};
        GROUPS[13] = new String[] {
                ACCEPT_RANGES,
                AUTHORIZATION,
                CACHE_CONTROL,
                CONTENT_RANGE,
                IF_NONE_MATCH,
                LAST_MODIFIED
        };
        GROUPS[14] = new String[] {CONTENT_LENGTH};
        GROUPS[15] = new String[] {ACCEPT_ENCODING, ACCEPT_LANGUAGE};
        GROUPS[16] = new String[] {
                CONTENT_ENCODING,
                CONTENT_LANGUAGE,
                CONTENT_LOCATION,
                PROXY_CONNECTION,
                WWW_AUTHENTICATE,
                X_AMZN_REQUESTID,
                X_AMZ_REQUEST_ID
        };
        GROUPS[17] = new String[] {IF_MODIFIED_SINCE, TRANSFER_ENCODING};
        GROUPS[18] = new String[] {PROXY_AUTHENTICATE};
        GROUPS[19] = new String[] {CONTENT_DISPOSITION, IF_UNMODIFIED_SINCE, PROXY_AUTHORIZATION};
        GROUPS[25] = new String[] {STRICT_TRANSPORT_SECURITY};
        GROUPS[28] = new String[] {ACCESS_CONTROL_ALLOW_ORIGIN};
    }

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
        int len = name.length();
        if (len < GROUPS.length) {
            String[] candidates = GROUPS[len];
            if (candidates != null) {
                return match(name, candidates);
            }
        }
        return HeaderUtils.normalizeName(name);
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
        if (length < GROUPS.length) {
            String[] candidates = GROUPS[length];
            if (candidates != null) {
                return match(buf, offset, length, candidates);
            }
        }
        return newLowerString(buf, offset, length);
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
