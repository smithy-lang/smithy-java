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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Regression test for the Phase 0 request-body fix: the H1 transport must send the request body via
 * {@link DataStream#writeTo(OutputStream)} exactly once, and must NOT materialize it through
 * {@link DataStream#asInputStream()} or probe {@link DataStream#asChannel()}.
 *
 * <p>The old path called {@code asChannel()} (which for non-{@code ScatteringByteChannel} bodies —
 * e.g. {@code AwsChunkedDataStream} — materialized the entire encoded body into a
 * {@code ByteArrayOutputStream} and then discarded it) and then {@code asInputStream()} to
 * materialize it a SECOND time. That double materialization was ~15% of caller-thread CPU and a
 * double CRC32 pass on the S3 upload path.
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
    void sendsBodyViaWriteToWithoutMaterializing() throws Exception {
        var received = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/put", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            received.set(body.length);
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();

        byte[] payload = new byte[256 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }
        var counting = new CountingDataStream(DataStream.ofBytes(payload, "application/octet-stream"));

        var config = new NettyHttpTransportConfig().maxConnectionsPerHost(1);
        var transport = new NettyHttpClientTransport(config);
        try {
            String uri = "http://127.0.0.1:" + server.getAddress().getPort() + "/put";
            HttpRequest request = HttpRequest.create()
                    .setMethod("PUT")
                    .setUri(URI.create(uri))
                    .setHttpVersion(HttpVersion.HTTP_1_1)
                    .setBody(counting)
                    .toUnmodifiable();
            HttpResponse response = transport.send(Context.create(), request);
            assertThat(response.statusCode(), equalTo(200));
            try (var b = response.body().asInputStream()) {
                b.readAllBytes();
            }
        } finally {
            transport.close();
        }

        // Server saw the full body...
        assertThat(received.get(), equalTo(payload.length));
        // ...sent via writeTo, with zero materialization through asInputStream()/asChannel().
        assertThat("writeTo should be used", counting.writeToCalls.get(), greaterThan(0));
        assertThat("asInputStream must not be called", counting.asInputStreamCalls.get(), equalTo(0));
        assertThat("asChannel must not be called", counting.asChannelCalls.get(), equalTo(0));
    }

    /** Wraps a DataStream and counts which consumption methods the transport invokes. */
    private static final class CountingDataStream implements DataStream {
        private final DataStream delegate;
        final AtomicInteger writeToCalls = new AtomicInteger();
        final AtomicInteger asInputStreamCalls = new AtomicInteger();
        final AtomicInteger asChannelCalls = new AtomicInteger();

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
            return delegate.asByteBuffer();
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
