/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.Route;

class H1ConnectionTest {

    private static final Route TEST_ROUTE = Route.direct("https", "example.com", 443);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void createsConnectionSuccessfully() throws IOException {
        var socket = new FakeSocket("");
        var connection = new H1Connection(socket, TEST_ROUTE, READ_TIMEOUT);

        assertTrue(connection.isActive());
        assertEquals(HttpVersion.HTTP_1_1, connection.httpVersion());
        assertEquals(TEST_ROUTE, connection.route());
    }

    @Test
    void createsExchangeSuccessfully() throws IOException {
        var socket = new FakeSocket("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
        var connection = new H1Connection(socket, TEST_ROUTE, READ_TIMEOUT);
        var request = HttpRequest.builder()
                .method("GET")
                .uri(URI.create("https://example.com/test"))
                .build();
        var exchange = connection.newExchange(request);

        assertNotNull(exchange);
        exchange.close();
    }

    @Test
    void throwsOnConcurrentExchange() throws IOException {
        var socket = new FakeSocket("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
        var connection = new H1Connection(socket, TEST_ROUTE, READ_TIMEOUT);
        var request = HttpRequest.builder()
                .method("GET")
                .uri(URI.create("https://example.com/test"))
                .build();

        connection.newExchange(request);

        assertThrows(IOException.class, () -> connection.newExchange(request));
    }

    @Test
    void throwsOnClosedConnection() throws IOException {
        var socket = new FakeSocket("");
        var connection = new H1Connection(socket, TEST_ROUTE, READ_TIMEOUT);
        var request = HttpRequest.builder()
                .method("GET")
                .uri(URI.create("https://example.com/test"))
                .build();

        connection.close();

        assertThrows(IOException.class, () -> connection.newExchange(request));
    }

    @Test
    void isActiveReturnsFalseAfterClose() throws IOException {
        var socket = new FakeSocket("");
        var connection = new H1Connection(socket, TEST_ROUTE, READ_TIMEOUT);
        connection.close();

        assertFalse(connection.isActive());
    }

    @Test
    void isActiveReturnsFalseWhenKeepAliveDisabled() throws IOException {
        var socket = new FakeSocket("");
        var connection = new H1Connection(socket, TEST_ROUTE, READ_TIMEOUT);
        connection.setKeepAlive(false);

        assertFalse(connection.isActive());
    }

    @Test
    void validateForReuseReturnsTrueForHealthyConnection() throws IOException {
        var socket = new FakeSocket("");
        var connection = new H1Connection(socket, TEST_ROUTE, READ_TIMEOUT);

        assertTrue(connection.validateForReuse());
    }

    @Test
    void validateForReuseReturnsFalseWhenInactive() throws IOException {
        var socket = new FakeSocket("");
        var connection = new H1Connection(socket, TEST_ROUTE, READ_TIMEOUT);
        connection.markInactive();

        assertFalse(connection.validateForReuse());
    }

    @Test
    void validateForReuseReturnsFalseWhenKeepAliveDisabled() throws IOException {
        var socket = new FakeSocket("");
        var connection = new H1Connection(socket, TEST_ROUTE, READ_TIMEOUT);
        connection.setKeepAlive(false);

        assertFalse(connection.validateForReuse());
    }

    @Test
    void validateForReuseReturnsFalseWhenSocketClosed() throws IOException {
        var socket = new FakeSocket("");
        var connection = new H1Connection(socket, TEST_ROUTE, READ_TIMEOUT);
        socket.close();

        assertFalse(connection.validateForReuse());
        assertFalse(connection.isActive());
    }

    @Test
    void validateForReuseReturnsFalseWhenDataAvailableOnIdleConnection() throws IOException {
        var socket = new FakeSocket("HTTP/1.1 200 OK\r\n");
        var connection = new H1Connection(socket, TEST_ROUTE, READ_TIMEOUT);

        assertFalse(connection.validateForReuse());
        assertFalse(connection.isActive());
    }

    @Test
    void validateForReuseReturnsFalseWhenAvailableThrows() throws IOException {
        var socket = new FailingAvailableSocket();
        var connection = new H1Connection(socket, TEST_ROUTE, READ_TIMEOUT);

        assertFalse(connection.validateForReuse());
        assertFalse(connection.isActive());
    }

    @Test
    void sslSessionReturnsNullForPlainSocket() throws IOException {
        var socket = new FakeSocket("");
        var connection = new H1Connection(socket, TEST_ROUTE, READ_TIMEOUT);

        assertNull(connection.sslSession());
    }

    @Test
    void negotiatedProtocolReturnsNullForPlainSocket() throws IOException {
        var socket = new FakeSocket("");
        var connection = new H1Connection(socket, TEST_ROUTE, READ_TIMEOUT);

        assertNull(connection.negotiatedProtocol());
    }

    @Test
    void setAndGetSocketTimeout() throws IOException {
        var socket = new FakeSocket("");
        var connection = new H1Connection(socket, TEST_ROUTE, READ_TIMEOUT);
        connection.setSocketTimeout(1000);

        assertEquals(1000, connection.getSocketTimeout());
    }

    @Test
    void keepAliveDefaultsToTrue() throws IOException {
        var socket = new FakeSocket("");
        var connection = new H1Connection(socket, TEST_ROUTE, READ_TIMEOUT);

        assertTrue(connection.isKeepAlive());
    }

    @Test
    void markInactiveSetsConnectionInactive() throws IOException {
        var socket = new FakeSocket("");
        var connection = new H1Connection(socket, TEST_ROUTE, READ_TIMEOUT);
        connection.markInactive();

        assertFalse(connection.isActive());
    }

    @Test
    void nullReadTimeoutDoesNotSetSocketTimeout() throws IOException {
        var socket = new FakeSocket("");
        var connection = new H1Connection(socket, TEST_ROUTE, null);

        assertEquals(0, connection.getSocketTimeout());
    }

    @Test
    void zeroReadTimeoutDoesNotSetSocketTimeout() throws IOException {
        var socket = new FakeSocket("");
        var connection = new H1Connection(socket, TEST_ROUTE, Duration.ZERO);

        assertEquals(0, connection.getSocketTimeout());
    }

    static class FakeSocket extends Socket {
        private final ByteArrayInputStream in;
        private final ByteArrayOutputStream out;
        private final InetAddress address;
        private int soTimeout = 0;
        private boolean closed = false;

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
            return 443;
        }

        @Override
        public void setSoTimeout(int timeout) {
            this.soTimeout = timeout;
        }

        @Override
        public int getSoTimeout() {
            return soTimeout;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public boolean isInputShutdown() {
            return closed;
        }

        @Override
        public boolean isOutputShutdown() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    static final class FailingAvailableSocket extends FakeSocket {
        FailingAvailableSocket() throws IOException {
            super("");
        }

        @Override
        public InputStream getInputStream() {
            return new InputStream() {
                @Override
                public int read() {
                    return -1;
                }

                @Override
                public int available() throws IOException {
                    throw new IOException("boom");
                }
            };
        }
    }
}
