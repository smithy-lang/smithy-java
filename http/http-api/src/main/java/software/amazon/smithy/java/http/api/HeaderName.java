/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical HTTP header name constants with fast case-insensitive lookup.
 *
 * <p>Use this class and constants as a lookup key when adding or querying headers since it skips the
 * need for canonicalization.
 */
public final class HeaderName {

    private static int longestBuiltinHeaderName = 0;
    private static final Map<Integer, List<HeaderName>> builtinBuckets = new HashMap<>();

    // === HTTP/2 Pseudo-Headers ===
    public static final HeaderName PSEUDO_AUTHORITY = HeaderName.builtin(":authority");
    public static final HeaderName PSEUDO_METHOD = HeaderName.builtin(":method");
    public static final HeaderName PSEUDO_PATH = HeaderName.builtin(":path");
    public static final HeaderName PSEUDO_SCHEME = HeaderName.builtin(":scheme");
    public static final HeaderName PSEUDO_STATUS = HeaderName.builtin(":status");

    // === Standard Headers (alphabetical) ===
    public static final HeaderName ACCEPT = HeaderName.builtin("accept");
    public static final HeaderName ACCEPT_CHARSET = HeaderName.builtin("accept-charset");
    public static final HeaderName ACCEPT_ENCODING = HeaderName.builtin("accept-encoding");
    public static final HeaderName ACCEPT_LANGUAGE = HeaderName.builtin("accept-language");
    public static final HeaderName ACCEPT_RANGES = HeaderName.builtin("accept-ranges");
    public static final HeaderName ACCESS_CONTROL_ALLOW_ORIGIN = HeaderName.builtin("access-control-allow-origin");
    public static final HeaderName AGE = HeaderName.builtin("age");
    public static final HeaderName ALLOW = HeaderName.builtin("allow");
    public static final HeaderName AUTHORIZATION = HeaderName.builtin("authorization");
    public static final HeaderName CACHE_CONTROL = HeaderName.builtin("cache-control");
    public static final HeaderName CONNECTION = HeaderName.builtin("connection");
    public static final HeaderName CONTENT_DISPOSITION = HeaderName.builtin("content-disposition");
    public static final HeaderName CONTENT_ENCODING = HeaderName.builtin("content-encoding");
    public static final HeaderName CONTENT_LANGUAGE = HeaderName.builtin("content-language");
    public static final HeaderName CONTENT_LENGTH = HeaderName.builtin("content-length");
    public static final HeaderName CONTENT_LOCATION = HeaderName.builtin("content-location");
    public static final HeaderName CONTENT_MD5 = HeaderName.builtin("content-md5");
    public static final HeaderName CONTENT_RANGE = HeaderName.builtin("content-range");
    public static final HeaderName CONTENT_TYPE = HeaderName.builtin("content-type");
    public static final HeaderName COOKIE = HeaderName.builtin("cookie");
    public static final HeaderName DATE = HeaderName.builtin("date");
    public static final HeaderName ETAG = HeaderName.builtin("etag");
    public static final HeaderName EXPECT = HeaderName.builtin("expect");
    public static final HeaderName EXPIRES = HeaderName.builtin("expires");
    public static final HeaderName FROM = HeaderName.builtin("from");
    public static final HeaderName HOST = HeaderName.builtin("host");
    public static final HeaderName IF_MATCH = HeaderName.builtin("if-match");
    public static final HeaderName IF_MODIFIED_SINCE = HeaderName.builtin("if-modified-since");
    public static final HeaderName IF_NONE_MATCH = HeaderName.builtin("if-none-match");
    public static final HeaderName IF_RANGE = HeaderName.builtin("if-range");
    public static final HeaderName IF_UNMODIFIED_SINCE = HeaderName.builtin("if-unmodified-since");
    public static final HeaderName KEEP_ALIVE = HeaderName.builtin("keep-alive");
    public static final HeaderName LAST_MODIFIED = HeaderName.builtin("last-modified");
    public static final HeaderName LINK = HeaderName.builtin("link");
    public static final HeaderName LOCATION = HeaderName.builtin("location");
    public static final HeaderName MAX_FORWARDS = HeaderName.builtin("max-forwards");
    public static final HeaderName ORIGIN = HeaderName.builtin("origin");
    public static final HeaderName PROXY_AUTHENTICATE = HeaderName.builtin("proxy-authenticate");
    public static final HeaderName PROXY_AUTHORIZATION = HeaderName.builtin("proxy-authorization");
    public static final HeaderName PROXY_CONNECTION = HeaderName.builtin("proxy-connection");
    public static final HeaderName RANGE = HeaderName.builtin("range");
    public static final HeaderName REFERER = HeaderName.builtin("referer");
    public static final HeaderName REFRESH = HeaderName.builtin("refresh");
    public static final HeaderName RETRY_AFTER = HeaderName.builtin("retry-after");
    public static final HeaderName SERVER = HeaderName.builtin("server");
    public static final HeaderName SET_COOKIE = HeaderName.builtin("set-cookie");
    public static final HeaderName SMITHY_PROTOCOL = HeaderName.builtin("smithy-protocol");
    public static final HeaderName STRICT_TRANSPORT_SECURITY = HeaderName.builtin("strict-transport-security");
    public static final HeaderName TRAILER = HeaderName.builtin("trailer");
    public static final HeaderName TRANSFER_ENCODING = HeaderName.builtin("transfer-encoding");
    public static final HeaderName UPGRADE = HeaderName.builtin("upgrade");
    public static final HeaderName USER_AGENT = HeaderName.builtin("user-agent");
    public static final HeaderName VARY = HeaderName.builtin("vary");
    public static final HeaderName VIA = HeaderName.builtin("via");
    public static final HeaderName WWW_AUTHENTICATE = HeaderName.builtin("www-authenticate");

