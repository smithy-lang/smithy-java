/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.dns;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class SystemDnsResolverTest {

    @Test
    void resolvesLocalhost() throws Exception {
        var result = SystemDnsResolver.INSTANCE.resolve("localhost");

        assertFalse(result.isEmpty());
        assertTrue(result.get(0).isLoopbackAddress());
    }

    @Test
    void throwsForUnknownHost() {
        assertThrows(IOException.class,
                () -> SystemDnsResolver.INSTANCE.resolve("this.host.definitely.does.not.exist.invalid"));
    }

    @Test
    void resolvesIpAddressDirectly() throws Exception {
        var result = SystemDnsResolver.INSTANCE.resolve("127.0.0.1");

        assertFalse(result.isEmpty());
        assertTrue(result.get(0).isLoopbackAddress());
    }
}
