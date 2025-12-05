/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;

class ProxySelectorTest {

    private static final URI TARGET = URI.create("https://example.com");
    private static final Context CTX = Context.create();

    @Test
    void directReturnsEmptyList() {
        var selector = ProxySelector.direct();
        var result = selector.select(TARGET, CTX);

        assertTrue(result.isEmpty());
    }

    @Test
    void ofReturnsSingleProxy() {
        var proxy = new ProxyConfiguration(URI.create("http://proxy:8080"), ProxyConfiguration.ProxyType.HTTP);
        var selector = ProxySelector.of(proxy);
        var result = selector.select(TARGET, CTX);

        assertEquals(List.of(proxy), result);
    }

    @Test
    void ofReturnsMultipleProxiesInOrder() {
        var proxy1 = new ProxyConfiguration(URI.create("http://proxy1:8080"), ProxyConfiguration.ProxyType.HTTP);
        var proxy2 = new ProxyConfiguration(URI.create("http://proxy2:8080"), ProxyConfiguration.ProxyType.HTTP);
        var selector = ProxySelector.of(proxy1, proxy2);
        var result = selector.select(TARGET, CTX);

        assertEquals(List.of(proxy1, proxy2), result);
    }

    @Test
    void noFailoverReturnsOnlyFirstProxy() {
        var proxy1 = new ProxyConfiguration(URI.create("http://proxy1:8080"), ProxyConfiguration.ProxyType.HTTP);
        var proxy2 = new ProxyConfiguration(URI.create("http://proxy2:8080"), ProxyConfiguration.ProxyType.HTTP);
        var delegate = ProxySelector.of(proxy1, proxy2);
        var selector = ProxySelector.noFailover(delegate);
        var result = selector.select(TARGET, CTX);

        assertEquals(List.of(proxy1), result);
    }

    @Test
    void noFailoverReturnsEmptyWhenDelegateReturnsEmpty() {
        var delegate = ProxySelector.direct();
        var selector = ProxySelector.noFailover(delegate);
        var result = selector.select(TARGET, CTX);

        assertTrue(result.isEmpty());
    }

    @Test
    void noFailoverDelegatesConnectFailed() {
        var failedProxy = new AtomicReference<ProxyConfiguration>();
        var proxy = new ProxyConfiguration(URI.create("http://proxy:8080"), ProxyConfiguration.ProxyType.HTTP);
        var delegate = new ProxySelector() {
            @Override
            public List<ProxyConfiguration> select(URI target, Context context) {
                return List.of(proxy);
            }

            @Override
            public void connectFailed(URI target, Context context, ProxyConfiguration p, IOException cause) {
                failedProxy.set(p);
            }
        };

        var selector = ProxySelector.noFailover(delegate);
        selector.connectFailed(TARGET, CTX, proxy, new IOException("test"));

        assertEquals(proxy, failedProxy.get());
    }
}
