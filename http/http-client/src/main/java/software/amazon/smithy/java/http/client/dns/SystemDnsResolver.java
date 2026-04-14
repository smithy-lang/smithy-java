/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.dns;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * DNS resolver using the JVM's default resolution mechanism.
 *
 * <p>This resolver delegates to {@link InetAddress#getAllByName(String)}, which typically uses the operating system's
 * DNS resolution. The JVM maintains its own DNS cache with configurable TTLs via security properties:
 * <ul>
 *   <li>{@code networkaddress.cache.ttl} - seconds to cache successful lookups</li>
 *   <li>{@code networkaddress.cache.negative.ttl} - seconds to cache failed lookups</li>
 * </ul>
 *
 * <p>This resolver is stateless and does not perform any caching beyond what the JVM provides. It returns all
 * addresses from DNS resolution, preserving the order returned by the underlying resolver.
 */
final class SystemDnsResolver implements DnsResolver {

    static final SystemDnsResolver INSTANCE = new SystemDnsResolver();

    private SystemDnsResolver() {}

    @Override
    public List<InetAddress> resolve(String hostname) throws IOException {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(hostname);
            if (addresses.length == 0) {
                throw new IOException("DNS resolution returned no addresses for: " + hostname);
            }
            return List.of(addresses);
        } catch (UnknownHostException e) {
            throw new IOException("Failed to resolve hostname: " + hostname, e);
        }
    }

    @Override
    public String toString() {
        return "SystemDnsResolver";
    }
}
