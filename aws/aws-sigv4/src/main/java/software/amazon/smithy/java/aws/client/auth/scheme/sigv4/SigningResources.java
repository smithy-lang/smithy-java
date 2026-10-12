/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.DigestException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;
import software.amazon.smithy.java.codecs.commons.CompactStringAccess;
import software.amazon.smithy.java.codecs.commons.StripedPool;

/**
 * Canonical requests use ASCII except for header values, which use UTF-8.
 */
final class SigningResources {
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final int BUFFER_SIZE = 512;
    private static final int INITIAL_HEADER_CAPACITY = 16;
    private static final long ONES = 0x0101010101010101L;
    private static final long HIGH_BITS = 0x8080808080808080L;
    private static final VarHandle LONGS = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle INTS = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

    private static final byte[] HEX2 = new byte[512];

    static {
        byte[] digits = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        for (int v = 0; v < 256; v++) {
            HEX2[v << 1] = digits[v >>> 4];
            HEX2[(v << 1) + 1] = digits[v & 0x0F];
        }
    }

    private static final StripedPool<SigningResources, Void> POOL = new ResourcesPool();

    final StringBuilder sb;
    private static final int SCRATCH_SIZE = 8192;
    final MessageDigest sha256Digest;
    final Mac sha256Mac;
    private byte[] hmacKey;
    private MessageDigest innerPad;
    private MessageDigest outerPad;
    private boolean digestCloneSupported = true;
    private final byte[] innerHash = new byte[32];
    private byte[] macKey;

    /**
     * Reusable flat strided header buffer: even indices hold name, odd indices hold value.
     * Sorted in-place by name on each signing call.
     */
    String[] headers;
    int headerCount;

    /**
     * Reusable buffer of canonical-query-param pairs: even indices hold encoded key,
     * odd indices hold encoded value. Cleared and refilled on each signing call.
     */
    String[] queryPairs;
    int queryPairCount;

    byte[] out;
    int outLength;
    byte[] stringToSignBytes;
    byte[] authorizationBytes;
    byte[] signedHeadersBytes;
    int signedHeadersLength;
    final byte[] hashBytes;
    final byte[] signatureBytes;
    final byte[] signatureHexBytes;
    private byte[] scratch;
    final byte[] payloadHashHex;
    private final byte[] timestampBytes;

    private long cachedSecond = Long.MIN_VALUE;
    String dateStamp;
    String requestTime;
    long cachedDays;

    private String keySecret;
    private String keyRegion;
    private String keyService;
    private long keyDays = Long.MIN_VALUE;
    private byte[] signingKey;

    private String[] memoNames = new String[0];
    private int[] memoOrder = new int[0];
    private String[] sortScratch = new String[INITIAL_HEADER_CAPACITY * 2];

    SigningResources() {
        this.sb = new StringBuilder(BUFFER_SIZE);
        this.headers = new String[INITIAL_HEADER_CAPACITY * 2];
        this.queryPairs = new String[INITIAL_HEADER_CAPACITY * 2];
        this.out = new byte[BUFFER_SIZE];
        this.stringToSignBytes = new byte[256];
        this.authorizationBytes = new byte[BUFFER_SIZE];
        this.signedHeadersBytes = new byte[256];
        this.hashBytes = new byte[32];
        this.signatureBytes = new byte[32];
        this.signatureHexBytes = new byte[64];
        this.payloadHashHex = new byte[64];
        this.timestampBytes = new byte[16];

        try {
            this.sha256Digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unable to fetch message digest instance for SHA-256", e);
        }
        try {
            this.sha256Mac = Mac.getInstance(HMAC_SHA_256);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unable to fetch Mac instance for HmacSHA256", e);
        }
    }

    static SigningResources get() {
        return POOL.acquire(null);
    }

    static void release(SigningResources signingResources) {
        POOL.release(signingResources);
    }

    private static final class ResourcesPool extends StripedPool<SigningResources, Void> {
        @Override
        protected SigningResources create(Void context) {
            return new SigningResources();
        }

        @Override
        protected boolean canPool(SigningResources item) {
            return true;
        }

        @Override
        protected void prepareForPool(SigningResources item) {
            item.shrink();
        }

        @Override
        protected boolean reset(SigningResources item, Void context) {
            item.reset();
            return true;
        }
    }

    void reset() {
        headerCount = 0;
        queryPairCount = 0;
        outLength = 0;
        signedHeadersLength = 0;
        sb.setLength(0);
    }

