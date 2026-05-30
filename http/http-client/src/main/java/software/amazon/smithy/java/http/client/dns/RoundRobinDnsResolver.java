/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.dns;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DNS resolver decorator that rotates multi-address results per hostname.
 */
final class RoundRobinDnsResolver implements DnsResolver {
    private final DnsResolver delegate;
    private final ConcurrentMap<String, AtomicInteger> offsets = new ConcurrentHashMap<>();

    RoundRobinDnsResolver(DnsResolver delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public List<InetAddress> resolve(String hostname) throws IOException {
        List<InetAddress> addresses = delegate.resolve(hostname);
        int size = addresses.size();
        if (size <= 1) {
            return addresses;
        }

        int offset = Math.floorMod(
                offsets.computeIfAbsent(normalize(hostname), ignored -> new AtomicInteger()).getAndIncrement(),
                size);
        if (offset == 0) {
            return addresses;
        }

        List<InetAddress> rotated = new ArrayList<>(size);
        rotated.addAll(addresses.subList(offset, size));
        rotated.addAll(addresses.subList(0, offset));
        return List.copyOf(rotated);
    }

    @Override
    public void reportFailure(InetAddress address) {
        delegate.reportFailure(address);
    }

    @Override
    public void purgeCache(String hostname) {
        offsets.remove(normalize(hostname));
        delegate.purgeCache(hostname);
    }

    @Override
    public void purgeCache() {
        offsets.clear();
        delegate.purgeCache();
    }

    private static String normalize(String hostname) {
        return hostname.toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "RoundRobinDnsResolver(" + delegate + ")";
    }
}
