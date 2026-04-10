/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import software.amazon.smithy.java.core.schema.MemberLookup;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.TraitKey;

/**
 * FNV-1a hash-based field name lookup that avoids String allocation during deserialization.
 *
 * <p>Computes hashes directly from UTF-8 bytes in the input buffer and matches against
 * pre-computed hashes of known member field names. Supports speculative ordered matching
 * for the common case where JSON fields arrive in schema definition order.
 *
 * <p>Also implements {@link MemberLookup} for compatibility with the existing field mapper API.
 */
final class SmithyMemberLookup implements MemberLookup {

    // FNV-1a constants
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    // Members in definition order (for speculative matching)
    final long[] orderedHashes;
    final Schema[] orderedSchemas;
    final byte[][] orderedNameBytes;

    SmithyMemberLookup(List<Schema> members, boolean useJsonName) {
        int size = members.size();
        this.orderedHashes = new long[size];
        this.orderedSchemas = new Schema[size];
        this.orderedNameBytes = new byte[size][];

        for (int i = 0; i < size; i++) {
            Schema m = members.get(i);
            String fieldName;
            if (useJsonName) {
                var jsonNameTrait = m.getTrait(TraitKey.JSON_NAME_TRAIT);
                fieldName = jsonNameTrait != null ? jsonNameTrait.getValue() : m.memberName();
            } else {
                fieldName = m.memberName();
            }
            byte[] nameBytes = fieldName.getBytes(StandardCharsets.UTF_8);
            orderedNameBytes[i] = nameBytes;
            orderedHashes[i] = fnvHash(nameBytes, 0, nameBytes.length);
            orderedSchemas[i] = m;
        }
    }

    /**
     * Looks up a member by matching the field name bytes directly from the input buffer.
     * No String allocation occurs on the common path.
     *
     * <p>This method is stateless and thread-safe (the schema extension is shared).
     * Speculative ordered matching is handled by the caller passing expectedNext.
     *
     * @param buf input buffer containing the field name
     * @param start start offset of the field name (after opening quote)
     * @param end end offset of the field name (before closing quote)
     * @param expectedNext the expected next member index for speculative matching (-1 to disable)
     * @return the matched member Schema, or null if not found
     */
    /**
     * Looks up with a pre-computed hash. Avoids re-hashing the field name bytes.
     * The hash is computed inline during field name scanning in readStruct.
     */
    Schema lookupWithHash(byte[] buf, int start, int end, long hash, int expectedNext) {
        int nameLen = end - start;

        // Speculative fast path: hash + length match is sufficient.
        if (expectedNext >= 0 && expectedNext < orderedHashes.length
                && orderedHashes[expectedNext] == hash
                && orderedNameBytes[expectedNext].length == nameLen) {
            return orderedSchemas[expectedNext];
        }

        // Slow path: linear scan with full byte verification for safety
        for (int i = 0; i < orderedHashes.length; i++) {
            if (orderedHashes[i] == hash
                    && orderedNameBytes[i].length == nameLen
                    && Arrays.equals(buf, start, end, orderedNameBytes[i], 0, nameLen)) {
                return orderedSchemas[i];
            }
        }

        return null;
    }

    /**
     * MemberLookup interface implementation for compatibility with JsonFieldMapper.
     * This path allocates a String→byte[] conversion but is only used by non-performance-critical code.
     */
    @Override
    public Schema member(String memberName) {
        byte[] nameBytes = memberName.getBytes(StandardCharsets.UTF_8);
        long hash = fnvHash(nameBytes, 0, nameBytes.length);

        for (int i = 0; i < orderedHashes.length; i++) {
            if (orderedHashes[i] == hash
                    && nameBytes.length == orderedNameBytes[i].length
                    && Arrays.equals(nameBytes, orderedNameBytes[i])) {
                return orderedSchemas[i];
            }
        }
        return null;
    }

    /**
     * Computes FNV-1a 64-bit hash of bytes in the given range.
     */
    static long fnvHash(byte[] buf, int start, int end) {
        long hash = FNV_OFFSET;
        for (int i = start; i < end; i++) {
            hash ^= buf[i] & 0xFF;
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
