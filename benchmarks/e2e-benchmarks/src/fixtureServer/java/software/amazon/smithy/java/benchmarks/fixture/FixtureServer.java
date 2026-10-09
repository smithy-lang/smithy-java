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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import software.amazon.smithy.java.http.client.connection.ConnectionTransport;
import software.amazon.smithy.java.http.client.connection.FixtureServerTransports;

/**
 * A fixture server for SDK client benchmarks: every request on every path gets the same prepared response. Accepts
 * on the main thread and serves each connection on its own platform thread with blocking socket I/O; the TLS
 * handshake runs on the connection's thread, never on the acceptor.
 *
 * <p>TLS is BoringSSL (netty-tcnative), the engine the smithy client uses, driven by the http-client's
 * {@code SSLEngineTransport}. There is no other TLS path: if the native library is missing the server refuses to
 * start rather than silently measuring a different engine.
 *
 * <p>The first output line is {@code Listening on <scheme>://<host>:<port> ...}, which {@code fixture/run-transport.py}
 * waits for before it points the client at the server.
 */
public final class FixtureServer {

    static final String VERSION = "smithy-java-fixture-server 0.2.0 (platform thread per connection, BoringSSL TLS)";
    static final int DEFAULT_READ_BUFFER = 16 * 1024;
    /** Ciphertext buffering: two records in, four records out so a large response is a few writes, not one per record. */
    static final int TLS_READ_BUFFER = 32 * 1024;
    static final int TLS_WRITE_BUFFER = 64 * 1024;

    private FixtureServer() {}

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
        if (options.version) {
            System.out.println(VERSION);
            return;
        }

        Fixture fixture = Fixture.load(options.body, options.status, options.contentType, options.headers);
        SslContext tls = options.plaintext ? null : serverSslContext(options.cert, options.key, options.tlsProtocols());
        ServerSocketChannel listener = ServerSocketChannel.open();
        listener.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        listener.bind(new InetSocketAddress(InetAddress.getByName(options.listenHost), options.listenPort), 1024);

        // fixture/run-transport.py parses this first line to learn the chosen port.
        var bound = (InetSocketAddress) listener.getLocalAddress();
        System.out.printf("Listening on %s://%s:%d backend=thread-per-connection tls=%s response_bytes=%d%n",
                tls == null ? "http" : "https",
                bound.getAddress().getHostAddress(),
                bound.getPort(),
                tls == null ? "none" : "boringssl",
                fixture.body().length);
        System.out.flush();

