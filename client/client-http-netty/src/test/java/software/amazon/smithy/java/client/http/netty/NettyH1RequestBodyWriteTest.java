/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Regression test for the request-body write path. After the zero-copy redesign the H1 transport
 * must send a <em>resident</em> ({@code isReplayable()}) request body without copying it into
 * transport-allocated buffers and without materializing it through {@link DataStream#asInputStream()}
 * or {@link DataStream#asChannel()}:
 *
 * <ul>
 *   <li>A single-buffer body ({@code hasByteBuffer()==true}, e.g. a serialized payload or
 *       {@link DataStream#ofBytes}) is wrapped via {@link DataStream#asByteBuffer()} and written as
 *       one {@code LastHttpContent} — no {@code writeTo}, no {@code asInputStream}.</li>
 *   <li>A multi-buffer/framed resident body ({@code hasByteBuffer()==false} but replayable, e.g. the
 *       SigV4 {@code aws-chunked} upload) is gathered via {@link DataStream#subscribe} (zero-copy
 *       views) — again no {@code writeTo}, no {@code asInputStream}.</li>
 * </ul>
 *
 * <p>Only non-replayable streaming bodies use {@code writeTo(OutputStream)}; those are covered
 * separately. The old path materialized {@code AwsChunkedDataStream} through {@code asChannel()} and
 * then {@code asInputStream()} (double CRC32, ~15% caller CPU); this guards against any return to a
 * materializing path.
 */
class NettyH1RequestBodyWriteTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsSingleBufferBodyViaByteBufferWithoutMaterializing() throws Exception {
        var received = new AtomicInteger();
        startEchoLengthServer(received);

        byte[] payload = sequentialBytes(256 * 1024);
        var counting = new CountingDataStream(DataStream.ofBytes(payload, "application/octet-stream"));

        sendPut(counting);

        // Server saw the full body...
        assertThat(received.get(), equalTo(payload.length));
        // ...sent zero-copy via asByteBuffer(), never materialized.
        assertThat("asByteBuffer should be used", counting.asByteBufferCalls.get(), greaterThan(0));
        assertThat("writeTo must not be called", counting.writeToCalls.get(), equalTo(0));
        assertThat("asInputStream must not be called", counting.asInputStreamCalls.get(), equalTo(0));
        assertThat("asChannel must not be called", counting.asChannelCalls.get(), equalTo(0));
    }

    @Test
    void sendsMultiBufferResidentBodyViaSubscribeWithoutMaterializing() throws Exception {
        var received = new AtomicInteger();
        startEchoLengthServer(received);

        // A resident, replayable body that reports hasByteBuffer()==false and emits its bytes only
        // via subscribe() — the shape of AwsChunkedDataStream on the S3 upload path.
        byte[] payload = sequentialBytes(200 * 1024);
        var counting = new CountingDataStream(new SubscribeOnlyDataStream(payload));

        sendPut(counting);

        assertThat(received.get(), equalTo(payload.length));
        assertThat("subscribe should be used", counting.subscribeCalls.get(), greaterThan(0));
        assertThat("writeTo must not be called", counting.writeToCalls.get(), equalTo(0));
        assertThat("asInputStream must not be called", counting.asInputStreamCalls.get(), equalTo(0));
        assertThat("asChannel must not be called", counting.asChannelCalls.get(), equalTo(0));
    }

    private void startEchoLengthServer(AtomicInteger received) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/put", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            received.set(body.length);
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();
    }

    private void sendPut(DataStream body) throws IOException {
        var config = new NettyHttpTransportConfig().maxConnectionsPerHost(1);
        var transport = new NettyHttpClientTransport(config);
        try {
            String uri = "http://127.0.0.1:" + server.getAddress().getPort() + "/put";
            HttpRequest request = HttpRequest.create()
                    .setMethod("PUT")
                    .setUri(URI.create(uri))
                    .setHttpVersion(HttpVersion.HTTP_1_1)
                    .setBody(body)
                    .toUnmodifiable();
            HttpResponse response = transport.send(Context.create(), request);
            assertThat(response.statusCode(), equalTo(200));
            try (var b = response.body().asInputStream()) {
                b.readAllBytes();
            }
        } finally {
            transport.close();
        }
    }

    private static byte[] sequentialBytes(int len) {
        byte[] payload = new byte[len];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }
        return payload;
    }

    /**
     * A resident, replayable {@link DataStream} that exposes its bytes ONLY through
     * {@link #subscribe} (reports {@code hasByteBuffer()==false}), emitting them as multiple
     * zero-copy buffer slices — mirroring how {@code AwsChunkedDataStream} frames an upload.
     */
    private static final class SubscribeOnlyDataStream implements DataStream {
        private final byte[] bytes;

        SubscribeOnlyDataStream(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public boolean isReplayable() {
            return true;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean hasByteBuffer() {
            return false;
        }

        @Override
        public long contentLength() {
            return bytes.length;
        }

        @Override
        public boolean hasKnownLength() {
            return true;
        }

        @Override
        public String contentType() {
            return "application/octet-stream";
        }

        @Override
        public InputStream asInputStream() {
            throw new UnsupportedOperationException("subscribe-only");
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                private boolean done;

                @Override
                public void request(long n) {
                    if (done || n <= 0) {
                        return;
                    }
                    done = true;
                    // Emit in two slices to exercise the multi-component gather path.
                    int mid = bytes.length / 2;
                    subscriber.onNext(ByteBuffer.wrap(bytes, 0, mid));
                    subscriber.onNext(ByteBuffer.wrap(bytes, mid, bytes.length - mid));
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                    done = true;
                }
            });
        }
    }

    /** Wraps a DataStream and counts which consumption methods the transport invokes. */
    private static final class CountingDataStream implements DataStream {
        private final DataStream delegate;
        final AtomicInteger writeToCalls = new AtomicInteger();
        final AtomicInteger asInputStreamCalls = new AtomicInteger();
        final AtomicInteger asChannelCalls = new AtomicInteger();
        final AtomicInteger asByteBufferCalls = new AtomicInteger();
        final AtomicInteger subscribeCalls = new AtomicInteger();

        CountingDataStream(DataStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            writeToCalls.incrementAndGet();
            delegate.writeTo(out);
        }

        @Override
        public InputStream asInputStream() {
            asInputStreamCalls.incrementAndGet();
            return delegate.asInputStream();
        }

        @Override
        public ReadableByteChannel asChannel() {
            asChannelCalls.incrementAndGet();
            return delegate.asChannel();
        }

        @Override
        public ByteBuffer asByteBuffer() {
            asByteBufferCalls.incrementAndGet();
            return delegate.asByteBuffer();
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            subscribeCalls.incrementAndGet();
            delegate.subscribe(subscriber);
        }

        @Override
        public boolean hasByteBuffer() {
            return delegate.hasByteBuffer();
        }

        @Override
        public long contentLength() {
            return delegate.contentLength();
        }

        @Override
        public String contentType() {
            return delegate.contentType();
        }

        @Override
        public boolean isReplayable() {
            return delegate.isReplayable();
        }

        @Override
        public boolean isAvailable() {
            return delegate.isAvailable();
        }

        @Override
        public boolean hasKnownLength() {
            return delegate.hasKnownLength();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
