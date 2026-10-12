/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import software.amazon.smithy.java.benchmarks.fixture.FixtureServer;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.http.boringssl.BoringSslTlsProvider;
import software.amazon.smithy.java.client.http.smithy.SmithyHttpClientTransport;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.client.HttpClient;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;

/**
 * The fixture server in a child JVM, reached through smithy-java's HTTP client with BoringSSL.
 * A separate process keeps the server's CPU time out of this process's measurement.
 */
final class FixtureServerProcess implements Target {

    private static final List<String> SERVER_JVM_FLAGS = List.of(
            "-Xbatch",
            "-XX:+UseSerialGC",
            "-Xms64m",
            "-Xmx64m",
            "-XX:+AlwaysPreTouch",
            "-Dio.netty.leakDetection.level=disabled");
    private static final String DESCRIPTION = "smithy-java HttpClient (SmithyHttpClientTransport), HTTP/1.1 over "
            + "BoringSSL TLS 1.3 to a loopback fixture server in a child JVM; certificate verification disabled "
            + "(self-signed)";

    private final Process process;
    private final String endpoint;
    private final InetSocketAddress control;
    private final SmithyHttpClientTransport transport;

    private FixtureServerProcess(
            Process process,
            String endpoint,
            InetSocketAddress control,
            SmithyHttpClientTransport transport
    ) {
        this.process = process;
        this.endpoint = endpoint;
        this.control = control;
        this.transport = transport;
    }

    static FixtureServerProcess start() throws IOException {
        if (!BoringSslTlsProvider.available()) {
            throw new IllegalStateException("BoringSSL (netty-tcnative) is not available on this host");
        }
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(SERVER_JVM_FLAGS);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(FixtureServer.class.getName());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {

            var output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = output.readLine()) != null && !line.startsWith(FixtureServer.LISTENING)) {
                System.err.println("fixture server: " + line);
            }
            if (line == null) {
                throw new IOException(
                        "The fixture server exited before listening (status " + exitStatus(process) + ")");
            }
            // "Listening on https://127.0.0.1:P control=http://127.0.0.1:Q"
            String[] parts = line.split(" ");
            String endpoint = parts[2];
            URI controlUri = URI.create(parts[3].substring("control=".length()));
            var controlAddress = new InetSocketAddress(controlUri.getHost(), controlUri.getPort());
            // Keep forwarding the server's output so TLS or framing errors are visible.
            Thread.ofPlatform().daemon().name("fixture-server-output").start(() -> forward(output));

            var client = HttpClient.builder()
                    .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(5))
                    .tlsProvider(BoringSslTlsProvider.create(true))
                    .build();
            return new FixtureServerProcess(process, endpoint, controlAddress, new SmithyHttpClientTransport(client));
        } catch (IOException | RuntimeException | Error e) {
            process.destroyForcibly();
            throw e;
        }
    }

    @Override
    public ClientTransport<HttpRequest, HttpResponse> transport() {
        return transport;
    }

    @Override
    public String endpoint() {
        return endpoint;
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    /** Posts the wire bytes of the response to the control port and waits for the server to accept them. */
    @Override
    public void respondWith(CannedResponse response) throws IOException {
        byte[] fixture = response.toHttp1Bytes();
        try (var socket = new Socket()) {
            socket.connect(control, 5_000);
            socket.setSoTimeout(10_000);
            var out = socket.getOutputStream();
            out.write(("POST " + FixtureServer.CONTROL_PATH + " HTTP/1.1\r\nHost: fixture\r\nContent-Length: "
                    + fixture.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(fixture);
            out.flush();
            String status = statusLine(socket.getInputStream());
            if (!status.startsWith("HTTP/1.1 204")) {
                throw new IOException("The fixture server rejected the fixture: " + status);
            }
        }
    }

    @Override
    public void close() throws IOException {
        try {
            transport.close();
        } finally {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String statusLine(InputStream in) throws IOException {
        var sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0 && c != '\n') {
            if (c != '\r') {
                sb.append((char) c);
            }
        }
        return sb.toString();
    }

    private static void forward(BufferedReader output) {
        try {
            String line;
            while ((line = output.readLine()) != null) {
                System.err.println("fixture server: " + line);
            }
        } catch (IOException e) {
            // The server exited.
        }
    }

    private static String exitStatus(Process process) {
        try {
            return process.waitFor(5, TimeUnit.SECONDS) ? Integer.toString(process.exitValue()) : "still running";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "unknown";
        }
    }
}
