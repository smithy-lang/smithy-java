/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.HttpClient;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.TestUtils;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h2.EchoHttp2ClientHandler;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Proves per-message flushing of a streaming request body over HTTP/2: each small message is sent as its
 * own DATA frame, not held in the output buffer until it fills or the stream closes. This is what lets a
 * send-then-await-reply event protocol work without deadlocking.
 *
 * <p>The request body ({@link PingPongDataStream}) writes one message, then blocks until the test has read
 * that message's echo, before writing the next. The server ({@link EchoHttp2ClientHandler}) echoes each
 * request DATA frame into the response. The body writes via {@link DataStream#writeTo} and flushes after
 * each message, like the event stream writer's DataStream.
 *
 * <p>Verified discriminator: on a coalescing path (transferTo, no per-message flush) message 0 never
 * reaches the server, so its echo never arrives, the body never unblocks, and the test times out.
 */
public class PerMessageFlushHttp2Test extends BaseHttpClientIntegTest {

    private static final List<String> MESSAGES = List.of("syn", "msg-1", "msg-2", "fin");

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        return builder
                .httpVersion(HttpVersion.HTTP_2)
                .h2ConnectionMode(NettyTestServer.H2ConnectionMode.PRIOR_KNOWLEDGE)
                .http2HandlerFactory(ctx -> new EchoHttp2ClientHandler());
    }

    @Override
    protected HttpClient.Builder configureClient(HttpClient.Builder builder) {
        return builder.httpVersionPolicy(HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE);
    }

    @Test
    @Timeout(30)
    void sendsEachMessageAsItsOwnFrameAndAwaitsEcho() throws Exception {
        // Hands each message's echo (read from the response) back to the body so it can proceed. A
        // non-blocking handoff (not SynchronousQueue) so the reader never blocks waiting for the writer
        // to be poised at its poll().
        var echoes = new LinkedBlockingQueue<byte[]>();
        var body = new PingPongDataStream(MESSAGES, echoes);

        var request = TestUtils.request(HttpVersion.HTTP_2, uri(), body);

        try {
            var response = client.send(request);
            assertEquals(200, response.statusCode());

            try (InputStream in = response.body().asInputStream()) {
                var roundTripped = new StringBuilder();
                for (String message : MESSAGES) {
                    byte[] expected = message.getBytes(StandardCharsets.UTF_8);
                    byte[] echo = readN(in, expected.length);
                    assertArrayEquals(expected, echo, "echo of " + message);
                    roundTripped.append(new String(echo, StandardCharsets.UTF_8));
                    // Release the body to write the next message only now that this one's echo is in hand.
                    echoes.add(echo);
                }
                assertEquals(-1, in.read(), "expected end of response after final message");
                assertEquals(String.join("", MESSAGES), roundTripped.toString());
            }
        } finally {
            body.unblock();
        }
    }

    private static byte[] readN(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int read = 0;
        while (read < n) {
            int r = in.read(buf, read, n - read);
            if (r < 0) {
                throw new AssertionError("response ended early: wanted " + n + " bytes, got " + read);
            }
            read += r;
        }
        return buf;
    }

    /**
     * A request body that writes one message, flushes it, then blocks until the test has read that
     * message's echo, modelling a send-then-await-reply event protocol.
     */
    private static final class PingPongDataStream implements DataStream {
        private final List<String> messages;
        private final LinkedBlockingQueue<byte[]> echoes;
        private volatile boolean unblocked;

        PingPongDataStream(List<String> messages, LinkedBlockingQueue<byte[]> echoes) {
            this.messages = messages;
            this.echoes = echoes;
        }

        void unblock() {
            unblocked = true;
        }

        // Blocks until this message's echo has been read by the test, so the producer cannot run ahead of
        // the response. Returns false if the test is tearing down.
        private boolean awaitEcho(int messageIndex) throws IOException {
            if (messageIndex + 1 >= messages.size()) {
                return true; // last message: nothing to wait for
            }
            try {
                while (echoes.poll(1, TimeUnit.SECONDS) == null) {
                    if (unblocked) {
                        return false;
                    }
                }
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted awaiting echo", e);
            }
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            for (int i = 0; i < messages.size(); i++) {
                out.write(messages.get(i).getBytes(StandardCharsets.UTF_8));
                out.flush(); // one message == one DATA frame
                if (!awaitEcho(i)) {
                    return;
                }
            }
        }

        // A real InputStream view with the same ping-pong gating, so on a coalescing transport
        // (transferTo) the test deadlocks for real rather than failing on a stubbed-out method.
        @Override
        public InputStream asInputStream() {
            return new InputStream() {
                private int index;
                private byte[] currentBytes;
                private int currentPos;

                @Override
                public int read() throws IOException {
                    byte[] one = new byte[1];
                    int n = read(one, 0, 1);
                    return n < 0 ? -1 : (one[0] & 0xFF);
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    if (currentBytes == null) {
                        if (index >= messages.size()) {
                            return -1;
                        }
                        // Gate on the previous message's echo before surfacing the next message's bytes.
                        if (index > 0 && !awaitEcho(index - 1)) {
                            return -1;
                        }
                        currentBytes = messages.get(index).getBytes(StandardCharsets.UTF_8);
                        currentPos = 0;
                        index++;
                    }
                    int n = Math.min(len, currentBytes.length - currentPos);
                    System.arraycopy(currentBytes, currentPos, b, off, n);
                    currentPos += n;
                    if (currentPos == currentBytes.length) {
                        currentBytes = null;
                    }
                    return n;
                }
            };
        }

        @Override
        public long contentLength() {
            return -1;
        }

        @Override
        public String contentType() {
            return "application/octet-stream";
        }

        @Override
        public boolean isReplayable() {
            return false;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }
}
