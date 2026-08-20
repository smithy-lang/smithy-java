/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class CompactStringAccessTest {

    @Test
    public void readsExactCompactStringRepresentationsWithoutJvmFlags() {
        assertThat(CompactStringAccess.isAvailable()).isTrue();
        assertThat(CompactStringAccess.latin1Bytes("")).isEmpty();
        assertThat(CompactStringAccess.latin1Bytes("plain ASCII"))
                .isEqualTo("plain ASCII".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(CompactStringAccess.latin1Bytes("caf\u00e9"))
                .isEqualTo("caf\u00e9".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(CompactStringAccess.latin1Bytes("\u20ac")).isNull();
    }
}
