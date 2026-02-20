/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.client.ProxyConfiguration;

class RouteTest {

    @Test
    void fromUriUsesDefaultPortForHttp() {
        var route = Route.from(URI.create("http://example.com/path"));

        assertEquals(80, route.port());
    }

    @Test
    void fromUriUsesDefaultPortForHttps() {
        var route = Route.from(URI.create("https://example.com/path"));

        assertEquals(443, route.port());
    }

    @Test
    void fromUriUsesExplicitPort() {
        var route = Route.from(URI.create("https://example.com:8443/path"));

        assertEquals(8443, route.port());
    }

    @Test
    void fromUriNormalizesHostToLowercase() {
        var route = Route.from(URI.create("https://EXAMPLE.COM/path"));

        assertEquals("example.com", route.host());
    }

    @Test
    void fromUriIgnoresPathAndQuery() {
        var route1 = Route.from(URI.create("https://example.com/users?id=1"));
        var route2 = Route.from(URI.create("https://example.com/posts?id=2"));

        assertEquals(route1, route2);
    }

    @Test
    void fromUriThrowsOnMissingScheme() {
        assertThrows(IllegalArgumentException.class,
                () -> Route.from(URI.create("example.com/path")));
    }

    @Test
    void fromUriThrowsOnMissingHost() {
        assertThrows(IllegalArgumentException.class,
                () -> Route.from(URI.create("http:///path")));
    }

    @Test
    void constructorThrowsOnInvalidScheme() {
        assertThrows(IllegalArgumentException.class,
                () -> new Route("ftp", "example.com", 21, null));
    }

    @Test
    void constructorThrowsOnInvalidPort() {
        assertThrows(IllegalArgumentException.class,
                () -> new Route("http", "example.com", 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Route("http", "example.com", 70000, null));
    }

    @Test
    void isSecureReturnsTrueForHttps() {
        var route = Route.direct("https", "example.com", 443);

        assertTrue(route.isSecure());
    }

    @Test
    void isSecureReturnsFalseForHttp() {
        var route = Route.direct("http", "example.com", 80);

        assertFalse(route.isSecure());
    }

    @Test
    void connectionTargetReturnsProxyWhenProxied() {
        var proxy = new ProxyConfiguration(URI.create("http://proxy:8080"), ProxyConfiguration.ProxyType.HTTP);
        var route = Route.viaProxy("https", "example.com", 443, proxy);

        assertEquals("proxy:8080", route.connectionTarget());
    }

    @Test
    void connectionTargetReturnsHostWhenDirect() {
        var route = Route.direct("https", "example.com", 443);

        assertEquals("example.com:443", route.connectionTarget());
    }

    @Test
    void tunnelTargetAlwaysReturnsTargetHost() {
        var proxy = new ProxyConfiguration(URI.create("http://proxy:8080"), ProxyConfiguration.ProxyType.HTTP);
        var route = Route.viaProxy("https", "example.com", 443, proxy);

        assertEquals("example.com:443", route.tunnelTarget());
    }

    @Test
    void withProxyCreatesNewRouteWithProxy() {
        var route = Route.direct("https", "example.com", 443);
        var proxy = new ProxyConfiguration(URI.create("http://proxy:8080"), ProxyConfiguration.ProxyType.HTTP);
        var proxied = route.withProxy(proxy);

        assertFalse(route.usesProxy());
        assertTrue(proxied.usesProxy());
        assertEquals(route.host(), proxied.host());
    }

    @Test
    void withoutProxyCreatesNewRouteWithoutProxy() {
        var proxy = new ProxyConfiguration(URI.create("http://proxy:8080"), ProxyConfiguration.ProxyType.HTTP);
        var route = Route.viaProxy("https", "example.com", 443, proxy);
        var direct = route.withoutProxy();

        assertTrue(route.usesProxy());
        assertFalse(direct.usesProxy());
    }
}
