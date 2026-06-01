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