    // === Amazon-specific Headers ===
    public static final HeaderName AMZ_SDK_REQUEST = HeaderName.builtin("amz-sdk-request");
    public static final HeaderName X_AMZN_REQUESTID = HeaderName.builtin("x-amzn-requestid");
    public static final HeaderName X_AMZN_TRACE_ID = HeaderName.builtin("x-amzn-trace-id");
    public static final HeaderName X_AMZ_DATE = HeaderName.builtin("x-amz-date");
    public static final HeaderName X_AMZ_REQUEST_ID = HeaderName.builtin("x-amz-request-id");
    public static final HeaderName X_AMZ_SECURITY_TOKEN = HeaderName.builtin("x-amz-security-token");
    public static final HeaderName X_AMZ_TARGET = HeaderName.builtin("x-amz-target");

    // Direct-indexed by header name length. GROUPS[len] is the candidate array for that length, or null.
    private static final String[][] GROUPS;

    static {
        // Make groups dynamically based on added header names above.
        GROUPS = new String[longestBuiltinHeaderName + 1][];

        // Now iterate over each bucket by length and setup the groups.
        for (var entry : builtinBuckets.entrySet()) {
            var len = entry.getKey();
            var names = entry.getValue();
            GROUPS[len] = new String[names.size()];
            for (var i = 0; i < names.size(); i++) {
                GROUPS[len][i] = names.get(i).name;
            }
        }

        builtinBuckets.clear();
    }

    private final String name;

    private HeaderName(String name) {
        // Expects already lowercase from the static methods.
        // Constants defined in this file can use this directly.
        this.name = name;
    }

    public static HeaderName of(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                throw new IllegalArgumentException("Canonical header names must be lowercase: " + name);
            }
        }

        return new HeaderName(name);
    }

    private static HeaderName builtin(String name) {
        var len = name.length();
        if (len > longestBuiltinHeaderName) {
            longestBuiltinHeaderName = len;
        }
        var result = new HeaderName(name);
        builtinBuckets.computeIfAbsent(len, l -> new ArrayList<>()).add(result);
        return result;
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        } else if (o == this) {
            return true;
        } else {
            HeaderName that = (HeaderName) o;
            return name.equals(that.name);
        }
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
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
        var length = name.length();
        if (length < GROUPS.length) {
            String[] candidates = GROUPS[length];
            if (candidates != null) {
                for (String candidate : candidates) {
                    if (name == candidate || name.regionMatches(true, 0, candidate, 0, length)) {
                        return candidate;
                    }
                }
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
}
