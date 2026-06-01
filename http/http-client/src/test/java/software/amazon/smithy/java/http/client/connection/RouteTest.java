/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.client.ProxyConfiguration;
import software.amazon.smithy.java.io.uri.SmithyUri;

class RouteTest {

    @Test
    void fromUriUsesDefaultPortForHttp() {
        var route = Route.from(SmithyUri.of("http://example.com/path"));

        assertEquals(80, route.port());
    }

    @Test
    void fromUriUsesDefaultPortForHttps() {
        var route = Route.from(SmithyUri.of("https://example.com/path"));

        assertEquals(443, route.port());
    }

    @Test
    void fromUriUsesExplicitPort() {
        var route = Route.from(SmithyUri.of("https://example.com:8443/path"));

        assertEquals(8443, route.port());
    }

    @Test
    void fromUriNormalizesHostToLowercase() {
        var route = Route.from(SmithyUri.of("https://EXAMPLE.COM/path"));

        assertEquals("example.com", route.host());
    }

    @Test
    void fromUriIgnoresPathAndQuery() {
        var route1 = Route.from(SmithyUri.of("https://example.com/users?id=1"));
        var route2 = Route.from(SmithyUri.of("https://example.com/posts?id=2"));

        assertEquals(route1, route2);
    }

    @Test
    void fromUriThrowsOnMissingScheme() {
        assertThrows(IllegalArgumentException.class,
                () -> Route.from(SmithyUri.of("example.com/path")));
    }

    @Test
    void fromUriThrowsOnMissingHost() {
        assertThrows(IllegalArgumentException.class,
                () -> Route.from(SmithyUri.of("http:///path")));
    }

    @Test
    void directThrowsOnInvalidScheme() {
        assertThrows(IllegalArgumentException.class,
                () -> Route.direct("ftp", "example.com", 21));
    }

    @Test
    void directThrowsOnInvalidPort() {
        assertThrows(IllegalArgumentException.class,
                () -> Route.direct("http", "example.com", 0));
        assertThrows(IllegalArgumentException.class,
                () -> Route.direct("http", "example.com", 70000));
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
    void withProxyCreatesNewRouteWithProxy() {
        var route = Route.direct("https", "example.com", 443);
        var proxy = new ProxyConfiguration(SmithyUri.of("http://proxy:8080"), ProxyConfiguration.ProxyType.HTTP);
        var proxied = route.withProxy(proxy);

        assertFalse(route.usesProxy());
        assertTrue(proxied.usesProxy());
        assertEquals(route.host(), proxied.host());
    }

    @Test
    void withoutProxyCreatesNewRouteWithoutProxy() {
        var proxy = new ProxyConfiguration(SmithyUri.of("http://proxy:8080"), ProxyConfiguration.ProxyType.HTTP);
        var route = Route.viaProxy("https", "example.com", 443, proxy);
        var direct = route.withoutProxy();

        assertTrue(route.usesProxy());
        assertFalse(direct.usesProxy());
    }
}
