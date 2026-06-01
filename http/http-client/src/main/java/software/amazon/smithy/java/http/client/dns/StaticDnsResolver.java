/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.dns;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DNS resolver with static hostname-to-IP mappings.
 *
 * <p>{@snippet :
 * var resolver = new StaticDnsResolver(Map.of(
 *     "api.example.com", List.of(
 *         InetAddress.getByName("192.168.1.100"),
 *         InetAddress.getByName("192.168.1.101")
 *     ),
 *     "localhost", List.of(InetAddress.getLoopbackAddress())
 * ));
 * }
 */
record StaticDnsResolver(Map<String, List<InetAddress>> mappings) implements DnsResolver {
    StaticDnsResolver(Map<String, List<InetAddress>> mappings) {
        Map<String, List<InetAddress>> copy = new HashMap<>(mappings.size());
        for (Map.Entry<String, List<InetAddress>> entry : mappings.entrySet()) {
            List<InetAddress> value = entry.getValue();
            if (value != null && !value.isEmpty()) {
                copy.put(entry.getKey().toLowerCase(Locale.ROOT), List.copyOf(value));
            }
        }
        this.mappings = Map.copyOf(copy);
    }

    @Override
    public List<InetAddress> resolve(String hostname) throws IOException {
        List<InetAddress> addresses = mappings.get(hostname.toLowerCase(Locale.ROOT));
        if (addresses == null) {
            throw new IOException("No static mapping defined for hostname: " + hostname);
        }
        return addresses;
    }
}
