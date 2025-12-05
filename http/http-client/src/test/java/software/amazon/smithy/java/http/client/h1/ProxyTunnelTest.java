/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.client.HttpCredentials;

class ProxyTunnelTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void establishSuccessfulTunnel() throws IOException {
        var socket = new FakeSocket("HTTP/1.1 200 Connection Established\r\n\r\n");
        var result = ProxyTunnel.establish(socket, "example.com", 443, null, TIMEOUT);

        assertNotNull(result.socket());
        assertEquals(200, result.statusCode());

        var request = socket.getRequest();

        // CONNECT uses authority-form: CONNECT host:port HTTP/1.1
        assertTrue(request.startsWith("CONNECT example.com:443 HTTP/1.1\r\n"), "Request was: " + request);
    }

    @Test
    void tunnelFailsWithForbidden() throws IOException {
        var socket = new FakeSocket("HTTP/1.1 403 Forbidden\r\n\r\n");
        var result = ProxyTunnel.establish(socket, "example.com", 443, null, TIMEOUT);

        assertNull(result.socket());
        assertEquals(403, result.statusCode());
    }

    @Test
    void tunnelWithBasicAuth() throws IOException {
        var socket = new FakeSocket("HTTP/1.1 200 Connection Established\r\n\r\n");
        var creds = new HttpCredentials.Basic("user", "pass", true);
        var result = ProxyTunnel.establish(socket, "example.com", 443, creds, TIMEOUT);

        assertNotNull(result.socket());
        assertEquals(200, result.statusCode());

        var request = socket.getRequest().toLowerCase();
        assertTrue(request.contains("proxy-authorization: basic"), "Request was: " + socket.getRequest());
    }

    @Test
    void tunnelAuthFailsAfter407() throws IOException {
        var socket = new FakeSocket("HTTP/1.1 407 Proxy Authentication Required\r\n\r\n");
        var creds = new HttpCredentials.Basic("user", "pass", true);
        var result = ProxyTunnel.establish(socket, "example.com", 443, creds, TIMEOUT);

        assertNull(result.socket());
        assertEquals(407, result.statusCode());
    }

    @Test
    void tunnelWithMultiRoundAuth() throws IOException {
        var socket = new FakeSocket(
                "HTTP/1.1 407 Proxy Authentication Required\r\n\r\n" +
                        "HTTP/1.1 200 Connection Established\r\n\r\n");
        var creds = new MultiRoundCredentials();
        var result = ProxyTunnel.establish(socket, "example.com", 443, creds, TIMEOUT);

        assertNotNull(result.socket());
        assertEquals(200, result.statusCode());
        assertEquals(2, creds.callCount);
    }

    @Test
    void tunnelWithoutCredentialsOn407() throws IOException {
        var socket = new FakeSocket("HTTP/1.1 407 Proxy Authentication Required\r\n\r\n");
        var result = ProxyTunnel.establish(socket, "example.com", 443, null, TIMEOUT);

        assertNull(result.socket());
        assertEquals(407, result.statusCode());
    }

    @Test
    void tunnelIncludesHostHeader() throws IOException {
        var socket = new FakeSocket("HTTP/1.1 200 Connection Established\r\n\r\n");
        ProxyTunnel.establish(socket, "example.com", 443, null, TIMEOUT);
        var request = socket.getRequest();

        // Host header is auto-generated from URI, check for lowercase
        assertTrue(request.contains("host: example.com") || request.contains("Host: example.com"),
                "Request was: " + request);
    }

    @Test
    void tunnelIncludesProxyConnectionHeader() throws IOException {
        var socket = new FakeSocket("HTTP/1.1 200 Connection Established\r\n\r\n");
        ProxyTunnel.establish(socket, "example.com", 443, null, TIMEOUT);
        var request = socket.getRequest();

        // Check for the header (case may vary)
        assertTrue(request.toLowerCase().contains("proxy-connection:"),
                "Request was: " + request);
    }

    static class MultiRoundCredentials implements HttpCredentials {
        int callCount = 0;

        @Override
        public boolean authenticate(HttpRequest.Builder request, HttpResponse priorResponse) {
            callCount++;
            request.withAddedHeader("Proxy-Authorization", "Round" + callCount);
            return true;
        }
    }

    static final class FakeSocket extends Socket {
        private final ByteArrayInputStream in;
        private final ByteArrayOutputStream out;
        private final InetAddress address;

        FakeSocket(String response) throws IOException {
            this.in = new ByteArrayInputStream(response.getBytes(StandardCharsets.US_ASCII));
            this.out = new ByteArrayOutputStream();
            this.address = InetAddress.getByName("127.0.0.1");
        }

        @Override
        public InputStream getInputStream() {
            return in;
        }

        @Override
        public OutputStream getOutputStream() {
            return out;
        }

        @Override
        public InetAddress getInetAddress() {
            return address;
        }

        @Override
        public int getPort() {
            return 8080;
        }

        @Override
        public SocketAddress getRemoteSocketAddress() {
            return null;
        }

        @Override
        public void setSoTimeout(int timeout) {}

        @Override
        public int getSoTimeout() {
            return 0;
        }

        String getRequest() {
            return out.toString(StandardCharsets.US_ASCII);
        }
    }
}
