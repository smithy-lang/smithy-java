/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import software.amazon.smithy.java.io.uri.SmithyUri;

/**
 * Selects proxies for HTTP requests.
 *
 * <h2>Failover</h2>
 * <p>ProxySelector implementations can return multiple {@link ProxyConfiguration} objects.
 * Implementations will try to connect to each proxy, one after the other, until a connection can be established.
 * To prevent proxy failover, return only a single result using {@link #noFailover(ProxySelector)}.
 *
 * <p>Implementations must be thread-safe.
 */
public interface ProxySelector {
    /**
     * Returns an ordered list of proxies to try for the given request.
     *
     * <p>An empty list means "connect directly".
     *
     * @param target the target URI of the request
     * @return ordered list of proxies to try (may be empty, never null)
     */
    List<ProxyConfiguration> select(SmithyUri target);

    /**
     * Notifies the selector that a connection via the given proxy failed.
     *
     * <p>Implementations can use this to update health / backoff state.
     *
     * @param target the original request target
     * @param proxy the proxy that failed
     * @param cause the IOException that occurred
     */
    default void connectFailed(SmithyUri target, ProxyConfiguration proxy, IOException cause) {
        // default no-op
    }

    /**
     * Returns a ProxySelector that always uses the given proxy configurations in order.
     *
     * @param config proxy configurations
     * @return the created ProxySelector.
     */
    static ProxySelector of(ProxyConfiguration... config) {
        var result = List.of(config);
        return target -> result;
    }

    /**
     * Returns a ProxySelector that never uses a proxy.
     *
     * @return the direct proxy.
     */
    static ProxySelector direct() {
        return target -> Collections.emptyList();
    }

    /**
     * Returns a ProxySelector that takes the first result of the selector to prevent failover.
     *
     * @param delegate Delegate selector to wrap.
     * @return the ProxySelector that does not use failover.
     */
    static ProxySelector noFailover(ProxySelector delegate) {
        return new ProxySelector() {
            @Override
            public List<ProxyConfiguration> select(SmithyUri target) {
                var proxies = delegate.select(target);
                return proxies.isEmpty() ? proxies : List.of(proxies.getFirst());
            }

            @Override
            public void connectFailed(SmithyUri target, ProxyConfiguration proxy, IOException cause) {
                delegate.connectFailed(target, proxy, cause);
            }
        };
    }
}
