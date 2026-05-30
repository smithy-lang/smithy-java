/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.dns;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoundRobinDnsResolverTest {

    @Test
    void rotatesAddressesPerHostname() throws Exception {
        var addr1 = InetAddress.getByName("127.0.0.1");
        var addr2 = InetAddress.getByName("127.0.0.2");
        var addr3 = InetAddress.getByName("127.0.0.3");
        var resolver = DnsResolver.roundRobin(DnsResolver.staticMapping(Map.of(
                "example.com",
                List.of(addr1, addr2, addr3))));

        assertEquals(List.of(addr1, addr2, addr3), resolver.resolve("example.com"));
        assertEquals(List.of(addr2, addr3, addr1), resolver.resolve("example.com"));
        assertEquals(List.of(addr3, addr1, addr2), resolver.resolve("example.com"));
        assertEquals(List.of(addr1, addr2, addr3), resolver.resolve("example.com"));
    }

    @Test
    void rotatesHostnamesIndependently() throws Exception {
        var addr1 = InetAddress.getByName("127.0.0.1");
        var addr2 = InetAddress.getByName("127.0.0.2");
        var addr3 = InetAddress.getByName("127.0.0.3");
        var addr4 = InetAddress.getByName("127.0.0.4");
        var resolver = DnsResolver.roundRobin(DnsResolver.staticMapping(Map.of(
                "a.example.com",
                List.of(addr1, addr2),
                "b.example.com",
                List.of(addr3, addr4))));

        assertEquals(List.of(addr1, addr2), resolver.resolve("a.example.com"));
        assertEquals(List.of(addr3, addr4), resolver.resolve("b.example.com"));
        assertEquals(List.of(addr2, addr1), resolver.resolve("a.example.com"));
        assertEquals(List.of(addr4, addr3), resolver.resolve("b.example.com"));
    }

    @Test
    void purgeCacheResetsRotation() throws Exception {
        var addr1 = InetAddress.getByName("127.0.0.1");
        var addr2 = InetAddress.getByName("127.0.0.2");
        var resolver = DnsResolver.roundRobin(DnsResolver.staticMapping(Map.of(
                "example.com",
                List.of(addr1, addr2))));

        assertEquals(List.of(addr1, addr2), resolver.resolve("example.com"));
        assertEquals(List.of(addr2, addr1), resolver.resolve("example.com"));

        resolver.purgeCache("example.com");

        assertEquals(List.of(addr1, addr2), resolver.resolve("example.com"));
    }

    @Test
    void delegatesFailureReportsAndCachePurges() throws Exception {
        var addr = InetAddress.getByName("127.0.0.1");
        var delegate = new RecordingResolver(addr);
        var resolver = DnsResolver.roundRobin(delegate);

        resolver.reportFailure(addr);
        resolver.purgeCache("example.com");
        resolver.purgeCache();

        assertEquals(1, delegate.failures);
        assertEquals(1, delegate.hostnamePurges);
        assertEquals(1, delegate.allPurges);
    }

    private static final class RecordingResolver implements DnsResolver {
        private final InetAddress address;
        private int failures;
        private int hostnamePurges;
        private int allPurges;

        RecordingResolver(InetAddress address) {
            this.address = address;
        }

        @Override
        public List<InetAddress> resolve(String hostname) throws IOException {
            return List.of(address);
        }

        @Override
        public void reportFailure(InetAddress address) {
            failures++;
        }

        @Override
        public void purgeCache(String hostname) {
            hostnamePurges++;
        }

        @Override
        public void purgeCache() {
            allPurges++;
        }
    }
}
