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
 * known headers can be compared with {@code ==}.
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
    public static final String AMZ_SDK_REQUEST = "amz-sdk-request";
    public static final String X_AMZN_REQUESTID = "x-amzn-requestid";
    public static final String X_AMZN_TRACE_ID = "x-amzn-trace-id";
    public static final String X_AMZ_REQUEST_ID = "x-amz-request-id";

    // Direct-indexed by header name length. GROUPS[len] is the candidate array for that length, or null.
    private static final String[][] GROUPS = new String[28][];

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
        GROUPS[14] = new String[] {CONTENT_LENGTH, ACCEPT_CHARSET};
        GROUPS[15] = new String[] {ACCEPT_ENCODING, ACCEPT_LANGUAGE, AMZ_SDK_REQUEST, X_AMZN_TRACE_ID};
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
        GROUPS[27] = new String[] {ACCESS_CONTROL_ALLOW_ORIGIN};
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

    private static String match(byte[] buf, int offset, int length, String[] candidates) {
        for (String candidate : candidates) {
            if (equalsIgnoreCase(buf, offset, candidate)) {
                return candidate;
            }
        }
        return newLowerString(buf, offset, length);
    }

    // ASCII-only case-insensitive comparison using `| 0x20` instead of branching.
    //
    // Safe for the canonical header names in this class because every candidate contains
    // only [a-z], [0-9], '-' or ':'.
    //
    // The `| 0x20` trick maps 'A'-'Z' -> 'a'-'z', but it also remaps some other ASCII
    // characters, for example: '@' -> '`', '[' -> '{', '\' -> '|', ']' -> '}', '^' -> '~'
    //
    // A false positive would require one of those remapped characters to appear in a canonical candidate at the
    // same position. That cannot happen here because none of '`', '{', '|', '}', or '~' appear in any canonical
    // header name in this table.
    //
    // If a future canonical header name contains one of those characters, this optimization must be revisited.
    private static boolean equalsIgnoreCase(byte[] buf, int offset, String candidate) {
        for (int i = 0; i < candidate.length(); i++) {
            if ((buf[offset + i] | 0x20) != candidate.charAt(i)) {
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

    // ----- Generated bucket-based string canonicalization.
    // the bucket methods should always be updated to match the GROUPS array.

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
            case 3 -> matchBucket3(name);
            case 4 -> matchBucket4(name);
            case 5 -> matchBucket5(name);
            case 6 -> matchBucket6(name);
            case 7 -> matchBucket7(name);
            case 8 -> matchBucket8(name);
            case 10 -> matchBucket10(name);
            case 11 -> matchBucket11(name);
            case 12 -> matchBucket12(name);
            case 13 -> matchBucket13(name);
            case 14 -> matchBucket14(name);
            case 15 -> matchBucket15(name);
            case 16 -> matchBucket16(name);
            case 17 -> matchBucket17(name);
            case 18 -> matchBucket18(name);
            case 19 -> matchBucket19(name);
            case 25 -> matchBucket25(name);
            case 27 -> matchBucket27(name);
            default -> HeaderUtils.normalizeName(name);
        };
    }

    private static String matchBucket3(String name) {
        switch (name.charAt(0) | 0x20) {
            case 'a': // [a]ge
                if (name.regionMatches(true, 0, AGE, 0, 3)) {
                    return AGE;
                }
                break;
            case 'v': // [v]ia
                if (name.regionMatches(true, 0, VIA, 0, 3)) {
                    return VIA;
                }
                break;
        }
        return HeaderUtils.normalizeName(name);
    }

    private static String matchBucket4(String name) {
        switch (name.charAt(0) | 0x20) {
            case 'h': // [h]ost
                if (name.regionMatches(true, 0, HOST, 0, 4)) {
                    return HOST;
                }
                break;
            case 'd': // [d]ate
                if (name.regionMatches(true, 0, DATE, 0, 4)) {
                    return DATE;
                }
                break;
            case 'e': // [e]tag
                if (name.regionMatches(true, 0, ETAG, 0, 4)) {
                    return ETAG;
                }
                break;
            case 'f': // [f]rom
                if (name.regionMatches(true, 0, FROM, 0, 4)) {
                    return FROM;
                }
                break;
            case 'l': // [l]ink
                if (name.regionMatches(true, 0, LINK, 0, 4)) {
                    return LINK;
                }
                break;
            case 'v': // [v]ary
                if (name.regionMatches(true, 0, VARY, 0, 4)) {
                    return VARY;
                }
                break;
        }
        return HeaderUtils.normalizeName(name);
    }

    private static String matchBucket5(String name) {
        switch (name.charAt(0) | 0x20) {
            case ':': // [:path]
                if (name.regionMatches(true, 0, PSEUDO_PATH, 0, 5)) {
                    return PSEUDO_PATH;
                }
                break;
            case 'a': // [a]llow
                if (name.regionMatches(true, 0, ALLOW, 0, 5)) {
                    return ALLOW;
                }
                break;
            case 'r': // [r]ange
                if (name.regionMatches(true, 0, RANGE, 0, 5)) {
                    return RANGE;
                }
                break;
        }
        return HeaderUtils.normalizeName(name);
    }

    private static String matchBucket6(String name) {
        switch (name.charAt(0) | 0x20) {
            case 'a': // [a]ccept
                if (name.regionMatches(true, 0, ACCEPT, 0, 6)) {
                    return ACCEPT;
                }
                break;
            case 'c': // [c]ookie
                if (name.regionMatches(true, 0, COOKIE, 0, 6)) {
                    return COOKIE;
                }
                break;
            case 'e': // [e]xpect
                if (name.regionMatches(true, 0, EXPECT, 0, 6)) {
                    return EXPECT;
                }
                break;
            case 's': // [s]erver
                if (name.regionMatches(true, 0, SERVER, 0, 6)) {
                    return SERVER;
                }
                break;
        }
        return HeaderUtils.normalizeName(name);
    }

    private static String matchBucket7(String name) {
        switch (name.charAt(2) | 0x20) {
            case 'a': // tr[a]iler
                if (name.regionMatches(true, 0, TRAILER, 0, 7)) {
                    return TRAILER;
                }
                break;
            case 'c': // ":s[c]heme"
                if (name.regionMatches(true, 0, PSEUDO_SCHEME, 0, 7)) {
                    return PSEUDO_SCHEME;
                }
                break;
            case 'e': // ":m[e]thod"
                if (name.regionMatches(true, 0, PSEUDO_METHOD, 0, 7)) {
                    return PSEUDO_METHOD;
                }
                break;
            case 'f': // "re[f]erer" / "re[f]resh"
                switch (name.charAt(3) | 0x20) {
                    case 'e': // "ref(e)rer"
                        if (name.regionMatches(true, 0, REFERER, 0, 7)) {
                            return REFERER;
                        }
                        break;
                    case 'r': // "ref[r]esh"
                        if (name.regionMatches(true, 0, REFRESH, 0, 7)) {
                            return REFRESH;
                        }
                        break;
                }
                break;
            case 'g': // "up[g]rade"
                if (name.regionMatches(true, 0, UPGRADE, 0, 7)) {
                    return UPGRADE;
                }
                break;
            case 'p': // "ex[p]ires"
                if (name.regionMatches(true, 0, EXPIRES, 0, 7)) {
                    return EXPIRES;
                }
                break;
            case 't': // ":s[t]atus"
                if (name.regionMatches(true, 0, PSEUDO_STATUS, 0, 7)) {
                    return PSEUDO_STATUS;
                }
                break;
        }
        return HeaderUtils.normalizeName(name);
    }

    private static String matchBucket8(String name) {
        switch (name.charAt(3) | 0x20) {
            case 'a': // loc[a]tion
                if (name.regionMatches(true, 0, LOCATION, 0, 8)) {
                    return LOCATION;
                }
                break;
            case 'm': // if-[m]atch
                if (name.regionMatches(true, 0, IF_MATCH, 0, 8)) {
                    return IF_MATCH;
                }
                break;
            case 'r': // if-[r]ange
                if (name.regionMatches(true, 0, IF_RANGE, 0, 8)) {
                    return IF_RANGE;
                }
                break;
        }
        return HeaderUtils.normalizeName(name);
    }

    private static String matchBucket10(String name) {
        switch (name.charAt(0) | 0x20) {
            case ':': // [:]authority
                if (name.regionMatches(true, 0, PSEUDO_AUTHORITY, 0, 10)) {
                    return PSEUDO_AUTHORITY;
                }
                break;
            case 'c': // [c]onnection
                if (name.regionMatches(true, 0, CONNECTION, 0, 10)) {
                    return CONNECTION;
                }
                break;
            case 'k': // [k]eep-alive
                if (name.regionMatches(true, 0, KEEP_ALIVE, 0, 10)) {
                    return KEEP_ALIVE;
                }
                break;
            case 's': // [s]et-cookie
                if (name.regionMatches(true, 0, SET_COOKIE, 0, 10)) {
                    return SET_COOKIE;
                }
                break;
            case 'u': // [u]ser-agent
                if (name.regionMatches(true, 0, USER_AGENT, 0, 10)) {
                    return USER_AGENT;
                }
                break;
        }
        return HeaderUtils.normalizeName(name);
    }

    private static String matchBucket11(String name) {
        if (name.regionMatches(true, 0, RETRY_AFTER, 0, 11)) {
            return RETRY_AFTER;
        }
        return HeaderUtils.normalizeName(name);
    }

    private static String matchBucket12(String name) {
        switch (name.charAt(0) | 0x20) {
            case 'c': // [c]ontent-type
                if (name.regionMatches(true, 0, CONTENT_TYPE, 0, 12)) {
                    return CONTENT_TYPE;
                }
                break;
            case 'm': // [m]ax-forwards
                if (name.regionMatches(true, 0, MAX_FORWARDS, 0, 12)) {
                    return MAX_FORWARDS;
                }
                break;
        }
        return HeaderUtils.normalizeName(name);
    }

    private static String matchBucket13(String name) {
        switch (name.charAt(6) | 0x20) {
            case '-': // accept[-]ranges
                if (name.regionMatches(true, 0, ACCEPT_RANGES, 0, 13)) {
                    return ACCEPT_RANGES;
                }
                break;
            case 'c': // cache-[c]ontrol
                if (name.regionMatches(true, 0, CACHE_CONTROL, 0, 13)) {
                    return CACHE_CONTROL;
                }
                break;
            case 'e': // if-non[e]-match
                if (name.regionMatches(true, 0, IF_NONE_MATCH, 0, 13)) {
                    return IF_NONE_MATCH;
                }
                break;
            case 'i': // author[i]zation
                if (name.regionMatches(true, 0, AUTHORIZATION, 0, 13)) {
                    return AUTHORIZATION;
                }
                break;
            case 'o': // last-m[o]dified
                if (name.regionMatches(true, 0, LAST_MODIFIED, 0, 13)) {
                    return LAST_MODIFIED;
                }
                break;
            case 't': // conten[t]-range
                if (name.regionMatches(true, 0, CONTENT_RANGE, 0, 13)) {
                    return CONTENT_RANGE;
                }
                break;
        }
        return HeaderUtils.normalizeName(name);
    }

    private static String matchBucket14(String name) {
        switch (name.charAt(0) | 0x20) {
            case 'c': // [c]ontent-length
                if (name.regionMatches(true, 0, CONTENT_LENGTH, 0, 14)) {
                    return CONTENT_LENGTH;
                }
                break;
            case 'a': // [a]ccept-charset
                if (name.regionMatches(true, 0, ACCEPT_CHARSET, 0, 14)) {
                    return ACCEPT_CHARSET;
                }
                break;
        }
        return HeaderUtils.normalizeName(name);
    }

    private static String matchBucket15(String name) {
        switch (name.charAt(7) | 0x20) {
            case 'e': // accept-[e]ncoding
                if (name.regionMatches(true, 0, ACCEPT_ENCODING, 0, 15)) {
                    return ACCEPT_ENCODING;
                }
                break;
            case 'l': // accept-[l]anguage
                if (name.regionMatches(true, 0, ACCEPT_LANGUAGE, 0, 15)) {
                    return ACCEPT_LANGUAGE;
                }
                break;
            case 'r': // amz-sdk-[r]equest
                if (name.regionMatches(true, 0, AMZ_SDK_REQUEST, 0, 15)) {
                    return AMZ_SDK_REQUEST;
                }
                break;
            case 't': // x-amzn-[t]race-id
                if (name.regionMatches(true, 0, X_AMZN_TRACE_ID, 0, 15)) {
                    return X_AMZN_TRACE_ID;
                }
                break;
        }
        return HeaderUtils.normalizeName(name);
    }

    private static String matchBucket16(String name) {
        switch (name.charAt(11) | 0x20) {
            case 'a': // content-loc[a]tion
                if (name.regionMatches(true, 0, CONTENT_LOCATION, 0, 16)) {
                    return CONTENT_LOCATION;
                }
                break;
            case 'c': // proxy-conne[c]tion
                if (name.regionMatches(true, 0, PROXY_CONNECTION, 0, 16)) {
                    return PROXY_CONNECTION;
                }
                break;
            case 'e': // x-amzn-requ[e]stid
                if (name.regionMatches(true, 0, X_AMZN_REQUESTID, 0, 16)) {
                    return X_AMZN_REQUESTID;
                }
                break;
            case 'g': // content-lan[g]uage
                if (name.regionMatches(true, 0, CONTENT_LANGUAGE, 0, 16)) {
                    return CONTENT_LANGUAGE;
                }
                break;
            case 'i': // www-authent[i]cate
                if (name.regionMatches(true, 0, WWW_AUTHENTICATE, 0, 16)) {
                    return WWW_AUTHENTICATE;
                }
                break;
            case 'o': // content-enc[o]ding
                if (name.regionMatches(true, 0, CONTENT_ENCODING, 0, 16)) {
                    return CONTENT_ENCODING;
                }
                break;
            case 's': // x-amz-reque[s]t-id
                if (name.regionMatches(true, 0, X_AMZ_REQUEST_ID, 0, 16)) {
                    return X_AMZ_REQUEST_ID;
                }
                break;
        }
        return HeaderUtils.normalizeName(name);
    }

    private static String matchBucket17(String name) {
        switch (name.charAt(0) | 0x20) {
            case 'i': // [i]f-modified-since
                if (name.regionMatches(true, 0, IF_MODIFIED_SINCE, 0, 17)) {
                    return IF_MODIFIED_SINCE;
                }
                break;
            case 't': // [t]ransfer-encoding
                if (name.regionMatches(true, 0, TRANSFER_ENCODING, 0, 17)) {
                    return TRANSFER_ENCODING;
                }
                break;
        }
        return HeaderUtils.normalizeName(name);
    }

    private static String matchBucket18(String name) {
        if (name.regionMatches(true, 0, PROXY_AUTHENTICATE, 0, 18)) {
            return PROXY_AUTHENTICATE;
        }
        return HeaderUtils.normalizeName(name);
    }

    private static String matchBucket19(String name) {
        switch (name.charAt(0) | 0x20) {
            case 'c': // [c]ontent-disposition
                if (name.regionMatches(true, 0, CONTENT_DISPOSITION, 0, 19)) {
                    return CONTENT_DISPOSITION;
                }
                break;
            case 'i': // [i]f-unmodified-since
                if (name.regionMatches(true, 0, IF_UNMODIFIED_SINCE, 0, 19)) {
                    return IF_UNMODIFIED_SINCE;
                }
                break;
            case 'p': // [p]roxy-authorization
                if (name.regionMatches(true, 0, PROXY_AUTHORIZATION, 0, 19)) {
                    return PROXY_AUTHORIZATION;
                }
                break;
        }
        return HeaderUtils.normalizeName(name);
    }

    private static String matchBucket25(String name) {
        if (name.regionMatches(true, 0, STRICT_TRANSPORT_SECURITY, 0, 25)) {
            return STRICT_TRANSPORT_SECURITY;
        }
        return HeaderUtils.normalizeName(name);
    }

    private static String matchBucket27(String name) {
        if (name.regionMatches(true, 0, ACCESS_CONTROL_ALLOW_ORIGIN, 0, 27)) {
            return ACCESS_CONTROL_ALLOW_ORIGIN;
        }
        return HeaderUtils.normalizeName(name);
    }
}
