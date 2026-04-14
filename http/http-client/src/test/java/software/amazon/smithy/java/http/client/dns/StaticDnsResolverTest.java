/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.dns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StaticDnsResolverTest {

    @Test
    void resolvesConfiguredHostname() throws Exception {
        var addr = InetAddress.getLoopbackAddress();
        var resolver = new StaticDnsResolver(Map.of("example.com", List.of(addr)));
        var result = resolver.resolve("example.com");

        assertEquals(List.of(addr), result);
    }

    @Test
    void resolveIsCaseInsensitive() throws Exception {
        var addr = InetAddress.getLoopbackAddress();
        var resolver = new StaticDnsResolver(Map.of("example.com", List.of(addr)));

        assertEquals(List.of(addr), resolver.resolve("EXAMPLE.COM"));
        assertEquals(List.of(addr), resolver.resolve("Example.Com"));
    }

    @Test
    void throwsForUnknownHostname() {
        var resolver = new StaticDnsResolver(Map.of("known.com", List.of(InetAddress.getLoopbackAddress())));

        assertThrows(IOException.class, () -> resolver.resolve("unknown.com"));
    }

    @Test
    void returnsMultipleAddresses() throws Exception {
        var addr1 = InetAddress.getByName("127.0.0.1");
        var addr2 = InetAddress.getByName("127.0.0.2");
        var resolver = new StaticDnsResolver(Map.of("example.com", List.of(addr1, addr2)));
        var result = resolver.resolve("example.com");

        assertEquals(2, result.size());
        assertEquals(addr1, result.get(0));
        assertEquals(addr2, result.get(1));
    }

    @Test
    void ignoresEmptyMappings() {
        var resolver = new StaticDnsResolver(Map.of("empty.com", List.of()));

        assertThrows(IOException.class, () -> resolver.resolve("empty.com"));
    }
}
