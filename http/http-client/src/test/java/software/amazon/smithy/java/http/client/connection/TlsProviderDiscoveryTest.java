/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link TlsProvider} ServiceLoader discovery and {@code smithy-java.tls-provider} opt-in. Two
 * test providers are registered via {@code META-INF/services} in the test source set:
 * {@link AvailableTestTlsProvider} and {@link UnavailableTestTlsProvider}.
 */
class TlsProviderDiscoveryTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty(TlsProvider.PROVIDER_PROPERTY);
    }

    @Test
    void fromSystemPropertyNullWhenUnset() {
        System.clearProperty(TlsProvider.PROVIDER_PROPERTY);
        assertNull(TlsProvider.fromSystemProperty());
    }

    @Test
    void fromSystemPropertyNullWhenBlank() {
        System.setProperty(TlsProvider.PROVIDER_PROPERTY, "   ");
        assertNull(TlsProvider.fromSystemProperty());
    }

    @Test
    void fromSystemPropertyResolvesRegisteredProvider() {
        System.setProperty(TlsProvider.PROVIDER_PROPERTY, AvailableTestTlsProvider.class.getName());
        assertThat(TlsProvider.fromSystemProperty(), instanceOf(AvailableTestTlsProvider.class));
    }

    @Test
    void byClassNameFindsAvailableProvider() {
        TlsProvider provider = TlsProvider.byClassName(
                AvailableTestTlsProvider.class.getName(),
                getClass().getClassLoader());
        assertThat(provider, instanceOf(AvailableTestTlsProvider.class));
    }

    @Test
    void byClassNameFallsBackWhenLoaderNull() {
        // null loader -> uses TlsProvider's own loader (the GraalVM native-image case, where the
        // thread-context loader may be null). Discovery must still succeed.
        TlsProvider provider = TlsProvider.byClassName(AvailableTestTlsProvider.class.getName(), null);
        assertThat(provider, instanceOf(AvailableTestTlsProvider.class));
    }

    @Test
    void discoveryWorksWithNullContextClassLoader() {
        // Simulate a runtime with no thread-context loader (as can happen under native-image): selection
        // by system property must still resolve the registered provider via the interface's loader.
        Thread current = Thread.currentThread();
        ClassLoader saved = current.getContextClassLoader();
        try {
            current.setContextClassLoader(null);
            System.setProperty(TlsProvider.PROVIDER_PROPERTY, AvailableTestTlsProvider.class.getName());
            assertThat(TlsProvider.fromSystemProperty(), instanceOf(AvailableTestTlsProvider.class));
        } finally {
            current.setContextClassLoader(saved);
        }
    }

    @Test
    void byClassNameRejectsUnavailableProvider() {
        var ex = assertThrows(IllegalStateException.class,
                () -> TlsProvider.byClassName(
                        UnavailableTestTlsProvider.class.getName(),
                        getClass().getClassLoader()));
        assertThat(ex.getMessage(), containsString("unavailable"));
    }

    @Test
    void byClassNameErrorsOnUnknownProvider() {
        var ex = assertThrows(IllegalStateException.class,
                () -> TlsProvider.byClassName(
                        "com.example.NoSuchProvider",
                        getClass().getClassLoader()));
        // The message must name the missing class and list what was actually discovered, to aid debugging.
        assertThat(ex.getMessage(), containsString("com.example.NoSuchProvider"));
        assertThat(ex.getMessage(), containsString(AvailableTestTlsProvider.class.getName()));
    }

    @Test
    void unavailableProviderReportsFalse() {
        assertFalse(new UnavailableTestTlsProvider().isAvailable());
        assertTrue(new AvailableTestTlsProvider().isAvailable());
    }

    @Test
    void supportsEpollDefaultsFalseForCustomProviders() {
        // A custom provider must NOT silently opt into the internal epoll substrate (which would hand it
        // a null socket). The default protects external/self-I/O providers.
        assertFalse(new AvailableTestTlsProvider().supportsEpoll());
    }

    @Test
    void builtInEngineProvidersSupportEpoll() {
        // Engine-based providers consume the internal epoll channel via SslEngineTransports.
        assertTrue(JdkTlsProvider.create().supportsEpoll());
    }

    @Test
    void defaultProviderPropertyName() {
        // Lock the documented property name so it cannot change silently.
        assertEquals("smithy-java.tls-provider", TlsProvider.PROVIDER_PROPERTY);
    }
}
