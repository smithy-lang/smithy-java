/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.fixture;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.concurrent.ThreadFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;

/**
 * Serves one prepared HTTP/1.1 response to every request on an HTTPS port, and switches that response
 * through a plaintext control port. One platform thread per connection, blocking I/O, JDK TLS with a
 * self-signed certificate made at startup. The e2e benchmark starts this in a child JVM.
 *
 * <p>This is the Feb-2026 baseline copy of the server: the SDK of that date has no BoringSSL engine
 * transport to reuse, so TLS comes from {@code SSLServerSocket}. Ports, output and the control protocol
 * are identical to the current server.
 */
public final class FixtureServer {

    public static final String LISTENING = "Listening on ";
    public static final String CONTROL_PATH = "/fixture";
    static final int READ_BUFFER = 16 * 1024;

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
        ServerSocket data = bindTls(selfSignedTls(), options.listen);
        ServerSocket control = bind(options.control);
        var dataAddress = (InetSocketAddress) data.getLocalSocketAddress();
        var controlAddress = (InetSocketAddress) control.getLocalSocketAddress();
        // The benchmark reads this line to find both ports.
        System.out.printf("%shttps://%s:%d control=http://%s:%d%n",
                LISTENING,
                dataAddress.getAddress().getHostAddress(),
                dataAddress.getPort(),
                controlAddress.getAddress().getHostAddress(),
                controlAddress.getPort());
        System.out.flush();

        Thread.ofPlatform().daemon().name("fixture-control").start(() -> server.serveControl(control));
        server.serve(data);
    }

    /** Accepts data connections until the listener closes. A plain ServerSocket serves plaintext, for tests. */
    void serve(ServerSocket listener) throws IOException {
        ThreadFactory threads = Thread.ofPlatform().daemon().name("fixture-conn-", 0).factory();
        while (true) {
            Socket socket;
            try {
                socket = listener.accept();
            } catch (IOException e) {
                if (listener.isClosed()) {
                    return;
                }
                continue;
            }
            socket.setTcpNoDelay(true);
            // An SSLSocket handshakes on first use; a failed handshake ends the connection like any other I/O error.
            threads.newThread(new Http1Connection(socket, this, READ_BUFFER)).start();
        }
    }

    /** Handles control connections one at a time; the benchmark is the only client. */
    void serveControl(ServerSocket listener) {
        while (true) {
            try {
                ControlConnection.handle(listener.accept(), this);
            } catch (IOException e) {
                if (listener.isClosed()) {
                    return;
                }
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

    /** A JDK TLS context with a fresh self-signed certificate from keytool. */
    static SSLContext selfSignedTls() throws IOException, GeneralSecurityException, InterruptedException {
        var context = SSLContext.getInstance("TLS");
        context.init(selfSignedKey().getKeyManagers(), null, null);
        return context;
    }

    /** Binds an HTTPS listener restricted to TLS 1.3 that advertises HTTP/1.1 through ALPN. */
    static ServerSocket bindTls(SSLContext tls, InetSocketAddress address) throws IOException {
        var listener = (SSLServerSocket) tls.getServerSocketFactory().createServerSocket();
        SSLParameters parameters = listener.getSSLParameters();
        parameters.setProtocols(new String[] {"TLSv1.3"});
        parameters.setApplicationProtocols(new String[] {"http/1.1"});
        listener.setSSLParameters(parameters);
        listener.setReuseAddress(true);
        listener.bind(address, 128);
        return listener;
    }

    static ServerSocket bind(InetSocketAddress address) throws IOException {
        var listener = new ServerSocket();
        listener.setReuseAddress(true);
        listener.bind(address, 128);
        return listener;
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
