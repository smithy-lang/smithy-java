/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.dns;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;

/**
 * A blocking DNS resolver used to resolve hostnames to IP addresses.
 *
 * <p>Thread-safe: All implementations must be safe for concurrent use.
 */
public interface DnsResolver {
    // Design note: we return all addresses and not just one because it allows for algorithms like happy eyeballs to
    // race connections across IPs.

    /**
     * Resolves a hostname to IP addresses.
     *
     * <p>Returns all available addresses in preference order, typically with IPv6
     * addresses before IPv4 as determined by the system's address selection policy
     * (RFC 6724).
     *
     * <p>Implementations may:
     * <ul>
     *   <li>Rotate addresses across calls for load distribution</li>
     *   <li>Cache results with appropriate TTL</li>
     *   <li>Exclude recently failed addresses</li>
     * </ul>
     *
     * <p>This method may block for DNS lookup.
     *
     * @param hostname the hostname to resolve (e.g., "api.example.com")
     * @return unmodifiable list of resolved IP addresses, never null or empty
     * @throws IOException if DNS resolution fails and no cached addresses are available
     */
    List<InetAddress> resolve(String hostname) throws IOException;

    /**
     * Reports that a connection attempt to an address failed.
     *
     * <p>Implementations may use this to temporarily deprioritize or exclude the
     * address from future results until it likely recovers.
     *
     * <p><b>Default:</b> No-op. Stateless resolvers ignore failure reports.
     *
     * @param address the IP address that failed to connect
     */
    default void reportFailure(InetAddress address) {}

    /**
     * Purges cached entries for a specific hostname.
     *
     * <p>Forces a fresh DNS lookup on the next {@link #resolve} call for this hostname.
     *
     * <p><b>Default:</b> No-op. Stateless resolvers have no cache.
     *
     * @param hostname the hostname to purge from cache
     */
    default void purgeCache(String hostname) {}

    /**
     * Purges all cached entries.
     *
     * <p>Forces fresh DNS lookups for all hostnames.
     *
     * <p><b>Default:</b> No-op. Stateless resolvers have no cache.
     */
    default void purgeCache() {}

    /**
     * Creates a DNS resolver using the JVM's default resolution.
     *
     * <p>Delegates to {@link InetAddress#getAllByName(String)}, which respects
     * JVM DNS cache settings configured via security properties:
     * <ul>
     *   <li>{@code networkaddress.cache.ttl} - seconds to cache successful lookups (default: 30)</li>
     *   <li>{@code networkaddress.cache.negative.ttl} - seconds to cache failures (default: 10)</li>
     * </ul>
     *
     * <p>This resolver is stateless and does not track failures or perform rotation.
     *
     * @return system DNS resolver singleton
     */
    static DnsResolver system() {
        return SystemDnsResolver.INSTANCE;
    }

    /**
     * Creates a DNS resolver with static hostname mappings.
     *
     * <p>Returns pre-configured addresses without performing DNS queries.
     * Useful for testing and local development.
     *
     * @param mappings hostname to address list mappings
     * @return static DNS resolver
     * @throws NullPointerException if mappings is null
     */
    static DnsResolver staticMapping(Map<String, List<InetAddress>> mappings) {
        return new StaticDnsResolver(mappings);
    }
}
