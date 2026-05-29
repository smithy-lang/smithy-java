/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.Route;
import software.amazon.smithy.java.http.client.connection.ConnectionTransport;
import software.amazon.smithy.java.io.uri.SmithyUri;

class H1ExchangeTest {

    private static final Route TEST_ROUTE = Route.direct("https", "example.com", 443);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private H1Connection connection(String response) throws IOException {
        var socket = new H1ConnectionTest.FakeSocket(response);
        return new H1Connection(ConnectionTransport.of(socket), TEST_ROUTE, READ_TIMEOUT);
    }

    private HttpRequest getRequest() {
        return HttpRequest.create()
                .setMethod("GET")
                .setUri(SmithyUri.of("https://example.com/test"));
    }

    @Test
    void connectionCloseDisablesKeepAlive() throws IOException {
        var conn = connection(
                "HTTP/1.1 200 OK\r\n"
                        + "Connection: close\r\n"
                        + "Content-Length: 0\r\n"
                        + "\r\n");
        var exchange = conn.newExchange(getRequest());
        exchange.responseHeaders();

        assertFalse(conn.isKeepAlive(), "Connection: close should disable keep-alive");
        exchange.close();
    }

    @Test
    void connectionKeepAliveWithoutCloseKeepsAlive() throws IOException {
        var conn = connection(
                "HTTP/1.0 200 OK\r\n"
                        + "Connection: keep-alive\r\n"
                        + "Content-Length: 0\r\n"
                        + "\r\n");
        var exchange = conn.newExchange(getRequest());
        exchange.responseHeaders();

        assertTrue(conn.isKeepAlive(),
                "Connection: keep-alive should enable keep-alive for HTTP/1.0");
        exchange.close();
    }

    @Test
    void http10DefaultsToConnectionClose() throws IOException {
        var conn = connection(
                "HTTP/1.0 200 OK\r\n"
                        + "Content-Length: 0\r\n"
                        + "\r\n");
        var exchange = conn.newExchange(getRequest());
        exchange.responseHeaders();

        assertFalse(conn.isKeepAlive(),
                "HTTP/1.0 should default to Connection: close");
        exchange.close();
    }

    @Test
    void http11DefaultsToKeepAlive() throws IOException {
        var conn = connection(
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Length: 0\r\n"
                        + "\r\n");
        var exchange = conn.newExchange(getRequest());
        exchange.responseHeaders();

        assertTrue(conn.isKeepAlive(),
                "HTTP/1.1 should default to keep-alive");
        exchange.close();
    }

    @Test
    void parsesResponseVersion() throws IOException {
        var conn = connection(
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Length: 0\r\n"
                        + "\r\n");
        var exchange = conn.newExchange(getRequest());

        assertEquals(HttpVersion.HTTP_1_1, exchange.responseVersion());
        exchange.close();
    }

    @Test
    void parsesResponseBody() throws IOException {
        var conn = connection(
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Length: 5\r\n"
                        + "\r\n"
                        + "hello");
        var exchange = conn.newExchange(getRequest());
        var body = new String(exchange.responseBody().readAllBytes());

        assertEquals("hello", body);
        exchange.close();
    }

    @Test
    void readsFixedLengthResponseBodyAsChannel() throws IOException {
        var conn = connection(
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Length: 5\r\n"
                        + "\r\n"
                        + "hello");
        var exchange = conn.newExchange(getRequest());
        var out = new java.io.ByteArrayOutputStream();

        Channels.newInputStream(exchange.responseBodyChannel()).transferTo(out);

        assertEquals("hello", out.toString(java.nio.charset.StandardCharsets.US_ASCII));
    }

    @Test
    void responseBodyChannelReleasesConnectionAtEof() throws IOException {
        var conn = connection(
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Length: 5\r\n"
                        + "\r\n"
                        + "hello"
                        + "HTTP/1.1 204 No Content\r\n"
                        + "Content-Length: 0\r\n"
                        + "\r\n");

        var first = conn.newExchange(getRequest());
        var channel = first.responseBodyChannel();
        ByteBuffer dst = ByteBuffer.allocate(16);
        assertEquals(5, channel.read(dst));
        assertEquals(-1, channel.read(dst.clear()));

        var second = conn.newExchange(getRequest());
        assertEquals(204, second.responseStatusCode());
        second.close();
    }

    @Test
    void exposesCachedContentHeaders() throws IOException {
        var conn = connection(
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/plain\r\n"
                        + "Content-Length: 5\r\n"
                        + "\r\n"
                        + "hello");
        var exchange = conn.newExchange(getRequest());

        assertEquals("text/plain", exchange.responseContentType());
        assertEquals(5, exchange.responseContentLength());
        exchange.close();
    }

    @Test
    void discardsFixedLengthBodyWithoutOpeningResponseStream() throws IOException {
        var conn = connection(
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Length: 5\r\n"
                        + "\r\n"
                        + "hello"
                        + "HTTP/1.1 204 No Content\r\n"
                        + "Content-Length: 0\r\n"
                        + "\r\n");

        var first = conn.newExchange(getRequest());
        assertEquals(200, first.responseStatusCode());
        first.discardResponseBody();

        var second = conn.newExchange(getRequest());
        assertEquals(204, second.responseStatusCode());
        second.close();
    }

    @Test
    void writesRawPathAndQueryInRequestLine() throws IOException {
        var socket = new H1ConnectionTest.FakeSocket("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
        var conn = new H1Connection(ConnectionTransport.of(socket), TEST_ROUTE, READ_TIMEOUT);
        var request = HttpRequest.create()
                .setMethod("GET")
                .setUri(SmithyUri.of("https://example.com/a%2Fb?prefix=x%2Fy"));

        var exchange = conn.newExchange(request);
        exchange.responseStatusCode();

        assertTrue(socket.outputString().startsWith("GET /a%2Fb?prefix=x%2Fy HTTP/1.1\r\n"));
        exchange.close();
    }

}
