/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.Mac;

/**
 * Precomputed resources needed for signing that can be pooled for resource reuse.
 */
final class SigningResources {
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final int BUFFER_SIZE = 512;
    private static final int POOL_SIZE = 32;
    private static final int INITIAL_HEADER_CAPACITY = 16;

    static final Pool<SigningResources> RESOURCES_POOL = new Pool<>(POOL_SIZE, SigningResources::new);

    final StringBuilder sb;
    final MessageDigest sha256Digest;
    final Mac sha256Mac;

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

    /**
     * Reusable byte buffer for the canonical request. The canonical request is ASCII (header
     * names + percent-encoded values + hex digests + RFC 3339 timestamps), so we can write
     * StringBuilder chars directly as bytes without going through {@code String.getBytes}.
     */
    byte[] canonicalRequestBytes;
    byte[] stringToSignBytes;
    final byte[] hashBytes;
    final byte[] signatureBytes;
    final byte[] signatureHexBytes;

    /**
     * Ensure {@link #canonicalRequestBytes} has at least {@code minLength} bytes of capacity,
     * growing to the next power of two if not. Returns the (possibly-replaced) backing array.
     */
    byte[] ensureCanonicalRequestCapacity(int minLength) {
        if (canonicalRequestBytes.length < minLength) {
            int newLen = Integer.highestOneBit(minLength - 1) << 1;
            canonicalRequestBytes = new byte[newLen];
        }
        return canonicalRequestBytes;
    }

    /**
     * Ensure {@link #stringToSignBytes} has at least {@code minLength} bytes of capacity,
     * growing to the next power of two if not.
     */
    byte[] ensureStringToSignCapacity(int minLength) {
        if (stringToSignBytes.length < minLength) {
            int newLen = Integer.highestOneBit(minLength - 1) << 1;
            stringToSignBytes = new byte[newLen];
        }
        return stringToSignBytes;
    }

    SigningResources() {
        this.sb = new StringBuilder(BUFFER_SIZE);
        this.headers = new String[INITIAL_HEADER_CAPACITY * 2];
        this.queryPairs = new String[INITIAL_HEADER_CAPACITY * 2];
        this.canonicalRequestBytes = new byte[BUFFER_SIZE];
        this.stringToSignBytes = new byte[BUFFER_SIZE];
        this.hashBytes = new byte[32];
        this.signatureBytes = new byte[32];
        this.signatureHexBytes = new byte[64];

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

    /**
     * Shrink the string builder to a max size of 512 to keep it always at or below this
     * when returned to the pool.
     */
    void shrink() {
        if (sb.length() > BUFFER_SIZE) {
            sb.setLength(BUFFER_SIZE);
            sb.trimToSize();
        }
        sb.setLength(0);

        clearHeaderRefs();
        if (headers.length > INITIAL_HEADER_CAPACITY * 8) {
            // Reallocate in case the header array grew too large
            headers = new String[INITIAL_HEADER_CAPACITY * 2];
        }
        if (stringToSignBytes.length > BUFFER_SIZE) {
            stringToSignBytes = new byte[BUFFER_SIZE];
        }
    }

    private void clearHeaderRefs() {
        Arrays.fill(headers, 0, headerCount * 2, null);
        headerCount = 0;
        Arrays.fill(queryPairs, 0, queryPairCount * 2, null);
        queryPairCount = 0;
    }

    void reset() {
        sb.setLength(0);
        sha256Digest.reset();
        sha256Mac.reset();
        clearHeaderRefs();
    }

    /**
     * Append a header entry. Resizes the strided array (doubling) if there's no room. The name is defensively
     * lowercased if it contains any uppercase ASCII.
     */
    void addHeader(String name, String value) {
        int slot = headerCount * 2;
        if (slot >= headers.length) {
            String[] grown = new String[headers.length * 2];
            System.arraycopy(headers, 0, grown, 0, slot);
            headers = grown;
        }

        headers[slot] = lowercaseIfNeeded(name);
        headers[slot + 1] = value;
        headerCount++;
    }

    private static String lowercaseIfNeeded(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                return name.toLowerCase(Locale.ROOT);
            }
        }
        return name;
    }

    /**
     * In-place insertion sort of the strided array by header name.
     */
    void sortHeadersByName() {
        for (int i = 1; i < headerCount; i++) {
            int srcSlot = i * 2;
            String name = headers[srcSlot];
            String value = headers[srcSlot + 1];

            int j = i - 1;
            while (j >= 0 && headers[j * 2].compareTo(name) > 0) {
                headers[(j + 1) * 2] = headers[j * 2];
                headers[(j + 1) * 2 + 1] = headers[j * 2 + 1];
                j--;
            }

            headers[(j + 1) * 2] = name;
            headers[(j + 1) * 2 + 1] = value;
        }
    }

    /**
     * Append a canonical query-string pair (already encoded). Resizes the strided array
     * if needed.
     */
    void addQueryPair(String encodedKey, String encodedValue) {
        int slot = queryPairCount * 2;
        if (slot >= queryPairs.length) {
            String[] grown = new String[queryPairs.length * 2];
            System.arraycopy(queryPairs, 0, grown, 0, slot);
            queryPairs = grown;
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

    /**
     * Returns a signing resource instance from the internal pool if available or a creates
     * a new instance if there's none available in the pool.
     *
     * @return a singing resources instance
     */
    static SigningResources get() {
        return RESOURCES_POOL.get();
    }

    /**
     * Returns the signing resource to the pool.
     *
     * @param signingResources the signing resource to release to the internal pool.
     */
    static void release(SigningResources signingResources) {
        signingResources.shrink();
        RESOURCES_POOL.release(signingResources);
    }
}
