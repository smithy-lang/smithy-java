/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.dns;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * DNS resolver with caching and round-robin address rotation.
 *
 * <p>This resolver wraps a delegate resolver and provides:
 * <ul>
 *   <li><b>Caching:</b> Caches resolved addresses to reduce DNS queries</li>
 *   <li><b>Round-robin rotation:</b> Rotates through addresses on each resolution,
 *       distributing load across multiple endpoints</li>
 *   <li><b>DNS outage resilience:</b> Retains at least one cached address per family
 *       even when DNS resolution fails or returns different addresses</li>
 *   <li><b>Address family separation:</b> Maintains separate rotation for IPv6 and
 *       IPv4 addresses to support Happy Eyeballs connection racing</li>
 * </ul>
 *
 * <h2>TTL Behavior</h2>
 *
 * <p>The configured TTL controls how often this resolver queries its delegate for
 * fresh addresses. This is independent of DNS record TTLs, which are handled by
 * the delegate resolver (typically the JVM's built-in cache).
 *
 * <p>Example with a 1-minute re-resolve TTL and 5-minute DNS record TTL:
 * <ul>
 *   <li>This resolver queries the delegate every 1 minute</li>
 *   <li>The delegate (JVM) queries actual DNS every 5 minutes</li>
 *   <li>Result: Address rotation updates every minute; DNS queries every 5 minutes</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>This class is thread-safe. Per-hostname operations use ReentrantLock to avoid
 * pinning virtual threads (which occurs with synchronized blocks).
 */
final class CachingDnsResolver implements DnsResolver {

    private final DnsResolver delegate;
    private final Duration ttl;
    private final ConcurrentHashMap<String, AddressCache> cache = new ConcurrentHashMap<>();

    CachingDnsResolver(DnsResolver delegate, Duration ttl) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must not be negative: " + ttl);
        }
    }

    @Override
    public List<InetAddress> resolve(String hostname) throws IOException {
        Objects.requireNonNull(hostname, "hostname");
        // Normalize hostname to lowercase for consistent cache keys
        String normalizedHost = hostname.toLowerCase(Locale.ROOT);
        AddressCache addressCache = cache.computeIfAbsent(normalizedHost, h -> new AddressCache());

        // Initial resolution - must block to avoid redundant DNS lookups
        if (addressCache.isEmpty()) {
            addressCache.lock.lock();
            try {
                // Double-check after acquiring lock
                if (addressCache.isEmpty()) {
                    refreshCache(normalizedHost, addressCache);
                }
            } finally {
                addressCache.lock.unlock();
            }

            List<InetAddress> result = addressCache.getRotated();
            if (result.isEmpty()) {
                throw new IOException("No addresses resolved for: " + hostname);
            }
            return result;
        }

        // Have cached data - use stale-while-revalidate
        if (addressCache.isExpired(ttl)) {
            // Only one thread does the refresh, others get stale data
            if (addressCache.refreshing.compareAndSet(false, true)) {
                try {
                    refreshCache(normalizedHost, addressCache);
                } finally {
                    addressCache.refreshing.set(false);
                }
            }
        }

        return addressCache.getRotated();
    }

    @Override
    public void purgeCache(String hostname) {
        cache.remove(hostname.toLowerCase(Locale.ROOT));
    }

    @Override
    public void reportFailure(InetAddress address) {
        // Deprioritize failed address by moving it to end of its family list
        for (AddressCache addressCache : cache.values()) {
            addressCache.deprioritize(address);
        }
    }

    @Override
    public void purgeCache() {
        cache.clear();
    }

    private void refreshCache(String hostname, AddressCache addressCache) throws IOException {
        try {
            List<InetAddress> fresh = delegate.resolve(hostname);
            addressCache.refresh(fresh);
        } catch (IOException e) {
            if (addressCache.isEmpty()) {
                throw new IOException(
                        "DNS resolution failed and no cached addresses available for: " + hostname,
                        e);
            }
            // DNS failure with existing cache: use stale addresses for resilience
        }
    }

    @Override
    public String toString() {
        return "CachingDnsResolver{delegate=" + delegate + ", ttl=" + ttl + "}";
    }

    /**
     * Per-hostname cache using copy-on-write for lock-free reads.
     *
     * <p>Maintains separate cached arrays for IPv6 and IPv4 addresses.
     * Reads use atomic rotation indexes for round-robin distribution without locking.
     * Writes (refresh, deprioritize) take a lock and rebuild the cached arrays.
     *
     * <p>Uses ReentrantLock instead of synchronized to avoid pinning virtual threads.
     */
    private static final class AddressCache {
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicBoolean refreshing = new AtomicBoolean(false);

        // Copy-on-write cached arrays - rebuilt on refresh/deprioritize
        private volatile InetAddress[] ipv6Cached = new InetAddress[0];
        private volatile InetAddress[] ipv4Cached = new InetAddress[0];

        // Atomic rotation indexes for lock-free round-robin
        private final AtomicInteger ipv6Index = new AtomicInteger();
        private final AtomicInteger ipv4Index = new AtomicInteger();

        // Internal mutable state - only accessed under lock
        private final List<InetAddress> ipv6List = new ArrayList<>();
        private final List<InetAddress> ipv4List = new ArrayList<>();
        private final Set<InetAddress> knownAddresses = new HashSet<>();

        private volatile Instant lastRefresh = Instant.MIN;

        boolean isExpired(Duration ttl) {
            return Duration.between(lastRefresh, Instant.now()).compareTo(ttl) >= 0;
        }

        boolean isEmpty() {
            return ipv6Cached.length == 0 && ipv4Cached.length == 0;
        }

        /**
         * Lock-free read with atomic rotation.
         *
         * <p>Returns addresses with round-robin rotation applied.
         * IPv6 addresses first, then IPv4.
         */
        List<InetAddress> getRotated() {
            InetAddress[] v6 = ipv6Cached;
            InetAddress[] v4 = ipv4Cached;

            int totalSize = v6.length + v4.length;
            if (totalSize == 0) {
                return List.of();
            }

            InetAddress[] result = new InetAddress[totalSize];
            int i = 0;

            // Copy IPv6 with rotation
            // Use floorMod to handle integer overflow (getAndIncrement can go negative)
            if (v6.length > 0) {
                int offset = Math.floorMod(ipv6Index.getAndIncrement(), v6.length);
                for (int j = 0; j < v6.length; j++) {
                    result[i++] = v6[(offset + j) % v6.length];
                }
            }

            // Copy IPv4 with rotation
            if (v4.length > 0) {
                int offset = Math.floorMod(ipv4Index.getAndIncrement(), v4.length);
                for (int j = 0; j < v4.length; j++) {
                    result[i++] = v4[(offset + j) % v4.length];
                }
            }

            return List.of(result);
        }

        /**
         * Updates the cache with fresh addresses from DNS.
         *
         * <p>New addresses are added to the end of the appropriate list.
         * Addresses no longer present in DNS are removed, but at least one
         * address of each family is retained for resilience.
         */
        void refresh(List<InetAddress> fresh) {
            lock.lock();
            try {
                Set<InetAddress> freshSet = new HashSet<>(fresh);

                // Remove stale addresses (keep at least one per family)
                removeStaleAddresses(ipv6List, freshSet);
                removeStaleAddresses(ipv4List, freshSet);

                // Add new addresses
                for (InetAddress addr : fresh) {
                    if (!knownAddresses.contains(addr)) {
                        if (addr instanceof Inet6Address) {
                            ipv6List.add(addr);
                        } else if (addr instanceof Inet4Address) {
                            ipv4List.add(addr);
                        }
                    }
                }

                // Rebuild known set
                knownAddresses.clear();
                knownAddresses.addAll(ipv6List);
                knownAddresses.addAll(ipv4List);

                // Publish new cached arrays (copy-on-write)
                ipv6Cached = ipv6List.toArray(InetAddress[]::new);
                ipv4Cached = ipv4List.toArray(InetAddress[]::new);

                lastRefresh = Instant.now();
            } finally {
                lock.unlock();
            }
        }

        /**
         * Removes addresses not present in the fresh set.
         *
         * <p>At least one address is always retained to provide resilience
         * against DNS changes that might temporarily remove all addresses.
         */
        private void removeStaleAddresses(List<InetAddress> list, Set<InetAddress> fresh) {
            if (list.size() <= 1) {
                return;
            }
            list.removeIf(addr -> list.size() > 1 && !fresh.contains(addr));
        }

        /**
         * Move a failed address to the end of its family list.
         *
         * <p>This deprioritizes the address so other addresses are tried first,
         * while still keeping it available as a fallback.
         */
        void deprioritize(InetAddress address) {
            lock.lock();
            try {
                List<InetAddress> list = (address instanceof Inet6Address) ? ipv6List : ipv4List;
                if (list.remove(address)) {
                    list.add(address);
                    // Rebuild cached array
                    if (address instanceof Inet6Address) {
                        ipv6Cached = ipv6List.toArray(InetAddress[]::new);
                    } else {
                        ipv4Cached = ipv4List.toArray(InetAddress[]::new);
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
