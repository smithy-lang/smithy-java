/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FixtureServerTest {

    private static final byte[] BODY = "{\"Item\":{\"pk\":{\"S\":\"1\"}}}".getBytes(StandardCharsets.UTF_8);
    private static final String EXPECTED_HEAD = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
            + "Content-Length: " + BODY.length + "\r\nx-amzn-RequestId: benchmark\r\n\r\n";

    @TempDir
    static Path tmp;
    private static ServerSocketChannel listener;
    private static Thread acceptor;
    private static int port;

    @BeforeAll
    static void start() throws Exception {
        Path body = tmp.resolve("body.json");
        Files.write(body, BODY);
        var fixture = Fixture.load(body, 200, "application/json", List.of(Map.entry("x-amzn-RequestId", "benchmark")));
        listener = ServerSocketChannel.open();
        listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        port = ((InetSocketAddress) listener.getLocalAddress()).getPort();
        acceptor = Thread.ofPlatform().daemon().start(() -> {
            try {
                FixtureServer.serve(listener, fixture, null, 1024, true);
            } catch (IOException e) {
                // closed by stop()
            }
        });
    }

    @AfterAll
    static void stop() throws Exception {
        listener.close();
        acceptor.join(5_000);
    }

    @Test
    void keepAliveServesRepeatedRequestsOnOneConnection() throws Exception {
        try (var client = connect()) {
            for (int i = 0; i < 3; i++) {
                send(client, "GET /any/path?x=" + i + " HTTP/1.1\r\nHost: localhost\r\n\r\n");
                assertThat(readResponse(client, BODY.length))
                        .isEqualTo(EXPECTED_HEAD + new String(BODY, StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void requestBodiesAreDrainedWhetherBufferedOrNot() throws Exception {
        try (var client = connect()) {
            // Smaller than the read buffer: arrives with the head.
            send(client, "POST / HTTP/1.1\r\nHost: h\r\nContent-Length: 5\r\n\r\nhello");
            assertThat(readResponse(client, BODY.length)).startsWith("HTTP/1.1 200 OK");
            // Larger than the 1 KB read buffer: drained straight from the socket.
            String big = "x".repeat(5_000);
            send(client, "PUT /obj HTTP/1.1\r\nHost: h\r\nContent-Length: " + big.length() + "\r\n\r\n" + big);
            assertThat(readResponse(client, BODY.length)).startsWith("HTTP/1.1 200 OK");
            // Still alive afterwards.
            send(client, "GET / HTTP/1.1\r\nHost: h\r\n\r\n");
            assertThat(readResponse(client, BODY.length)).startsWith("HTTP/1.1 200 OK");
        }
    }

    @Test
    void chunkedUploadsWithTrailersAreDrained() throws Exception {
        try (var client = connect()) {
            String chunk = "y".repeat(3_000);
            send(client,
                    "POST / HTTP/1.1\r\nHost: h\r\nTransfer-Encoding: chunked\r\nTrailer: x-crc\r\n\r\n"
                            + "5\r\nhello\r\n" + Integer.toHexString(chunk.length()) + ";ext=1\r\n" + chunk
                            + "\r\n0\r\n"
                            + "x-crc: abc\r\n\r\n");
            assertThat(readResponse(client, BODY.length)).startsWith("HTTP/1.1 200 OK");
            send(client, "GET / HTTP/1.1\r\nHost: h\r\n\r\n");
            assertThat(readResponse(client, BODY.length)).startsWith("HTTP/1.1 200 OK");
        }
    }

    @Test
    void chunkDataRequiresACrlfTerminator() throws Exception {
        try (var client = connect()) {
            send(client,
                    "POST / HTTP/1.1\r\nHost: h\r\nTransfer-Encoding: chunked\r\n\r\n"
                            + "3\r\nabcXX\r\n0\r\n\r\n");
            assertThat(client.getInputStream().read()).as("malformed chunk closes without a success response")
                    .isEqualTo(-1);
        }
    }

    @Test
    void headGetsHeadersOnly() throws Exception {
        try (var client = connect()) {
            send(client, "HEAD / HTTP/1.1\r\nHost: h\r\n\r\n");
            assertThat(readResponse(client, 0)).isEqualTo(EXPECTED_HEAD);
            send(client, "GET / HTTP/1.1\r\nHost: h\r\n\r\n");
            assertThat(readResponse(client, BODY.length)).endsWith(new String(BODY, StandardCharsets.UTF_8));
        }
    }

    @Test
    void fragmentedHeadIsFramedCorrectly() throws Exception {
        try (var client = connect()) {
            byte[] request = "GET / HTTP/1.1\r\nHost: h\r\nX-A: 1\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
            OutputStream out = client.getOutputStream();
            for (byte b : request) {
                out.write(b);
                out.flush();
            }
            assertThat(readResponse(client, BODY.length)).startsWith("HTTP/1.1 200 OK");
        }
    }

    @Test
    void pipelinedRequestsAreAnsweredInOrder() throws Exception {
        try (var client = connect()) {
            send(client, "GET /1 HTTP/1.1\r\nHost: h\r\n\r\nGET /2 HTTP/1.1\r\nHost: h\r\n\r\n");
            assertThat(readResponse(client, BODY.length)).startsWith("HTTP/1.1 200 OK");
            assertThat(readResponse(client, BODY.length)).startsWith("HTTP/1.1 200 OK");
        }
    }

    @Test
    void expectContinueIsAnsweredBeforeTheBodyIsRead() throws Exception {
        try (var client = connect()) {
            send(client, "PUT / HTTP/1.1\r\nHost: h\r\nContent-Length: 3\r\nExpect: 100-continue\r\n\r\n");
            assertThat(readLine(client.getInputStream())).isEqualTo("HTTP/1.1 100 Continue");
            assertThat(readLine(client.getInputStream())).isEmpty();
            send(client, "abc");
            assertThat(readResponse(client, BODY.length)).startsWith("HTTP/1.1 200 OK");
        }
    }

    @Test
    void connectionCloseIsHonoured() throws Exception {
        try (var client = connect()) {
            send(client, "GET / HTTP/1.1\r\nHost: h\r\nConnection: close\r\n\r\n");
            String response = readResponse(client, BODY.length);
            assertThat(response).contains("\r\nConnection: close\r\n");
            assertThat(client.getInputStream().read()).as("server closed").isEqualTo(-1);
        }
    }

    @Test
    void ambiguousFramingGetsA400AndAClose() throws Exception {
        try (var client = connect()) {
            send(client, "POST / HTTP/1.1\r\nHost: h\r\nContent-Length: 3\r\nTransfer-Encoding: chunked\r\n\r\n");
            assertThat(readResponse(client, 0)).startsWith("HTTP/1.1 400 Bad Request");
            assertThat(client.getInputStream().read()).isEqualTo(-1);
        }
        try (var client = connect()) {
            send(client, "POST / HTTP/1.1\r\nHost: h\r\nTransfer-Encoding: gzip, chunked\r\n\r\n");
            assertThat(readResponse(client, 0)).startsWith("HTTP/1.1 400 Bad Request");
        }
    }

    @Test
    void oversizedHeadGetsA431() throws Exception {
        try (var client = connect()) {
            send(client, "GET / HTTP/1.1\r\nHost: h\r\nX-Big: " + "z".repeat(2_000) + "\r\n\r\n");
            assertThat(readResponse(client, 0)).startsWith("HTTP/1.1 431 ");
        }
    }

    @Test
    void bodilessStatusesRejectABodyAndOmitContentLength() throws Exception {
        Path body = tmp.resolve("body204.bin");
        Files.write(body, BODY);
        assertThatThrownBy(() -> Fixture.load(body, 204, "text/plain", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty body");
        Path empty = tmp.resolve("empty.bin");
        Files.write(empty, new byte[0]);
        var fixture = Fixture.load(empty, 204, "text/plain", List.of());
        assertThat(new String(fixture.response(false, false), StandardCharsets.US_ASCII))
                .isEqualTo("HTTP/1.1 204 No Content\r\nContent-Type: text/plain\r\n\r\n");
        assertThat(new String(Fixture.load(empty, 205, "text/plain", List.of()).response(false, true),
                StandardCharsets.US_ASCII))
                .isEqualTo("HTTP/1.1 205 Reset Content\r\nContent-Type: text/plain\r\nContent-Length: 0\r\n"
                        + "Connection: close\r\n\r\n");
    }

    @Test
    void parsesTheServerCommandLine() {
        var o = FixtureServer.Options.parse(new String[] {
                "--listen",
                "127.0.0.1:0",
                "--body",
                "x.body",
                "--status",
                "200",
                "--content-type",
                "application/cbor",
                "--header",
                "x-amzn-RequestId: benchmark",
                "--plaintext",
                "--tls-version",
                "1.3"});
        assertThat(o.listenPort).isZero();
        assertThat(o.headers).containsExactly(Map.entry("x-amzn-RequestId", "benchmark"));
        assertThat(o.plaintext).isTrue();
        assertThatThrownBy(() -> FixtureServer.Options.parse(new String[] {"--body", "x"}))
                .hasMessageContaining("--cert and --key");
    }

    private static Socket connect() throws IOException {
        var socket = new Socket();
        socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2_000);
        socket.setSoTimeout(5_000);
        return socket;
    }

    private static void send(Socket socket, String text) throws IOException {
        socket.getOutputStream().write(text.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

    /** Reads a response head plus exactly {@code bodyLength} body bytes. */
    private static String readResponse(Socket socket, int bodyLength) throws IOException {
        InputStream in = socket.getInputStream();
        var sb = new StringBuilder();
        while (true) {
            String line = readLine(in);
            sb.append(line).append("\r\n");
            if (line.isEmpty()) {
                break;
            }
        }
        if (bodyLength > 0) {
            byte[] body = in.readNBytes(bodyLength);
            sb.append(new String(body, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static String readLine(InputStream in) throws IOException {
        var sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\r') {
                int lf = in.read();
                if (lf != '\n') {
                    throw new IOException("CR without LF");
                }
                return sb.toString();
            }
            sb.append((char) c);
        }
        throw new IOException("EOF while reading a line; got '" + sb + "'");
    }
}
