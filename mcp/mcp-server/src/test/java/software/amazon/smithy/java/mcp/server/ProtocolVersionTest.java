/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProtocolVersionTest {

    @Test
    void knownVersionsResolveCorrectly() {
        assertInstanceOf(ProtocolVersion.v2024_11_05.class, ProtocolVersion.version("2024-11-05"));
        assertInstanceOf(ProtocolVersion.v2025_03_26.class, ProtocolVersion.version("2025-03-26"));
        assertInstanceOf(ProtocolVersion.v2025_06_18.class, ProtocolVersion.version("2025-06-18"));
        assertInstanceOf(ProtocolVersion.v2025_11_25.class, ProtocolVersion.version("2025-11-25"));
    }

    @Test
    void unknownVersionReturnsUnknownVersion() {
        var version = ProtocolVersion.version("9999-01-01");
        assertInstanceOf(ProtocolVersion.UnknownVersion.class, version);
        assertEquals("9999-01-01", version.identifier());
    }

    @Test
    void nullVersionResolvesToDefault() {
        var version = ProtocolVersion.version(null);
        assertEquals(ProtocolVersion.defaultVersion(), version);
    }

    @Test
    void defaultVersionIs2025_03_26() {
        assertEquals("2025-03-26", ProtocolVersion.defaultVersion().identifier());
    }

    @Test
    void latestVersionIs2025_11_25() {
        assertInstanceOf(ProtocolVersion.v2025_11_25.class, ProtocolVersion.latestVersion());
        assertEquals("2025-11-25", ProtocolVersion.latestVersion().identifier());
    }

    @Test
    void compareToOrdersChronologically() {
        assertTrue(ProtocolVersion.v2024_11_05.INSTANCE.compareTo(ProtocolVersion.v2025_03_26.INSTANCE) < 0);
        assertTrue(ProtocolVersion.v2025_03_26.INSTANCE.compareTo(ProtocolVersion.v2025_06_18.INSTANCE) < 0);
        assertTrue(ProtocolVersion.v2025_06_18.INSTANCE.compareTo(ProtocolVersion.v2025_11_25.INSTANCE) < 0);
        assertEquals(0, ProtocolVersion.v2025_11_25.INSTANCE.compareTo(ProtocolVersion.v2025_11_25.INSTANCE));
    }

    @Test
    void knownVersionsRankAboveUnknown() {
        var unknown = ProtocolVersion.version("0000-00-00");
        assertTrue(ProtocolVersion.v2024_11_05.INSTANCE.compareTo(unknown) > 0);
        assertTrue(ProtocolVersion.v2025_11_25.INSTANCE.compareTo(unknown) > 0);
    }
}
