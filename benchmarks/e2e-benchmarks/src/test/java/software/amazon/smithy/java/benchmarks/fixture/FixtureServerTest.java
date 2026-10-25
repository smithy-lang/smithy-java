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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FixtureServerTest {

    private static final byte[] BODY = "{\"Item\":{\"pk\":{\"S\":\"1\"}}}".getBytes(StandardCharsets.UTF_8);
    private static final String HEAD = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
            + "Content-Length: " + BODY.length + "\r\nx-amzn-RequestId: benchmark\r\n\r\n";
    private static final byte[] FIXTURE = concat(HEAD.getBytes(StandardCharsets.US_ASCII), BODY);

    private static FixtureServer server;
    private static ServerSocketChannel data;
    private static ServerSocketChannel control;
    private static int port;
    private static int controlPort;

    @BeforeAll
    static void start() throws Exception {
        server = new FixtureServer();
        server.respondWith(FIXTURE);
        data = ServerSocketChannel.open();
        data.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        port = ((InetSocketAddress) data.getLocalAddress()).getPort();
        control = ServerSocketChannel.open();
        control.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        controlPort = ((InetSocketAddress) control.getLocalAddress()).getPort();
        Thread.ofPlatform().daemon().start(() -> {
            try {
                server.serve(data, null);
            } catch (IOException e) {}
        });
        Thread.ofPlatform().daemon().start(() -> server.serveControl(control));
    }

    @AfterAll
    static void stop() throws Exception {
        data.close();
        control.close();
    }

    @Test
    void keepAliveServesRepeatedRequestsOnOneConnection() throws Exception {
        try (var client = connect(port)) {
            for (int i = 0; i < 3; i++) {
                send(client, "GET /any/path?x=" + i + " HTTP/1.1\r\nHost: localhost\r\n\r\n");
                assertThat(readResponse(client, BODY.length))
                        .isEqualTo(HEAD + new String(BODY, StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void requestBodiesAreDrainedWhetherBufferedOrNot() throws Exception {
        try (var client = connect(port)) {
            // This body fits in the header read buffer.
            send(client, "POST / HTTP/1.1\r\nHost: h\r\nContent-Length: 5\r\n\r\nhello");
            assertThat(readResponse(client, BODY.length)).startsWith("HTTP/1.1 200 OK");
            // This body exceeds the read buffer and must drain from the socket.
            String big = "x".repeat(FixtureServer.READ_BUFFER + 5_000);
            send(client, "PUT /obj HTTP/1.1\r\nHost: h\r\nContent-Length: " + big.length() + "\r\n\r\n" + big);
            assertThat(readResponse(client, BODY.length)).startsWith("HTTP/1.1 200 OK");
            send(client, "GET / HTTP/1.1\r\nHost: h\r\n\r\n");
            assertThat(readResponse(client, BODY.length)).startsWith("HTTP/1.1 200 OK");
        }
    }

    @Test
    void chunkedUploadsWithTrailersAreDrained() throws Exception {
        try (var client = connect(port)) {
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
        try (var client = connect(port)) {
            send(client,
                    "POST / HTTP/1.1\r\nHost: h\r\nTransfer-Encoding: chunked\r\n\r\n"
                            + "3\r\nabcXX\r\n0\r\n\r\n");
            assertThat(client.getInputStream().read()).as("malformed chunk closes without a success response")
                    .isEqualTo(-1);
        }
    }

    @Test
    void fragmentedHeadIsFramedCorrectly() throws Exception {
        try (var client = connect(port)) {
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
        try (var client = connect(port)) {
            send(client, "GET /1 HTTP/1.1\r\nHost: h\r\n\r\nGET /2 HTTP/1.1\r\nHost: h\r\n\r\n");
            assertThat(readResponse(client, BODY.length)).startsWith("HTTP/1.1 200 OK");
            assertThat(readResponse(client, BODY.length)).startsWith("HTTP/1.1 200 OK");
        }
    }

    @Test
    void connectionCloseAndHttp10GetOneResponseThenAClose() throws Exception {
        try (var client = connect(port)) {
            send(client, "GET / HTTP/1.1\r\nHost: h\r\nConnection: close\r\n\r\n");
            assertThat(readResponse(client, BODY.length)).startsWith("HTTP/1.1 200 OK");
            assertThat(client.getInputStream().read()).as("server closed").isEqualTo(-1);
        }
        try (var client = connect(port)) {
            send(client, "GET / HTTP/1.0\r\n\r\n");
            assertThat(readResponse(client, BODY.length)).startsWith("HTTP/1.1 200 OK");
            assertThat(client.getInputStream().read()).as("server closed").isEqualTo(-1);
        }
    }

    @Test
    void malformedFramingGetsA400AndAClose() throws Exception {
        try (var client = connect(port)) {
            send(client, "POST / HTTP/1.1\r\nHost: h\r\nContent-Length: 3\r\nTransfer-Encoding: chunked\r\n\r\n");
            assertThat(readResponse(client, 0)).startsWith("HTTP/1.1 400 Bad Request");
            assertThat(client.getInputStream().read()).isEqualTo(-1);
        }
        try (var client = connect(port)) {
            send(client, "POST / HTTP/1.1\r\nHost: h\r\nTransfer-Encoding: gzip, chunked\r\n\r\n");
            assertThat(readResponse(client, 0)).startsWith("HTTP/1.1 400 Bad Request");
        }
        try (var client = connect(port)) {
            send(client,
                    "GET / HTTP/1.1\r\nHost: h\r\nX-Big: " + "z".repeat(FixtureServer.READ_BUFFER + 100)
                            + "\r\n\r\n");
            assertThat(readResponse(client, 0)).startsWith("HTTP/1.1 400 Bad Request");
        }
    }

    @Test
    void controlPortSwitchesTheResponseForOpenAndNewConnections() throws Exception {
        byte[] body2 = "{\"Item\":{\"pk\":{\"S\":\"second\"}}}".getBytes(StandardCharsets.UTF_8);
        byte[] fixture2 = concat(("HTTP/1.1 200 OK\r\ncontent-type: application/json\r\ncontent-length: "
                + body2.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII), body2);
        try (var open = connect(port)) {
            send(open, "GET / HTTP/1.1\r\nHost: h\r\n\r\n");
            assertThat(readResponse(open, BODY.length)).endsWith(new String(BODY, StandardCharsets.UTF_8));

            assertThat(control("POST /fixture HTTP/1.1\r\nHost: c\r\nContent-Length: " + fixture2.length + "\r\n\r\n",
                    fixture2)).startsWith("HTTP/1.1 204");

            send(open, "GET / HTTP/1.1\r\nHost: h\r\n\r\n");
            assertThat(readResponse(open, body2.length)).as("kept-alive connection sees the new fixture")
                    .endsWith(new String(body2, StandardCharsets.UTF_8));
            try (var fresh = connect(port)) {
                send(fresh, "GET / HTTP/1.1\r\nHost: h\r\n\r\n");
                assertThat(readResponse(fresh, body2.length)).endsWith(new String(body2, StandardCharsets.UTF_8));
            }
        } finally {
            server.respondWith(FIXTURE);
        }
    }

    @Test
    void controlPortRejectsBadFixturesAndOtherRequests() throws Exception {
        byte[] garbage = "not a response".getBytes(StandardCharsets.US_ASCII);
        assertThat(control("POST /fixture HTTP/1.1\r\nHost: c\r\nContent-Length: " + garbage.length + "\r\n\r\n",
                garbage)).startsWith("HTTP/1.1 400");
        byte[] wrongLength = "HTTP/1.1 200 OK\r\nContent-Length: 99\r\n\r\nabc".getBytes(StandardCharsets.US_ASCII);
        assertThat(control("POST /fixture HTTP/1.1\r\nHost: c\r\nContent-Length: " + wrongLength.length + "\r\n\r\n",
                wrongLength)).startsWith("HTTP/1.1 400");
        assertThat(control("POST /fixture HTTP/1.1\r\nHost: c\r\n\r\n", new byte[0]))
                .as("a fixture needs a Content-Length")
                .startsWith("HTTP/1.1 400");
        assertThat(control("GET /fixture HTTP/1.1\r\nHost: c\r\n\r\n", new byte[0])).startsWith("HTTP/1.1 404");
        assertThat(control("POST /other HTTP/1.1\r\nHost: c\r\nContent-Length: 0\r\n\r\n", new byte[0]))
                .startsWith("HTTP/1.1 404");
        // The data port still serves the original fixture after all of that.
        try (var client = connect(port)) {
            send(client, "GET / HTTP/1.1\r\nHost: h\r\n\r\n");
            assertThat(readResponse(client, BODY.length)).isEqualTo(HEAD + new String(BODY, StandardCharsets.UTF_8));
        }
    }

    @Test
    void answers503UntilAFixtureIsSet() throws Exception {
        var fresh = new FixtureServer();
        try (var listener = ServerSocketChannel.open()) {
            listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            Thread.ofPlatform().daemon().start(() -> {
                try {
                    fresh.serve(listener, null);
                } catch (IOException e) {}
            });
            try (var client = connect(((InetSocketAddress) listener.getLocalAddress()).getPort())) {
                send(client, "GET / HTTP/1.1\r\nHost: h\r\n\r\n");
                assertThat(readResponse(client, 0)).startsWith("HTTP/1.1 503 Service Unavailable");
            }
        }
    }

    @Test
    void validatesFixtures() {
        assertThat(FixtureServer.isValidResponse(FIXTURE)).isTrue();
        assertThat(FixtureServer.isValidResponse("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes())).isTrue();
        assertThat(FixtureServer.isValidResponse("HTTP/1.1 200 OK\r\n\r\n".getBytes())).as("needs Content-Length")
                .isFalse();
        assertThat(FixtureServer.isValidResponse("HTTP/1.1 200 OK\r\nContent-Length: 1\r\n\r\n".getBytes()))
                .as("short body")
                .isFalse();
        assertThat(FixtureServer.isValidResponse("HTTP/1.1 200 OK\r\nContent-Length: x\r\n\r\n".getBytes())).isFalse();
        assertThat(FixtureServer.isValidResponse("HTTP/1.0 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes())).isFalse();
        assertThat(FixtureServer.isValidResponse("HTTP/1.1 200 OK\r\nContent-Length: 0".getBytes())).isFalse();
    }

    @Test
    void parsesTheServerCommandLine() {
        var o = FixtureServer.Options.parse(new String[] {"--listen", "127.0.0.1:0", "--control", "127.0.0.1:0"});
        assertThat(o.listen.getPort()).isZero();
        assertThat(o.control.getPort()).isZero();
        assertThat(o.help).isFalse();
        assertThat(FixtureServer.Options.parse(new String[0]).listen.getAddress().isLoopbackAddress()).isTrue();
        assertThatThrownBy(() -> FixtureServer.Options.parse(new String[] {"--listen", "nonsense"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host:port");
        assertThatThrownBy(() -> FixtureServer.Options.parse(new String[] {"--body", "x"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown argument");
    }

    private static String control(String head, byte[] body) throws IOException {
        try (var socket = connect(controlPort)) {
            socket.getOutputStream().write(head.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(body);
            socket.getOutputStream().flush();
            return readLine(socket.getInputStream());
        }
    }

    private static Socket connect(int port) throws IOException {
        var socket = new Socket();
        socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2_000);
        socket.setSoTimeout(5_000);
        return socket;
    }

    private static void send(Socket socket, String text) throws IOException {
        socket.getOutputStream().write(text.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

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

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