        serve(listener, fixture, tls, options.readBuffer, options.timing);
    }

    /**
     * A server-side BoringSSL context from PEM files, TLS 1.3 unless told otherwise, advertising HTTP/1.1 via ALPN.
     */
    static SslContext serverSslContext(Path certificateChain, Path privateKey, String[] protocols) throws SSLException {
        if (!OpenSsl.isAvailable()) {
            throw new IllegalStateException(
                    "BoringSSL (netty-tcnative) is not available on this host: " + OpenSsl.unavailabilityCause());
        }
        var builder = SslContextBuilder.forServer(certificateChain.toFile(), privateKey.toFile())
                .sslProvider(SslProvider.OPENSSL)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        "http/1.1"));
        if (protocols != null) {
            builder.protocols(protocols);
        }
        return builder.build();
    }

    /** Accepts until the listener is closed, one platform thread per connection. */
    static void serve(ServerSocketChannel listener, Fixture fixture, SslContext tls, int readBuffer, boolean timing)
            throws IOException {
        ThreadFactory threads = Thread.ofPlatform().daemon().name("fixture-conn-", 0).factory();
        while (true) {
            SocketChannel channel;
            try {
                channel = listener.accept();
            } catch (ClosedChannelException e) {
                return;
            }
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            var timer = timing ? new Http1Connection.Timing() : null;
            threads.newThread(() -> {
                ConnectionTransport transport;
                try {
                    transport = open(channel.socket(), tls);
                } catch (IOException | RuntimeException e) {
                    // Handshake failure or a client that went away before it; the transport factory already
                    // released the engine and closed the socket.
                    return;
                }
                new Http1Connection(transport, fixture, readBuffer, timer).run();
            }).start();
        }
    }

    /** The accepted socket as a transport: plain, or handshaken TLS with a fresh server-mode engine. */
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

    static final class Options {
        String listenHost = "127.0.0.1";
        int listenPort = 8443;
        Path body;
        int status = 200;
        String contentType = "application/octet-stream";
        final List<Map.Entry<String, String>> headers = new ArrayList<>();
        Path cert;
        Path key;
        boolean plaintext;
        String tlsVersion = "1.3";
        int readBuffer = DEFAULT_READ_BUFFER;
        boolean timing;
        boolean help;
        boolean version;

        static Options parse(String[] args) {
            var o = new Options();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--listen" -> {
                        String v = value(args, ++i, arg);
                        int colon = v.lastIndexOf(':');
                        if (colon < 0) {
                            throw new IllegalArgumentException("--listen expects host:port, got '" + v + "'");
                        }
                        o.listenHost = v.substring(0, colon);
                        o.listenPort = Integer.parseInt(v.substring(colon + 1));
                    }
                    case "--body" -> o.body = Path.of(value(args, ++i, arg));
                    case "--status" -> o.status = Integer.parseInt(value(args, ++i, arg));
                    case "--content-type" -> o.contentType = value(args, ++i, arg);
                    case "--header", "-H" -> {
                        String v = value(args, ++i, arg);
                        int colon = v.indexOf(':');
                        if (colon <= 0) {
                            throw new IllegalArgumentException("--header expects 'Name: value', got '" + v + "'");
                        }
                        o.headers.add(Map.entry(v.substring(0, colon).strip(), v.substring(colon + 1).strip()));
                    }
                    case "--cert" -> o.cert = Path.of(value(args, ++i, arg));
                    case "--key" -> o.key = Path.of(value(args, ++i, arg));
                    case "--plaintext" -> o.plaintext = true;
                    case "--tls-version" -> o.tlsVersion = value(args, ++i, arg);
                    case "--read-buffer" -> o.readBuffer = Integer.parseInt(value(args, ++i, arg));
                    case "--timing" -> o.timing = true;
                    case "--help", "-h" -> o.help = true;
                    case "--version", "-V" -> o.version = true;
                    default -> throw new IllegalArgumentException("Unknown argument '" + arg + "'");
                }
            }
            if (o.help || o.version) {
                return o;
            }
            if (o.body == null) {
                throw new IllegalArgumentException("--body is required");
            }
            if (o.plaintext) {
                if (o.cert != null || o.key != null) {
                    throw new IllegalArgumentException("--plaintext conflicts with --cert/--key");
                }
            } else if (o.cert == null || o.key == null) {
                throw new IllegalArgumentException(
                        "provide --cert and --key for HTTPS, or explicitly choose --plaintext");
            }
            if (!o.tlsVersion.equals("1.2") && !o.tlsVersion.equals("1.3") && !o.tlsVersion.equals("auto")) {
                throw new IllegalArgumentException("--tls-version expects 1.2, 1.3 or auto");
            }
            if (o.readBuffer < 1024) {
                throw new IllegalArgumentException("--read-buffer must be at least 1024 bytes");
            }
            return o;
        }

        String[] tlsProtocols() {
            return switch (tlsVersion) {
                case "1.2" -> new String[] {"TLSv1.2"};
                case "1.3" -> new String[] {"TLSv1.3"};
                default -> null;
            };
        }

        private static String value(String[] args, int index, String flag) {
            if (index >= args.length) {
                throw new IllegalArgumentException(flag + " requires a value");
            }
            return args[index];
        }

        static String usage() {
            return """
                    smithy-java fixture server (Java, one platform thread per connection, BoringSSL TLS)

                    Usage: java -jar smithy-java-fixture-server.jar --body FILE [options]

                      --listen HOST:PORT     Listen address (default 127.0.0.1:8443; port 0 picks a free port)
                      --body FILE            Response bytes, loaded once at startup
                      --status N             Response status, default 200 (204, 205 and 304 need an empty body)
                      --content-type TYPE    Content-Type, default application/octet-stream
                      --header 'Name: v'     Additional fixed response header, repeatable
                      --cert PEM --key PEM   Serve HTTPS (BoringSSL) with this certificate chain and PKCS#8 key
                      --plaintext            Serve HTTP instead
                      --tls-version V        1.2, 1.3 (default) or auto
                      --read-buffer N        Per-connection read buffer in bytes (default 16384)
                      --timing               Diagnostic: per-connection service and idle time on stderr at close
                      --version, --help

                    Every path and method receives the same response; HEAD gets the headers only. Request bodies
                    (Content-Length or chunked, with trailers) are drained and discarded. Keep-alive by default,
                    Connection: close honoured, Expect: 100-continue answered. The first line printed is
                    "Listening on <scheme>://<host>:<port> ...".
                    """;
        }
    }
}
