/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the TLS-provider seam: {@link JdkTlsProvider}, {@link TlsConnectionContext}, and
 * {@link SslEngineTransports} argument handling. The end-to-end JDK TLS path (a real handshake through
 * these types) is covered by the {@code TlsValidationTest} integration test, which uses properly-issued
 * certificates; these unit tests cover the construction/configuration logic without a live socket.
 */
class JdkTlsProviderTest {

    @Test
    void builderProducesUsableProvider() {
        // No SSLContext supplied -> resolves to the default; the provider is available.
        JdkTlsProvider provider = JdkTlsProvider.create();
        assertTrue(provider.isAvailable());
        assertThat(JdkTlsProvider.builder().build(), notNullValue());
    }

    @Test
    void connectionContextRoundTrips() {
        var ctx = TlsConnectionContext.builder()
                .host("example.com")
                .port(8443)
                .alpnProtocols(List.of("h2", "http/1.1"))
                .negotiationTimeoutMillis(1234)
                .readTimeoutMillis(5678)
                .tlsReadBufferSize(4096)
                .tlsWriteBufferSize(2048)
                .build();
        assertEquals("example.com", ctx.host());
        assertEquals(8443, ctx.port());
        assertEquals(List.of("h2", "http/1.1"), ctx.alpnProtocols());
        assertEquals(1234, ctx.negotiationTimeoutMillis());
        assertEquals(5678, ctx.readTimeoutMillis());
        assertEquals(4096, ctx.tlsReadBufferSize());
        assertEquals(2048, ctx.tlsWriteBufferSize());
    }

    @Test
    void connectionContextAlpnNeverNull() {
        // ALPN defaults to an empty (never null) list, and an explicit null is normalized.
        assertEquals(List.of(), TlsConnectionContext.builder().build().alpnProtocols());
        assertEquals(List.of(), TlsConnectionContext.builder().alpnProtocols(null).build().alpnProtocols());
    }

    @Test
    void connectionContextAlpnIsDefensivelyCopied() {
        var mutable = new ArrayList<>(List.of("h2"));
        var ctx = TlsConnectionContext.builder().alpnProtocols(mutable).build();
        mutable.add("http/1.1");
        assertEquals(List.of("h2"), ctx.alpnProtocols(), "context must not reflect later mutation of the source list");
    }

    @Test
    void sslEngineTransportsRejectsNullEngine() {
        // No engine and no socket/channel -> fails fast rather than NPEing deep in the transport.
        assertThrows(Exception.class,
                () -> SslEngineTransports.connect(TlsConnectionContext.builder().host("h").build(), null, null));
    }

    @Test
    void tlsProviderIsAFunctionalInterface() {
        // The SPI is a single-method type: a lambda is a complete provider. (Compile-time guarantee that
        // an out-of-module provider needs only connect().)
        AtomicInteger calls = new AtomicInteger();
        TlsProvider lambda = ctx -> {
            calls.incrementAndGet();
            throw new IOException("sentinel");
        };
        assertThrows(IOException.class,
                () -> lambda.connect(TlsConnectionContext.builder().host("h").build()));
        assertEquals(1, calls.get());
        assertTrue(lambda.isAvailable(), "isAvailable defaults to true");
    }
}
