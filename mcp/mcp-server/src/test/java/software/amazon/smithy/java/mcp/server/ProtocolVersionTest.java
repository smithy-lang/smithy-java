/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProtocolVersionTest {

    @Test
    void knownVersionsResolveCorrectly() {
        assertEquals(KnownProtocolVersion.V2024_11_05, ProtocolVersion.parse("2024-11-05"));
        assertEquals(KnownProtocolVersion.V2025_03_26, ProtocolVersion.parse("2025-03-26"));
        assertEquals(KnownProtocolVersion.V2025_06_18, ProtocolVersion.parse("2025-06-18"));
        assertEquals(KnownProtocolVersion.V2025_11_25, ProtocolVersion.parse("2025-11-25"));
        assertEquals(KnownProtocolVersion.V2026_07_28, ProtocolVersion.parse("2026-07-28"));
    }

    @Test
    void unknownVersionReturnsUnknownVersion() {
        var version = ProtocolVersion.parse("9999-01-01");
        assertTrue(version instanceof UnknownProtocolVersion);
        assertEquals("9999-01-01", version.identifier());
    }

    @Test
    void nullVersionResolvesToDefault() {
        var version = ProtocolVersion.parse(null);
        assertEquals(ProtocolVersion.defaultVersion(), version);
    }

    @Test
    void defaultVersionIsLegacyHttpCompatibilityVersion() {
        assertEquals("2025-03-26", ProtocolVersion.defaultVersion().identifier());
    }

    @Test
    void compareToOrdersChronologically() {
        assertTrue(KnownProtocolVersion.V2024_11_05.compareTo(KnownProtocolVersion.V2025_03_26) < 0);
        assertTrue(KnownProtocolVersion.V2025_03_26.compareTo(KnownProtocolVersion.V2025_06_18) < 0);
        assertTrue(KnownProtocolVersion.V2025_06_18.compareTo(KnownProtocolVersion.V2025_11_25) < 0);
        assertTrue(KnownProtocolVersion.V2025_11_25.compareTo(KnownProtocolVersion.V2026_07_28) < 0);
        assertEquals(0, KnownProtocolVersion.V2026_07_28.compareTo(KnownProtocolVersion.V2026_07_28));
    }

    @Test
    void knownVersionsRankAboveUnknown() {
        var unknown = ProtocolVersion.parse("0000-00-00");
        assertTrue(unknown instanceof UnknownProtocolVersion);
    }
}
