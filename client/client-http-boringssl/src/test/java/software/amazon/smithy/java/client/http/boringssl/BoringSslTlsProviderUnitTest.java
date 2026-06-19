/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.boringssl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit checks that need no native library (so they run on every host, unlike the handshake tests).
 */
class BoringSslTlsProviderUnitTest {

    @Test
    void supportsEpoll() {
        // Engine-based provider: it consumes the internal epoll channel via SslEngineTransports, so the
        // client may use the epoll backend for it (no-arg ctor avoids any native context build).
        assertTrue(new BoringSslTlsProvider().supportsEpoll());
    }
}
