/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import software.amazon.smithy.java.core.schema.Schema;

/**
 * Fast element name lookup using byte-span comparison with speculative fast path.
 *
 * <p>When XML arrives in definition order (common for smithy-to-smithy communication),
 * the speculative path matches on the first try. For out-of-order elements, falls back
 * to linear scan with length pre-filter.
 *
 * <p>This class is thread-safe and immutable. Per-invocation speculation state is tracked
 * externally via the int[] hint parameter.
 */
final class XmlMemberLookup {

    private final byte[][] memberNames;
    private final int[] memberLens;
    private final Schema[] memberSchemas;

    XmlMemberLookup(Map<String, Schema> elements) {
        int size = elements.size();
        memberNames = new byte[size][];
        memberLens = new int[size];
        memberSchemas = new Schema[size];
        int i = 0;
        for (var entry : elements.entrySet()) {
            byte[] nameBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
            memberNames[i] = nameBytes;
            memberLens[i] = nameBytes.length;
            memberSchemas[i] = entry.getValue();
            i++;
        }
    }

    Schema findMember(byte[] buf, int nameStart, int nameLen, int[] hint) {
        int len = memberNames.length;
        if (len == 0)
            return null;

        int idx = hint[0];
        if (idx < len && memberLens[idx] == nameLen) {
            byte[] expected = memberNames[idx];
            if (Arrays.equals(buf, nameStart, nameStart + nameLen, expected, 0, nameLen)) {
                hint[0] = idx + 1;
                return memberSchemas[idx];
            }
        }

        for (int i = 0; i < len; i++) {
            if (i == idx)
                continue;
            if (memberLens[i] == nameLen
                    && Arrays.equals(buf, nameStart, nameStart + nameLen, memberNames[i], 0, nameLen)) {
                hint[0] = i + 1;
                return memberSchemas[i];
            }
        }

        return null;
    }
}
