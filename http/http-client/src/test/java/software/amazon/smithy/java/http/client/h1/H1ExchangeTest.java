/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.RequestOptions;
import software.amazon.smithy.java.http.client.connection.ConnectionTransport;
import software.amazon.smithy.java.http.client.connection.Route;
import software.amazon.smithy.java.io.datastream.DataStream;
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

    private HttpRequest headRequest() {
        return HttpRequest.create()
                .setMethod("HEAD")
                .setUri(SmithyUri.of("https://example.com/test"));
    }

    @Test
    void connectionCloseDisablesKeepAlive() throws IOException {
        var conn = connection(
                "HTTP/1.1 200 OK\r\n"
                        + "Connection: close\r\n"
                        + "Content-Length: 0\r\n"
                        + "\r\n");
        var exchange = conn.newExchange(getRequest(), RequestOptions.defaults());
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
        var exchange = conn.newExchange(getRequest(), RequestOptions.defaults());
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
        var exchange = conn.newExchange(getRequest(), RequestOptions.defaults());
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
        var exchange = conn.newExchange(getRequest(), RequestOptions.defaults());
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
        var exchange = conn.newExchange(getRequest(), RequestOptions.defaults());

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
        var exchange = conn.newExchange(getRequest(), RequestOptions.defaults());
        var body = new String(exchange.responseBody().readAllBytes());

        assertEquals("hello", body);
        exchange.close();
    }

    @Test
    void transfersFixedLengthResponseBodyAndReusesConnection() throws IOException {
        var conn = connection(
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Length: 5\r\n"
                        + "\r\n"
                        + "hello"
                        + "HTTP/1.1 204 No Content\r\n"
                        + "Content-Length: 0\r\n"
                        + "\r\n");
        var first = conn.newExchange(getRequest(), RequestOptions.defaults());
        var out = new ByteArrayOutputStream();

        assertEquals(5, first.responseBody().transferTo(out));
        assertEquals("hello", out.toString(java.nio.charset.StandardCharsets.US_ASCII));

        var second = conn.newExchange(getRequest(), RequestOptions.defaults());
        assertEquals(204, second.responseStatusCode());
        second.close();
    }

    @Test
    void fixedLengthTransferToThrowsOnPrematureEof() throws IOException {
        var conn = connection(
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Length: 5\r\n"
                        + "\r\n"
                        + "he");
        var exchange = conn.newExchange(getRequest(), RequestOptions.defaults());

        assertThrows(IOException.class, () -> exchange.responseBody().transferTo(OutputStream.nullOutputStream()));
    }

    @Test
    void acceptsMatchingDuplicateContentLengthHeaders() throws IOException {
        var conn = connection(
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Length: 5\r\n"
                        + "Content-Length: 5\r\n"
                        + "\r\n"
                        + "hello");
        var exchange = conn.newExchange(getRequest(), RequestOptions.defaults());

        assertEquals(5, exchange.responseContentLength());
        assertEquals("hello", new String(exchange.responseBody().readAllBytes()));
        exchange.close();
    }

    @Test
    void rejectsConflictingDuplicateContentLengthHeaders() throws IOException {
        var conn = connection(
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Length: 5\r\n"
                        + "Content-Length: 6\r\n"
                        + "\r\n"
                        + "hello");
        var exchange = conn.newExchange(getRequest(), RequestOptions.defaults());

        assertThrows(IOException.class, exchange::responseHeaders);
    }

    @Test
    void readsFixedLengthResponseBodyAsChannel() throws IOException {
        var conn = connection(
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Length: 5\r\n"
                        + "\r\n"
                        + "hello");
        var exchange = conn.newExchange(getRequest(), RequestOptions.defaults());
        var out = new ByteArrayOutputStream();

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

        var first = conn.newExchange(getRequest(), RequestOptions.defaults());
        var channel = first.responseBodyChannel();
        ByteBuffer dst = ByteBuffer.allocate(16);
        assertEquals(5, channel.read(dst));
        assertEquals(-1, channel.read(dst.clear()));

        var second = conn.newExchange(getRequest(), RequestOptions.defaults());
        assertEquals(204, second.responseStatusCode());
        second.close();
    }

    @Test
    void responseBodyChannelCloseDrainsOnlyRemainingFixedLengthBytes() throws IOException {
        var conn = connection(
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Length: 5\r\n"
                        + "\r\n"
                        + "hello"
                        + "HTTP/1.1 204 No Content\r\n"
                        + "Content-Length: 0\r\n"
                        + "\r\n");

        var first = conn.newExchange(getRequest(), RequestOptions.defaults());
        var channel = first.responseBodyChannel();
        ByteBuffer dst = ByteBuffer.allocate(2);
        assertEquals(2, channel.read(dst));
        channel.close();

        var second = conn.newExchange(getRequest(), RequestOptions.defaults());
        assertEquals(204, second.responseStatusCode());
        second.close();
    }

    @Test
    void responseBodyChannelThrowsOnPrematureEof() throws IOException {
        var conn = connection(
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Length: 5\r\n"
                        + "\r\n"
                        + "he");

        var exchange = conn.newExchange(getRequest(), RequestOptions.defaults());
        var channel = exchange.responseBodyChannel();
        ByteBuffer dst = ByteBuffer.allocate(16);
        assertEquals(2, channel.read(dst));

        assertThrows(IOException.class, () -> channel.read(dst.clear()));
        assertThrows(IOException.class, channel::close);
    }

    @Test
    void exposesCachedContentHeaders() throws IOException {
        var conn = connection(
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/plain\r\n"
                        + "Content-Length: 5\r\n"
                        + "\r\n"
                        + "hello");
        var exchange = conn.newExchange(getRequest(), RequestOptions.defaults());

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

        var first = conn.newExchange(getRequest(), RequestOptions.defaults());
        assertEquals(200, first.responseStatusCode());
        first.discardResponseBody();

        var second = conn.newExchange(getRequest(), RequestOptions.defaults());
        assertEquals(204, second.responseStatusCode());
        second.close();
    }

    @Test
    void discardsChunkedBodyWithoutOpeningResponseStream() throws IOException {
        var conn = connection(
                "HTTP/1.1 200 OK\r\n"
                        + "Transfer-Encoding: chunked\r\n"
                        + "\r\n"
                        + "5;ignored=extension\r\n"
                        + "hello\r\n"
                        + "0\r\n"
                        + "Trailer: value\r\n"
                        + "\r\n"
                        + "HTTP/1.1 204 No Content\r\n"
                        + "Content-Length: 0\r\n"
                        + "\r\n");

        var first = conn.newExchange(getRequest(), RequestOptions.defaults());
        assertEquals(200, first.responseStatusCode());
        first.discardResponseBody();

        var second = conn.newExchange(getRequest(), RequestOptions.defaults());
        assertEquals(204, second.responseStatusCode());
        second.close();
    }

    @Test
    void headResponseIgnoresContentLengthWhenCreatingBody() throws IOException {
        var conn = connection(
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Length: 5\r\n"
                        + "\r\n"
                        + "HTTP/1.1 204 No Content\r\n"
                        + "Content-Length: 0\r\n"
                        + "\r\n");

        var first = conn.newExchange(headRequest(), RequestOptions.defaults());
        assertEquals(-1, first.responseBody().read());

        var second = conn.newExchange(getRequest(), RequestOptions.defaults());
        assertEquals(204, second.responseStatusCode());
        second.close();
    }

    @Test
    void noBodyStatusIgnoresContentLengthWhenDiscarding() throws IOException {
        var conn = connection(
                "HTTP/1.1 204 No Content\r\n"
                        + "Content-Length: 5\r\n"
                        + "\r\n"
                        + "HTTP/1.1 200 OK\r\n"
                        + "Content-Length: 0\r\n"
                        + "\r\n");

        var first = conn.newExchange(getRequest(), RequestOptions.defaults());
        assertEquals(204, first.responseStatusCode());
        first.discardResponseBody();

        var second = conn.newExchange(getRequest(), RequestOptions.defaults());
        assertEquals(200, second.responseStatusCode());
        second.close();
    }

    @Test
    void expectContinueFinalResponseSkipsRequestBodyAndReturnsResponse() throws IOException {
        var socket = new H1ConnectionTest.FakeSocket(
                "HTTP/1.1 413 Payload Too Large\r\n"
                        + "Content-Length: 0\r\n"
                        + "\r\n");
        var conn = new H1Connection(ConnectionTransport.of(socket), TEST_ROUTE, READ_TIMEOUT);
        var request = HttpRequest.create()
                .setMethod("POST")
                .setUri(SmithyUri.of("https://example.com/test"))
                .setHeaders(HttpHeaders.of(Map.of("Expect", List.of("100-continue"))))
                .setBody(DataStream.ofString("request-body"));

        var exchange = conn.newExchange(request, RequestOptions.defaults());
        exchange.writeRequestBody(request.body());

        assertEquals(413, exchange.responseStatusCode());
        assertFalse(socket.outputString().contains("request-body"));
        exchange.close();
    }

    @Test
    void expectContinueOverrideTrueAddsHeaderAndWaitsForContinue() throws IOException {
        // Server replies 100 Continue, then the final 200 once the body is sent.
        var socket = new H1ConnectionTest.FakeSocket(
                "HTTP/1.1 100 Continue\r\n"
                        + "\r\n"
                        + "HTTP/1.1 200 OK\r\n"
                        + "Content-Length: 0\r\n"
                        + "\r\n");
        var conn = new H1Connection(ConnectionTransport.of(socket), TEST_ROUTE, READ_TIMEOUT);
        // Request does NOT carry an Expect header; the override forces it.
        var request = HttpRequest.create()
                .setMethod("POST")
                .setUri(SmithyUri.of("https://example.com/test"))
                .setBody(DataStream.ofString("request-body"));
        var options = RequestOptions.builder().expectContinue(true).build();

        var exchange = conn.newExchange(request, options);
        exchange.writeRequestBody(request.body());

        assertEquals(200, exchange.responseStatusCode());
        var written = socket.outputString();
        assertTrue(written.toLowerCase().contains("expect: 100-continue"), "Expect header should be on the wire");
        // 100 Continue was received, so the body is sent.
        assertTrue(written.contains("request-body"));
        exchange.close();
    }

    @Test
    void expectContinueOverrideFalseStripsHeaderAndSkipsHandshake() throws IOException {
        // Only a final response is queued: if the client wrongly waited for 100 Continue it would
        // consume this 200 as the interim response and misbehave. It must send the body immediately.
        var socket = new H1ConnectionTest.FakeSocket(
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Length: 0\r\n"
                        + "\r\n");
        var conn = new H1Connection(ConnectionTransport.of(socket), TEST_ROUTE, READ_TIMEOUT);
        // Request carries Expect: 100-continue; the override suppresses it.
        var request = HttpRequest.create()
                .setMethod("POST")
                .setUri(SmithyUri.of("https://example.com/test"))
                .setHeaders(HttpHeaders.of(Map.of("Expect", List.of("100-continue"))))
                .setBody(DataStream.ofString("request-body"));
        var options = RequestOptions.builder().expectContinue(false).build();

        var exchange = conn.newExchange(request, options);
        exchange.writeRequestBody(request.body());

        assertEquals(200, exchange.responseStatusCode());
        var written = socket.outputString();
        assertFalse(written.toLowerCase().contains("expect:"), "Expect header should be suppressed");
        assertTrue(written.contains("request-body"));
        exchange.close();
    }

    @Test
    void unwrapsIoExceptionFromHeaderConsumer() throws IOException {
        var socket = new H1ConnectionTest.FakeSocket("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n") {
            @Override
            public OutputStream getOutputStream() {
                return new OutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        throw new IOException("boom");
                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        throw new IOException("boom");
                    }
                };
            }
        };
        var conn = new H1Connection(ConnectionTransport.of(socket), TEST_ROUTE, READ_TIMEOUT);
        var request = HttpRequest.create()
                .setMethod("GET")
                .setUri(SmithyUri.of("https://example.com/test"))
                .setHeaders(HttpHeaders.of(Map.of("X-Big", List.of("x".repeat(9000)))));

        var thrown = assertThrows(IOException.class, () -> conn.newExchange(request, RequestOptions.defaults()));
        assertEquals("boom", thrown.getMessage());
    }

    @Test
    void writesRawPathAndQueryInRequestLine() throws IOException {
        var socket = new H1ConnectionTest.FakeSocket("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
        var conn = new H1Connection(ConnectionTransport.of(socket), TEST_ROUTE, READ_TIMEOUT);
        var request = HttpRequest.create()
                .setMethod("GET")
                .setUri(SmithyUri.of("https://example.com/a%2Fb?prefix=x%2Fy"));

        var exchange = conn.newExchange(request, RequestOptions.defaults());
        exchange.responseStatusCode();

        assertTrue(socket.outputString().startsWith("GET /a%2Fb?prefix=x%2Fy HTTP/1.1\r\n"));
        exchange.close();
    }

}
