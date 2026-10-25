/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.fixture;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.concurrent.ThreadFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import software.amazon.smithy.java.http.client.connection.ConnectionTransport;
import software.amazon.smithy.java.http.client.connection.FixtureServerTransports;

/**
 * Serves one prepared HTTP/1.1 response to every request on an HTTPS port, and switches that response
 * through a plaintext control port. One platform thread per connection, blocking I/O, BoringSSL TLS with
 * a self-signed certificate made at startup. The e2e benchmark starts this in a child JVM.
 */
public final class FixtureServer {

    public static final String LISTENING = "Listening on ";
    public static final String CONTROL_PATH = "/fixture";
    static final int READ_BUFFER = 16 * 1024;
    /** Buffer several TLS records per socket operation. */
    static final int TLS_READ_BUFFER = 32 * 1024;
    static final int TLS_WRITE_BUFFER = 64 * 1024;

    private static final byte[] NOT_READY = ascii("HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\n\r\n");

    private volatile byte[] response = NOT_READY;

    /** The bytes written for every request. One volatile read per request. */
    byte[] response() {
        return response;
    }

    void respondWith(byte[] wireBytes) {
        response = wireBytes;
    }

    public static void main(String[] args) throws Exception {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println();
            System.err.print(Options.usage());
            System.exit(2);
            return;
        }
        if (options.help) {
            System.out.print(Options.usage());
            return;
        }

        var server = new FixtureServer();
        SslContext tls = selfSignedTls();
        ServerSocketChannel data = bind(options.listen);
        ServerSocketChannel control = bind(options.control);
        var dataAddress = (InetSocketAddress) data.getLocalAddress();
        var controlAddress = (InetSocketAddress) control.getLocalAddress();
        // The benchmark reads this line to find both ports.
        System.out.printf("%shttps://%s:%d control=http://%s:%d%n",
                LISTENING,
                dataAddress.getAddress().getHostAddress(),
                dataAddress.getPort(),
                controlAddress.getAddress().getHostAddress(),
                controlAddress.getPort());
        System.out.flush();

