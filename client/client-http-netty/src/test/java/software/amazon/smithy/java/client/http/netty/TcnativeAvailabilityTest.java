/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.ssl.OpenSsl;
import org.junit.jupiter.api.Test;

/**
 * Confirms netty-tcnative (BoringSSL) loads on the build/test host. The VT-blocking transport
 * prefers the OpenSSL TLS engine and falls back to the JDK SSLEngine when unavailable; this test
 * documents and guards that the native is actually wired up on supported platforms.
 */
class TcnativeAvailabilityTest {

    @Test
    void boringSslIsAvailable() {
        if (!OpenSsl.isAvailable()) {
            Throwable cause = OpenSsl.unavailabilityCause();
            throw new AssertionError("netty-tcnative BoringSSL not available on this host", cause);
        }
        assertTrue(OpenSsl.isAlpnSupported(), "BoringSSL should support ALPN");
        System.out.println("BoringSSL available: " + OpenSsl.versionString()
                + " (ALPN=" + OpenSsl.isAlpnSupported() + ")");
    }
}
