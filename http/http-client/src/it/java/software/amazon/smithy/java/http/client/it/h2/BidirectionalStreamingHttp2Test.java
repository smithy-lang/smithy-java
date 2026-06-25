/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
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
 * Proves true HTTP/2 bidirectional (full-duplex) streaming: the response is read while the request body is
 * still open and unfinished.
 *
 * <p>The request body ({@link BlockingInputStream}) hands over a leading payload and then <em>blocks
 * indefinitely</em> before signalling end-of-stream — it only unblocks once the test thread releases it,
 * which the test does only <em>after</em> {@code client.send(...)} has returned and the leading echo has
 * been read. The server ({@link EchoHttp2ClientHandler}) sends response HEADERS as soon as the request
 * HEADERS arrive and echoes each request DATA frame straight back.
 *
 * <p>This is a strict discriminator for duplex:
 * <ul>
 *   <li><b>Full duplex (correct):</b> the client writes the request body on a background virtual thread
 *       (see {@code DefaultHttpClient.sendForRoute}), so {@code send()} returns on the response HEADERS
 *       while the body is still blocked. The test reads the leading echo, then releases the body to finish.</li>
 *   <li><b>Serialized (broken):</b> if the client wrote the request body inline before reading the
 *       response, {@code send()} would block inside the body write forever — the test thread that releases
 *       the body never runs — and the test fails on the timeout.</li>
 * </ul>
 *
 * <p>Verified to be a real discriminator: forcing the client onto the inline-write path makes this test
 * hang to the timeout, while the duplex path passes.
 */
public class BidirectionalStreamingHttp2Test extends BaseHttpClientIntegTest {

    // Larger than the 16 KB H2 frame buffer so the client flushes a DATA frame for it before the body
    // blocks (an OutputStream-backed body only auto-flushes once its frame buffer fills). Position-
    // dependent bytes catch a truncated or misordered echo.
    private static final byte[] LEADING_PAYLOAD = makeLeadingPayload(64 * 1024);

    private static byte[] makeLeadingPayload(int size) {
        byte[] b = new byte[size];
        for (int i = 0; i < size; i++) {
            b[i] = (byte) (i * 31 + 7);
        }
        return b;
    }

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
    void readsResponseWhileRequestBodyIsStillOpen() throws Exception {
        var releaseRequest = new CountDownLatch(1);
        var body = DataStream.ofInputStream(
                new BlockingInputStream(LEADING_PAYLOAD, releaseRequest),
                "application/octet-stream");
        var request = TestUtils.request(HttpVersion.HTTP_2, uri(), body);

        try {
            // With duplex, send() returns on the response HEADERS even though the request body is still
            // blocked mid-stream. Without it, this call would never return and the test would hit @Timeout.
            var response = client.send(request);
            assertEquals(200, response.statusCode());

            try (InputStream in = response.body().asInputStream()) {
                // Read one full 16 KB frame of the echo while the request body is still blocked. Receiving
                // any response body before releasing the request is the proof that the two directions
                // interleave. (Reading the whole leading payload could block on bytes the client hasn't
                // flushed yet, since an OutputStream-backed body only flushes on a full frame buffer or
                // close — one frame is enough.)
                int prefixLen = 16 * 1024;
                byte[] leadingEcho = readN(in, prefixLen);
                byte[] expectedPrefix = Arrays.copyOf(LEADING_PAYLOAD, prefixLen);
                assertArrayEquals(expectedPrefix, leadingEcho, "leading echo read while request still open");

                // Now let the request body finish; the server echoes the rest plus END_STREAM. Drain the
                // rest and confirm the full leading payload round-tripped.
                releaseRequest.countDown();
                byte[] rest = in.readAllBytes();
                byte[] full = new byte[leadingEcho.length + rest.length];
                System.arraycopy(leadingEcho, 0, full, 0, leadingEcho.length);
                System.arraycopy(rest, 0, full, leadingEcho.length, rest.length);
                assertArrayEquals(LEADING_PAYLOAD, full, "full echoed request body");
            }
        } finally {
            // Ensure the background request-writer VT is never left blocked, even if an assertion above fails.
            releaseRequest.countDown();
        }
    }

    private static byte[] readN(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int read = 0;
        while (read < n) {
            int r = in.read(buf, read, n - read);
            if (r < 0) {
                throw new AssertionError("response stream ended early: wanted " + n + " bytes, got " + read);
            }
            read += r;
        }
        return buf;
    }

    /**
     * Emits {@code leading} bytes, then blocks on {@code release} before returning EOF. Models a request
     * whose producer is still working: the body is open and unfinished until the test releases it.
     */
    private static final class BlockingInputStream extends InputStream {
        private final byte[] leading;
        private final CountDownLatch release;
        private int pos;
        private boolean released;

        BlockingInputStream(byte[] leading, CountDownLatch release) {
            this.leading = leading;
            this.release = release;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : (one[0] & 0xFF);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (pos < leading.length) {
                int n = Math.min(len, leading.length - pos);
                System.arraycopy(leading, pos, b, off, n);
                pos += n;
                return n;
            }
            if (!released) {
                try {
                    // Block indefinitely until the test releases the request, modelling an in-progress
                    // upload. Crucially there is no self-timeout: a non-duplex client that writes the body
                    // inline before reading the response would block here forever (the test thread that
                    // releases this never gets to run), so the test fails via @Timeout rather than passing
                    // slowly. That is what makes this a real duplex discriminator.
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while blocked on request release", e);
                }
                released = true;
            }
            return -1;
        }
    }
}