        Thread.ofPlatform().daemon().name("fixture-control").start(() -> server.serveControl(control));
        server.serve(data, tls);
    }

    /** Accepts data connections until the listener closes. A null TLS context serves plaintext, for tests. */
    void serve(ServerSocketChannel listener, SslContext tls) throws IOException {
        ThreadFactory threads = Thread.ofPlatform().daemon().name("fixture-conn-", 0).factory();
        while (true) {
            SocketChannel channel;
            try {
                channel = listener.accept();
            } catch (ClosedChannelException e) {
                return;
            }
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            threads.newThread(() -> {
                ConnectionTransport transport;
                try {
                    transport = open(channel.socket(), tls);
                } catch (IOException | RuntimeException e) {
                    // The transport factory closes the socket and releases the engine on handshake failure.
                    return;
                }
                new Http1Connection(transport, this, READ_BUFFER).run();
            }).start();
        }
    }

    /** Handles control connections one at a time; the benchmark is the only client. */
    void serveControl(ServerSocketChannel listener) {
        while (true) {
            try {
                SocketChannel channel = listener.accept();
                ControlConnection.handle(channel.socket(), this);
            } catch (ClosedChannelException e) {
                return;
            } catch (IOException e) {
                System.err.println("control: " + e);
            }
        }
    }

    /** Accepts a complete HTTP/1.1 response whose Content-Length matches the body it carries. */
    static boolean isValidResponse(byte[] bytes) {
        if (bytes.length < 12 || !new String(bytes, 0, 9, StandardCharsets.ISO_8859_1).equals("HTTP/1.1 ")) {
            return false;
        }
        int headEnd = -1;
        for (int i = 0; i + 3 < bytes.length; i++) {
            if (bytes[i] == '\r' && bytes[i + 1] == '\n' && bytes[i + 2] == '\r' && bytes[i + 3] == '\n') {
                headEnd = i + 4;
                break;
            }
        }
        if (headEnd < 0) {
            return false;
        }
        long declared = -1;
        for (String line : new String(bytes, 0, headEnd, StandardCharsets.ISO_8859_1).split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).strip().equalsIgnoreCase("content-length")) {
                try {
                    declared = Long.parseLong(line.substring(colon + 1).strip());
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }
        return declared == bytes.length - headEnd;
    }

    /** A BoringSSL server context for TLS 1.3 with a fresh self-signed certificate from keytool. */
    static SslContext selfSignedTls() throws IOException, GeneralSecurityException, InterruptedException {
        if (!OpenSsl.isAvailable()) {
            throw new IllegalStateException(
                    "BoringSSL (netty-tcnative) is not available on this host: " + OpenSsl.unavailabilityCause());
        }
        return SslContextBuilder.forServer(selfSignedKey())
                .sslProvider(SslProvider.OPENSSL)
                .protocols("TLSv1.3")
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        "http/1.1"))
                .build();
    }

    /** Generates an EC key and self-signed certificate with keytool in a temporary directory. */
    private static KeyManagerFactory selfSignedKey()
            throws IOException, GeneralSecurityException, InterruptedException {
        Path dir = Files.createTempDirectory("smithy-java-fixture-tls");
        Path store = dir.resolve("server.p12");
        char[] password = "fixture".toCharArray();
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        var process = new ProcessBuilder(keytool,
                "-genkeypair",
                "-alias",
                "fixture",
                "-keyalg",
                "EC",
                "-groupname",
                "secp256r1",
                "-sigalg",
                "SHA256withECDSA",
                "-dname",
                "CN=localhost",
                "-ext",
                "san=dns:localhost,ip:127.0.0.1",
                "-validity",
                "30",
                "-storetype",
                "PKCS12",
                "-keystore",
                store.toString(),
                "-storepass",
                new String(password))
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IOException("keytool could not create a self-signed certificate: " + output.strip());
        }
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(store)) {
                keyStore.load(in, password);
            }
            var keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagers.init(keyStore, password);
            return keyManagers;
        } finally {
            Files.deleteIfExists(store);
            Files.deleteIfExists(dir);
        }
    }

    private static ServerSocketChannel bind(InetSocketAddress address) throws IOException {
        ServerSocketChannel listener = ServerSocketChannel.open();
        listener.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        listener.bind(address, 128);
        return listener;
    }

    private static ConnectionTransport open(Socket socket, SslContext tls) throws IOException {
        if (tls == null) {
            return FixtureServerTransports.plaintext(socket);
        }
        SSLEngine engine = tls.newEngine(ByteBufAllocator.DEFAULT);
        engine.setUseClientMode(false);
        return FixtureServerTransports.tls(
                socket,
                engine,
                () -> ReferenceCountUtil.release(engine),
                TLS_READ_BUFFER,
                TLS_WRITE_BUFFER);
    }

    static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    static final class Options {
        InetSocketAddress listen = loopback(0);
        InetSocketAddress control = loopback(0);
        boolean help;

        static Options parse(String[] args) {
            var o = new Options();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--listen" -> o.listen = address(value(args, ++i, arg));
                    case "--control" -> o.control = address(value(args, ++i, arg));
                    case "--help", "-h" -> o.help = true;
                    default -> throw new IllegalArgumentException("Unknown argument '" + arg + "'");
                }
            }
            return o;
        }

        static InetSocketAddress address(String hostPort) {
            int colon = hostPort.lastIndexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException("expected host:port, got '" + hostPort + "'");
            }
            try {
                return new InetSocketAddress(
                        InetAddress.getByName(hostPort.substring(0, colon)),
                        Integer.parseInt(hostPort.substring(colon + 1)));
            } catch (IOException | NumberFormatException e) {
                throw new IllegalArgumentException("expected host:port, got '" + hostPort + "'", e);
            }
        }

        private static InetSocketAddress loopback(int port) {
            return new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
        }

        private static String value(String[] args, int index, String flag) {
            if (index >= args.length) {
                throw new IllegalArgumentException(flag + " requires a value");
            }
            return args[index];
        }

        static String usage() {
            return """
                    smithy-java e2e fixture server

                    Usage: java -cp smithy-java-e2e-benchmark.jar software.amazon.smithy.java.benchmarks.fixture.FixtureServer [options]

                      --listen HOST:PORT    HTTPS data port (default 127.0.0.1:0, a free port)
                      --control HOST:PORT   Plaintext control port (default 127.0.0.1:0)
                      --help

                    Every request on the data port receives the current fixture; request bodies are drained.
                    POST /fixture on the control port, with the complete HTTP/1.1 response as the body,
                    replaces the fixture and answers 204. Until then the data port answers 503. The first
                    line printed is "Listening on https://HOST:PORT control=http://HOST:PORT".
                    """;
        }
    }
}
