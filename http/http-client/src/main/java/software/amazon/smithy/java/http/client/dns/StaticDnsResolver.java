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
 * <p>This resolver returns pre-configured addresses for known hostnames without
 * performing any network DNS queries. It is useful for:
 * <ul>
 *   <li>Testing without real DNS infrastructure</li>
 *   <li>Local development with custom hostname mappings</li>
 *   <li>Overriding specific hostnames while delegating others</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * {@snippet :
 * var resolver = new StaticDnsResolver(Map.of(
 *     "api.example.com", new InetAddress[] {
 *         InetAddress.getByName("192.168.1.100"),
 *         InetAddress.getByName("192.168.1.101")
 *     },
 *     "localhost", new InetAddress[] {
 *         InetAddress.getLoopbackAddress()
 *     }
 * ));
 * }
 */
record StaticDnsResolver(Map<String, List<InetAddress>> mappings) implements DnsResolver {
    /**
     * Creates a static resolver with the given hostname mappings.
     *
     * <p>The mappings are defensively copied to prevent external modification.
     *
     * @param mappings hostname to address list mappings; empty lists are permitted
     *                 but will cause {@link #resolve} to throw for that hostname
     */
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

    /**
     * Resolves a hostname to its configured addresses.
     *
     * <p>Returns the pre-configured address list for the hostname.
     *
     * @param hostname the hostname to resolve
     * @return the configured addresses for this hostname, never empty
     * @throws IOException if no mapping exists for the hostname or the mapping is empty
     */
    @Override
    public List<InetAddress> resolve(String hostname) throws IOException {
        List<InetAddress> addresses = mappings.get(hostname.toLowerCase(Locale.ROOT));
        if (addresses == null) {
            throw new IOException("No static mapping defined for hostname: " + hostname);
        }
        return addresses;
    }
}
