/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TlsFixtureServerTest {

    private static final int BODY_BYTES = 70_000;

    private static ServerSocket listener;
    private static SSLContext clientContext;
    private static int port;

    @BeforeAll
    static void start() throws Exception {
        byte[] body = new byte[BODY_BYTES];
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) ('a' + i % 26);
        }
        byte[] head = ("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: " + BODY_BYTES
                + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] fixture = new byte[head.length + body.length];
        System.arraycopy(head, 0, fixture, 0, head.length);
        System.arraycopy(body, 0, fixture, head.length, body.length);
        var server = new FixtureServer();
        server.respondWith(fixture);
        listener = FixtureServer.bindTls(FixtureServer.selfSignedTls(),
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        port = listener.getLocalPort();
        Thread.ofPlatform().daemon().start(() -> {
            try {
                server.serve(listener);
            } catch (IOException e) {}
        });
        clientContext = trustAll();
    }

    @AfterAll
    static void stop() throws IOException {
        if (listener != null) {
            listener.close();
        }
    }

    @Test
    void servesTls13WithAlpnKeepAliveAndMultiRecordResponses() throws Exception {
        try (SSLSocket socket = connect()) {
            socket.startHandshake();
            assertThat(socket.getSession().getProtocol()).isEqualTo("TLSv1.3");
            assertThat(socket.getApplicationProtocol()).isEqualTo("http/1.1");
            InputStream in = socket.getInputStream();
            for (int i = 0; i < 3; i++) {
                send(socket, "GET /" + i + " HTTP/1.1\r\nHost: localhost\r\n\r\n");
                assertThat(readHead(in)).startsWith("HTTP/1.1 200 OK\r\n").contains("Content-Length: " + BODY_BYTES);
                byte[] body = in.readNBytes(BODY_BYTES);
                assertThat(body).hasSize(BODY_BYTES);
                assertThat(body[0]).isEqualTo((byte) 'a');
                assertThat(body[BODY_BYTES - 1]).isEqualTo((byte) ('a' + (BODY_BYTES - 1) % 26));
            }
            send(socket, "GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            assertThat(readHead(in)).startsWith("HTTP/1.1 200 OK");
            assertThat(in.readNBytes(BODY_BYTES)).hasSize(BODY_BYTES);
            assertThat(in.read()).as("server closed after Connection: close").isEqualTo(-1);
        }
    }

    @Test
    void uploadsSpanningSeveralRecordsAreDrained() throws Exception {
        try (SSLSocket socket = connect()) {
            String upload = "z".repeat(50_000);
            send(socket,
                    "PUT /object HTTP/1.1\r\nHost: localhost\r\nContent-Length: " + upload.length() + "\r\n\r\n"
                            + upload);
            InputStream in = socket.getInputStream();
            assertThat(readHead(in)).startsWith("HTTP/1.1 200 OK");
            assertThat(in.readNBytes(BODY_BYTES)).hasSize(BODY_BYTES);
            send(socket, "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n");
            assertThat(readHead(in)).startsWith("HTTP/1.1 200 OK");
            assertThat(in.readNBytes(BODY_BYTES)).hasSize(BODY_BYTES);
        }
    }

    /** The server's certificate is generated at startup and self-signed, so the test trusts whatever it presents. */
    private static SSLContext trustAll() throws Exception {
        var context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[] {new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                assertThat(chain[0].getSubjectX500Principal().getName()).isEqualTo("CN=localhost");
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }}, null);
        return context;
    }

    private static SSLSocket connect() throws IOException {
        var socket = (SSLSocket) clientContext.getSocketFactory().createSocket(InetAddress.getLoopbackAddress(), port);
        socket.setSoTimeout(10_000);
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setProtocols(new String[] {"TLSv1.3"});
        parameters.setApplicationProtocols(new String[] {"http/1.1"});
        socket.setSSLParameters(parameters);
        return socket;
    }

    private static void send(SSLSocket socket, String text) throws IOException {
        socket.getOutputStream().write(text.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

    private static String readHead(InputStream in) throws IOException {
        var sb = new StringBuilder();
        int c;
        int trailing = 0;
        while ((c = in.read()) >= 0) {
            sb.append((char) c);
            trailing =
                    (c == '\r' && (trailing == 0 || trailing == 2)) || (c == '\n' && (trailing == 1 || trailing == 3))
                            ? trailing + 1
                            : (c == '\r' ? 1 : 0);
            if (trailing == 4) {
                return sb.toString();
            }
        }
        throw new IOException("EOF while reading a response head; got '" + sb + "'");
    }
}
