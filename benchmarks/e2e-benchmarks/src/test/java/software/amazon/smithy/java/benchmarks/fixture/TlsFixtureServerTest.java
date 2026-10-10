/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.netty.handler.ssl.OpenSsl;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TlsFixtureServerTest {

    private static final int BODY_BYTES = 70_000;

    @TempDir
    static Path tmp;
    private static ServerSocketChannel listener;
    private static SSLContext clientContext;
    private static int port;

    @BeforeAll
    static void start() throws Exception {
        assumeTrue(OpenSsl.isAvailable(), "netty-tcnative (BoringSSL) is not available on this host");
        Path cert = tmp.resolve("server.pem");
        Path key = tmp.resolve("server-key.pem");
        assumeTrue(generateCertificate(cert, key), "openssl is not available to generate a test certificate");

        byte[] body = new byte[BODY_BYTES];
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) ('a' + i % 26);
        }
        Path bodyFile = tmp.resolve("body.bin");
        Files.write(bodyFile, body);
        var fixture = Fixture.load(bodyFile, 200, "application/octet-stream", List.of());
        var tls = FixtureServer.serverSslContext(cert, key, new String[] {"TLSv1.3"});

        listener = ServerSocketChannel.open();
        listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        port = ((InetSocketAddress) listener.getLocalAddress()).getPort();
        Thread.ofPlatform().daemon().start(() -> {
            try {
                FixtureServer.serve(listener, fixture, tls, FixtureServer.DEFAULT_READ_BUFFER, false);
            } catch (IOException e) {}
        });
        clientContext = trusting(cert);
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
            send(socket, "HEAD / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            assertThat(readHead(in)).startsWith("HTTP/1.1 200 OK").contains("\r\nConnection: close\r\n");
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

    private static boolean generateCertificate(Path cert, Path key) {
        try {
            var process = new ProcessBuilder("openssl",
                    "req",
                    "-x509",
                    "-newkey",
                    "ec",
                    "-pkeyopt",
                    "ec_paramgen_curve:prime256v1",
                    "-nodes",
                    "-days",
                    "1",
                    "-subj",
                    "/CN=localhost",
                    "-addext",
                    "subjectAltName=DNS:localhost,IP:127.0.0.1",
                    "-keyout",
                    key.toString(),
                    "-out",
                    cert.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0 && Files.exists(cert);
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static SSLContext trusting(Path cert) throws Exception {
        var store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        try (InputStream in = Files.newInputStream(cert)) {
            store.setCertificateEntry("fixture", CertificateFactory.getInstance("X.509").generateCertificate(in));
        }
        var trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(store);
        var context = SSLContext.getInstance("TLS");
        context.init(null, trust.getTrustManagers(), null);
        return context;
    }

    private static SSLSocket connect() throws IOException {
        var socket = (SSLSocket) clientContext.getSocketFactory().createSocket(InetAddress.getLoopbackAddress(), port);
        socket.setSoTimeout(10_000);
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setProtocols(new String[] {"TLSv1.3"});
        parameters.setApplicationProtocols(new String[] {"http/1.1"});
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
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
