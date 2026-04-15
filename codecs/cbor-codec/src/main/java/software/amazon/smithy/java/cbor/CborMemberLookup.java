/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import software.amazon.smithy.java.core.schema.Schema;

/**
 * Hash-based field name lookup that operates directly on UTF-8 bytes during CBOR deserialization.
 *
 * <p>Strategy:
 * <ol>
 *   <li><b>Speculative fast path</b>: check the expected next member via length + Arrays.equals
 *       (JVM-intrinsified). Fires on every field when CBOR map entries arrive in schema definition
 *       order (the common case for smithy-to-smithy communication).
 *   <li><b>Slow path</b>: linear scan with FNV-1a hash pre-filter. Hash is computed lazily
 *       (only on speculative miss) to avoid the per-byte multiply+XOR cost on the hot path.
 * </ol>
 */
final class CborMemberLookup {

    // FNV-1a constants
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    // Members in definition order (for speculative matching)
    final long[] orderedHashes;
    final Schema[] orderedSchemas;
    final byte[][] orderedNameBytes;

    CborMemberLookup(List<Schema> members) {
        int size = members.size();
        this.orderedHashes = new long[size];
        this.orderedSchemas = new Schema[size];
        this.orderedNameBytes = new byte[size][];

        for (int i = 0; i < size; i++) {
            Schema m = members.get(i);
            // CBOR always uses member names (no jsonName equivalent)
            byte[] nameBytes = m.memberName().getBytes(StandardCharsets.UTF_8);
            orderedNameBytes[i] = nameBytes;
            orderedHashes[i] = fnvHash(nameBytes, 0, nameBytes.length);
            orderedSchemas[i] = m;
        }
    }

    /**
     * Looks up a member by matching the field name bytes directly from the CBOR payload buffer.
     * No String allocation on the common path.
     *
     * @param buf input buffer containing the field name bytes (raw UTF-8, no CBOR header)
     * @param start start offset in buf
     * @param end end offset in buf (exclusive)
     * @param expectedNext speculative next member index (-1 to disable)
     * @return matched Schema, or null if not found
     */
    Schema lookup(byte[] buf, int start, int end, int expectedNext) {
        int nameLen = end - start;

        // Speculative fast path: Arrays.equals only, no hash.
        if (expectedNext >= 0 && expectedNext < orderedNameBytes.length
                && orderedNameBytes[expectedNext].length == nameLen
                && Arrays.equals(buf, start, end, orderedNameBytes[expectedNext], 0, nameLen)) {
            return orderedSchemas[expectedNext];
        }

        // Slow path: compute hash lazily, then scan with hash + length + equals.
        long hash = fnvHash(buf, start, end);
        for (int i = 0; i < orderedHashes.length; i++) {
            if (orderedHashes[i] == hash
                    && orderedNameBytes[i].length == nameLen
                    && Arrays.equals(buf, start, end, orderedNameBytes[i], 0, nameLen)) {
                return orderedSchemas[i];
            }
        }

        return null;
    }

    private static long fnvHash(byte[] buf, int start, int end) {
        long hash = FNV_OFFSET;
        for (int i = start; i < end; i++) {
            hash ^= buf[i] & 0xFF;
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