    void shrink() {
        if (sb.length() > BUFFER_SIZE) {
            sb.setLength(BUFFER_SIZE);
            sb.trimToSize();
        }
        sb.setLength(0);
        Arrays.fill(headers, 0, headerCount * 2, null);
        Arrays.fill(sortScratch, 0, Math.min(sortScratch.length, headerCount * 2), null);
        headerCount = 0;
        Arrays.fill(queryPairs, 0, queryPairCount * 2, null);
        queryPairCount = 0;
        if (headers.length > INITIAL_HEADER_CAPACITY * 8) {
            headers = new String[INITIAL_HEADER_CAPACITY * 2];
        }
        if (out.length > BUFFER_SIZE * 8) {
            out = new byte[BUFFER_SIZE];
        }
        if (authorizationBytes.length > BUFFER_SIZE * 8) {
            authorizationBytes = new byte[BUFFER_SIZE];
        }
        if (stringToSignBytes.length > BUFFER_SIZE) {
            stringToSignBytes = new byte[256];
        }
    }

    byte[] hmac(byte[] key, byte[] data, int offset, int length, byte[] output) {
        if (digestCloneSupported) {
            try {
                if (hmacKey != key) {
                    prepareHmacPads(key);
                }
                MessageDigest inner = (MessageDigest) innerPad.clone();
                inner.update(data, offset, length);
                inner.digest(innerHash, 0, 32);
                MessageDigest outer = (MessageDigest) outerPad.clone();
                outer.update(innerHash, 0, 32);
                outer.digest(output, 0, 32);
                return output;
            } catch (CloneNotSupportedException e) {
                digestCloneSupported = false;
            } catch (DigestException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            Mac mac = keyedMac(key);
            mac.update(data, offset, length);
            mac.doFinal(output, 0);
            return output;
        } catch (ShortBufferException | RuntimeException e) {
            macKey = null; // Reinitialize the Mac after a failure that may leave a partial update.
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }

    byte[] hmac(byte[] key, byte[] data) {
        return hmac(key, data, 0, data.length, new byte[32]);
    }

    private void prepareHmacPads(byte[] key) throws CloneNotSupportedException {
        byte[] k = key.length > 64 ? hash(key) : key;
        byte[] ipad = new byte[64];
        byte[] opad = new byte[64];
        for (int i = 0; i < 64; i++) {
            byte b = i < k.length ? k[i] : 0;
            ipad[i] = (byte) (b ^ 0x36);
            opad[i] = (byte) (b ^ 0x5c);
        }
        sha256Digest.reset();
        MessageDigest inner = (MessageDigest) sha256Digest.clone();
        inner.update(ipad);
        MessageDigest outer = (MessageDigest) sha256Digest.clone();
        outer.update(opad);
        innerPad = inner;
        outerPad = outer;
        hmacKey = key;
    }

    private Mac keyedMac(byte[] key) {
        if (macKey != key) {
            try {
                sha256Mac.init(new SecretKeySpec(key, HMAC_SHA_256));
            } catch (InvalidKeyException e) {
                macKey = null;
                throw new RuntimeException("Invalid signing key", e);
            }
            macKey = key;
        }
        return sha256Mac;
    }

    byte[] hash(byte[] data, int offset, int length, byte[] output) {
        try {
            sha256Digest.update(data, offset, length);
            sha256Digest.digest(output, 0, output.length);
            return output;
        } catch (DigestException | RuntimeException e) {
            sha256Digest.reset();
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }

    /** Hashes the remaining bytes without changing the buffer position. */
    byte[] hash(ByteBuffer data, byte[] output) {
        try {
            updateFromBuffer(data.duplicate());
            sha256Digest.digest(output, 0, output.length);
            return output;
        } catch (DigestException | RuntimeException e) {
            sha256Digest.reset();
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }

    /**
     * Copy non-array buffers through scratch to avoid retained JDK buffers that each digest clone would copy.
     * This method consumes the buffer.
     */
    private void updateFromBuffer(ByteBuffer data) {
        if (data.hasArray()) {
            sha256Digest.update(data.array(), data.arrayOffset() + data.position(), data.remaining());
            data.position(data.limit());
            return;
        }
        byte[] tmp = scratch;
        if (tmp == null) {
            tmp = scratch = new byte[SCRATCH_SIZE];
        }
        while (data.hasRemaining()) {
            int n = Math.min(data.remaining(), tmp.length);
            data.get(tmp, 0, n);
            sha256Digest.update(tmp, 0, n);
        }
    }

    byte[] hash(byte[] data) {
        try {
            sha256Digest.update(data);
            return sha256Digest.digest();
        } catch (RuntimeException e) {
            sha256Digest.reset();
            throw e;
        }
    }

    void timestamp(long epochSecond) {
        if (epochSecond == cachedSecond) {
            return;
        }
        long days = Math.floorDiv(epochSecond, 86_400L);
        int secondOfDay = (int) (epochSecond - days * 86_400L);
        // Civil date from days since 1970-01-01 (Howard Hinnant's algorithm).
        long z = days + 719_468L;
        long era = Math.floorDiv(z, 146_097L);
        long doe = z - era * 146_097L;
        long yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365;
        long year = yoe + era * 400;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        int day = (int) (doy - (153 * mp + 2) / 5 + 1);
        int month = (int) (mp < 10 ? mp + 3 : mp - 9);
        if (month <= 2) {
            year++;
        }
        byte[] b = timestampBytes;
        int y = (int) year;
        b[0] = (byte) ('0' + y / 1000);
        b[1] = (byte) ('0' + (y / 100) % 10);
        b[2] = (byte) ('0' + (y / 10) % 10);
        b[3] = (byte) ('0' + y % 10);
        twoDigits(b, 4, month);
        twoDigits(b, 6, day);
        b[8] = 'T';
        twoDigits(b, 9, secondOfDay / 3600);
        twoDigits(b, 11, (secondOfDay / 60) % 60);
        twoDigits(b, 13, secondOfDay % 60);
        b[15] = 'Z';
        requestTime = new String(b, 0, 16, StandardCharsets.ISO_8859_1);
        dateStamp = requestTime.substring(0, 8);
        cachedDays = days;
        cachedSecond = epochSecond;
    }

    private static void twoDigits(byte[] b, int at, int value) {
        b[at] = (byte) ('0' + value / 10);
        b[at + 1] = (byte) ('0' + value % 10);
    }

    byte[] memoizedSigningKey(String secretKey, String regionName, String serviceName) {
        if (signingKey != null
                && keyDays == cachedDays
                && keySecret.equals(secretKey)
                && keyRegion.equals(regionName)
                && keyService.equals(serviceName)) {
            return signingKey;
        }
        return null;
    }

    void memoizeSigningKey(String secretKey, String regionName, String serviceName, byte[] key) {
        this.keySecret = secretKey;
        this.keyRegion = regionName;
        this.keyService = serviceName;
        this.keyDays = cachedDays;
        this.signingKey = key;
    }

    void addHeaderForSigning(String name, String value) {
        String canonical = lowercaseIfNeeded(name);
        if (!isExcludedFromSigning(canonical)) {
            addHeaderCanonical(canonical, value);
        }
    }

    /**
     * The signer supplies its own date and authorization. Proxies and SDKs can rewrite the other excluded headers.
     */
    static boolean isExcludedFromSigning(String lowercaseName) {
        return switch (lowercaseName) {
            case "x-amz-date", "authorization", "connection", "content-length", "transfer-encoding", "x-amzn-trace-id",
                    "user-agent", "expect" ->
                true;
            default -> false;
        };
    }

    /**
     * Append a header entry whose name is already canonical lowercase.
     */
    void addHeaderCanonical(String name, String value) {
        int slot = headerCount * 2;
        if (slot >= headers.length) {
            headers = Arrays.copyOf(headers, headers.length * 2);
        }
        headers[slot] = name;
        headers[slot + 1] = value;
        headerCount++;
    }

    private static String lowercaseIfNeeded(String name) {
        byte[] latin1 = latin1Bytes(name);
        if (latin1 == null) {
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (c >= 'A' && c <= 'Z') {
                    return name.toLowerCase(Locale.ROOT);
                }
            }
            return name;
        }
        int n = latin1.length;
        int i = 0;
        for (; i + 8 <= n; i += 8) {
            if (hasUppercaseAscii((long) LONGS.get(latin1, i))) {
                return name.toLowerCase(Locale.ROOT);
            }
        }
        for (; i < n; i++) {
            byte b = latin1[i];
            if (b >= 'A' && b <= 'Z') {
                return name.toLowerCase(Locale.ROOT);
            }
        }
        return name;
    }

    /**
     * Each addition sets a comparison flag in the high bit without carrying into another byte.
     * {@code ~x} excludes non-ASCII bytes from the result.
     */
    static boolean hasUppercaseAscii(long x) {
        long low7 = x & (ONES * 0x7F);
        long atLeastA = low7 + ONES * (0x80 - 'A');
        long aboveZ = low7 + ONES * (0x80 - ('Z' + 1));
        return (atLeastA & ~aboveZ & ~x & HIGH_BITS) != 0L;
    }

    /**
     * Preserve insertion order for repeated names so their values retain their order in the canonical request.
     */
    void sortHeadersByName() {
        int n = headerCount;
        if (n < 2) {
            return;
        }
        String[] names = memoNames;
        if (names.length == n && sameNames(names, n)) {
            applyOrder(memoOrder, n);
            return;
        }
        sortAndMemoize(n);
    }

    private boolean sameNames(String[] names, int n) {
        String[] h = headers;
        for (int i = 0; i < n; i++) {
            if (h[i * 2] != names[i]) {
                return false;
            }
        }
        return true;
    }

    private void sortAndMemoize(int n) {
        String[] h = headers;
        int[] order = memoOrder.length == n ? memoOrder : new int[n];
        for (int i = 0; i < n; i++) {
            int j = i - 1;
            while (j >= 0 && h[order[j] * 2].compareTo(h[i * 2]) > 0) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = i;
        }
        String[] names = memoNames.length == n ? memoNames : new String[n];
        for (int i = 0; i < n; i++) {
            names[i] = h[i * 2];
        }
        memoNames = names;
        memoOrder = order;
        applyOrder(order, n);
    }

    private void applyOrder(int[] order, int n) {
        String[] src = headers;
        String[] dst = sortScratch.length >= src.length ? sortScratch : new String[src.length];
        for (int k = 0; k < n; k++) {
            int from = order[k] * 2;
            dst[k * 2] = src[from];
            dst[k * 2 + 1] = src[from + 1];
        }
        headers = dst;
        sortScratch = src;
    }

    /**
     * Append a canonical query-string pair (already encoded). Resizes the strided array
     * if needed.
     */
    void addQueryPair(String encodedKey, String encodedValue) {
        int slot = queryPairCount * 2;
        if (slot >= queryPairs.length) {
            queryPairs = Arrays.copyOf(queryPairs, queryPairs.length * 2);
        }
        queryPairs[slot] = encodedKey;
        queryPairs[slot + 1] = encodedValue;
        queryPairCount++;
    }

    /**
     * In-place insertion sort of the query pairs by encoded key, breaking ties by encoded value.
     */
    void sortQueryPairs() {
        for (int i = 1; i < queryPairCount; i++) {
            int srcSlot = i * 2;
            String key = queryPairs[srcSlot];
            String value = queryPairs[srcSlot + 1];

            int j = i - 1;
            while (j >= 0 && compareKeyValue(queryPairs[j * 2], queryPairs[j * 2 + 1], key, value) > 0) {
                queryPairs[(j + 1) * 2] = queryPairs[j * 2];
                queryPairs[(j + 1) * 2 + 1] = queryPairs[j * 2 + 1];
                j--;
            }

            queryPairs[(j + 1) * 2] = key;
            queryPairs[(j + 1) * 2 + 1] = value;
        }
    }

    private static int compareKeyValue(String aKey, String aValue, String bKey, String bValue) {
        int cmp = aKey.compareTo(bKey);
        return cmp != 0 ? cmp : aValue.compareTo(bValue);
    }

    void beginOutput() {
        outLength = 0;
    }

    private void ensureOut(int extra) {
        int needed = outLength + extra;
        if (needed > out.length) {
            out = Arrays.copyOf(out, Math.max(needed, out.length * 2));
        }
    }

    void put(byte b) {
        ensureOut(1);
        out[outLength++] = b;
    }

    void put(byte[] src, int offset, int length) {
        ensureOut(length);
        System.arraycopy(src, 0, out, outLength, length);
        outLength += length;
    }

    /** The input must contain only ASCII characters. */
    void putAscii(String s) {
        byte[] latin1 = latin1Bytes(s);
        if (latin1 != null) {
            put(latin1, 0, latin1.length);
            return;
        }
        int len = s.length();
        ensureOut(len);
        for (int i = 0; i < len; i++) {
            out[outLength++] = (byte) s.charAt(i);
        }
    }

    /** The input must contain only ASCII characters. */
    void putAscii(StringBuilder s) {
        int len = s.length();
        ensureOut(len);
        for (int i = 0; i < len; i++) {
            out[outLength++] = (byte) s.charAt(i);
        }
    }

    void putHeaderValue(String value) {
        byte[] latin1 = latin1Bytes(value);
        if (latin1 == null) {
            putHeaderValueChars(value);
            return;
        }
        int n = latin1.length;
        int i = 0;
        for (; i + 8 <= n; i += 8) {
            long x = (long) LONGS.get(latin1, i);
            // Detect bytes below 0x21 or at least 0x80. The slow path checks each byte for whitespace and UTF-8 encoding.
            if (((((x - 0x2121212121212121L) & ~x) | x) & HIGH_BITS) != 0L) {
                putHeaderValueSlow(latin1);
                return;
            }
        }
        for (; i < n; i++) {
            int c = latin1[i] & 0xFF;
            if (c < 0x21 || c >= 0x80) {
                putHeaderValueSlow(latin1);
                return;
            }
        }
        put(latin1, 0, n);
    }

    private void putHeaderValueSlow(byte[] latin1) {
        int start = 0;
        int end = latin1.length;
        while (start < end && isWhiteSpace(latin1[start] & 0xFF)) {
            start++;
        }
        while (end > start && isWhiteSpace(latin1[end - 1] & 0xFF)) {
            end--;
        }
        ensureOut((end - start) * 2);
        boolean previousIsWhiteSpace = false;
        for (int i = start; i < end; i++) {
            int c = latin1[i] & 0xFF;
            if (isWhiteSpace(c)) {
                if (!previousIsWhiteSpace) {
                    out[outLength++] = ' ';
                    previousIsWhiteSpace = true;
                }
                continue;
            }
            previousIsWhiteSpace = false;
            if (c < 0x80) {
                out[outLength++] = (byte) c;
            } else {
                out[outLength++] = (byte) (0xC0 | (c >> 6));
                out[outLength++] = (byte) (0x80 | (c & 0x3F));
            }
        }
    }

    private void putHeaderValueChars(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isWhiteSpace(value.charAt(start))) {
            start++;
        }
        while (end > start && isWhiteSpace(value.charAt(end - 1))) {
            end--;
        }
        boolean previousIsWhiteSpace = false;
        for (int i = start; i < end; i++) {
            char c = value.charAt(i);
            if (isWhiteSpace(c)) {
                if (!previousIsWhiteSpace) {
                    put((byte) ' ');
                    previousIsWhiteSpace = true;
                }
                continue;
            }
            previousIsWhiteSpace = false;
            if (c < 0x80) {
                put((byte) c);
            } else if (c < 0x800) {
                put((byte) (0xC0 | (c >> 6)));
                put((byte) (0x80 | (c & 0x3F)));
            } else if (Character.isSurrogate(c)) {
                int cp = Character.codePointAt(value, i);
                if (Character.charCount(cp) == 2) {
                    i++;
                }
                put((byte) (0xF0 | (cp >> 18)));
                put((byte) (0x80 | ((cp >> 12) & 0x3F)));
                put((byte) (0x80 | ((cp >> 6) & 0x3F)));
                put((byte) (0x80 | (cp & 0x3F)));
            } else {
                put((byte) (0xE0 | (c >> 12)));
                put((byte) (0x80 | ((c >> 6) & 0x3F)));
                put((byte) (0x80 | (c & 0x3F)));
            }
        }
    }

    private static boolean isWhiteSpace(int c) {
        return c == ' ' || (c >= '\t' && c <= '\f');
    }

    /** The Latin-1 bytes backing a compact string, or null if the string is UTF-16 or access is unavailable. */
    static byte[] latin1Bytes(String s) {
        return CompactStringAccess.latin1Bytes(s);
    }

    /** The input must contain only ASCII characters. */
    static int writeAscii(String value, byte[] dst, int offset) {
        byte[] latin1 = latin1Bytes(value);
        if (latin1 != null) {
            System.arraycopy(latin1, 0, dst, offset, latin1.length);
            return offset + latin1.length;
        }
        for (int i = 0; i < value.length(); i++) {
            dst[offset++] = (byte) value.charAt(i);
        }
        return offset;
    }

    /**
     * Four input bytes occupy alternate byte positions before each byte splits into two hex digits.
     * The carry test adds 39 to convert digits 10 through 15 to {@code a} through {@code f}.
     */
    static int writeHex(byte[] bytes, byte[] dst, int offset) {
        int i = 0;
        for (; i + 4 <= bytes.length; i += 4) {
            long x = (int) INTS.get(bytes, i) & 0xFFFFFFFFL;
            x = (x | (x << 16)) & 0x0000FFFF0000FFFFL;
            x = (x | (x << 8)) & 0x00FF00FF00FF00FFL;
            long nibbles = ((x >>> 4) & 0x000F000F000F000FL) | ((x & 0x000F000F000F000FL) << 8);
            long ascii = nibbles + 0x3030303030303030L
                    + (((nibbles + 0x0606060606060606L) >>> 4) & 0x0101010101010101L) * 39L;
            LONGS.set(dst, offset, ascii);
            offset += 8;
        }
        for (; i < bytes.length; i++) {
            int v = (bytes[i] & 0xFF) << 1;
            dst[offset++] = HEX2[v];
            dst[offset++] = HEX2[v + 1];
        }
        return offset;
    }
}
