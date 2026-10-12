/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.datastream.PooledDataStream;

class AwsChunkedDataStreamTest {

    @Test
    void writeToProducesAwsChunkedFraming() throws Exception {
        // 1024 bytes of repeating "abcdefgh" — enough to need multi-char hex size.
        byte[] payload = new byte[1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) ('a' + (i % 8));
        }
        var inner = DataStream.ofBytes(payload, "application/octet-stream");
        var chunked = AwsChunkedDataStream.of(inner);

        var out = new ByteArrayOutputStream();
        chunked.writeTo(out);
        byte[] encoded = out.toByteArray();

        // Frame format: 400\r\n<1024 bytes>\r\n0\r\nx-amz-checksum-crc32:<base64>\r\n\r\n
        var crc = new CRC32();
        crc.update(payload);
        long v = crc.getValue();
        byte[] crcBytes = {
                (byte) ((v >>> 24) & 0xff),
                (byte) ((v >>> 16) & 0xff),
                (byte) ((v >>> 8) & 0xff),
                (byte) (v & 0xff)};
        String crcB64 = Base64.getEncoder().encodeToString(crcBytes);

        String expected = "400\r\n"
                + new String(payload, StandardCharsets.US_ASCII)
                + "\r\n0\r\nx-amz-checksum-crc32:" + crcB64 + "\r\n\r\n";
        assertArrayEquals(expected.getBytes(StandardCharsets.US_ASCII), encoded);
    }

    @Test
    void contentLengthMatchesActualEncoding() throws Exception {
        for (int size : new int[] {1, 15, 16, 255, 256, 1024, 16384, 262144}) {
            byte[] payload = new byte[size];
            var chunked = AwsChunkedDataStream.of(DataStream.ofBytes(payload, null));

            var out = new ByteArrayOutputStream();
            chunked.writeTo(out);

            assertThat("size=" + size, (long) out.size(), equalTo(chunked.contentLength()));
        }
    }

    @Test
    void subscribeProducesSameBytesAsWriteTo() throws Exception {
        byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
        var chunked = AwsChunkedDataStream.of(DataStream.ofBytes(payload, null));

        var sync = new ByteArrayOutputStream();
        chunked.writeTo(sync);

        var frames = subscribe(chunked);

        assertArrayEquals(sync.toByteArray(), concat(frames.frames));
        assertThat(frames.calls, equalTo(List.of("next", "next", "next", "complete")));
    }

    @Test
    void subscribeDoesNotCopyAnUnpooledBody() {
        byte[] payload = "zero copy".getBytes(StandardCharsets.UTF_8);
        var chunked = AwsChunkedDataStream.of(DataStream.ofBytes(payload, null));

        var frames = subscribe(chunked).frames;

        assertThat(frames.size(), equalTo(3));
        assertThat(frames.get(1).array(), sameInstance(payload));
    }

    @Test
    void forEachBufferAndAsByteBufferMirrorWriteTo() throws Exception {
        byte[] payload = "hello buffers".getBytes(StandardCharsets.UTF_8);
        var chunked = AwsChunkedDataStream.of(DataStream.ofBytes(payload, null));

        var written = new ByteArrayOutputStream();
        chunked.writeTo(written);

        var visited = new ByteArrayOutputStream();
        chunked.forEachBuffer(b -> {
            byte[] tmp = new byte[b.remaining()];
            b.get(tmp);
            visited.writeBytes(tmp);
        });
        assertArrayEquals(written.toByteArray(), visited.toByteArray());

        var first = chunked.asByteBuffer();
        assertArrayEquals(written.toByteArray(), first.array());
        assertThat(chunked.asByteBuffer().array(), sameInstance(first.array()));
        assertArrayEquals(written.toByteArray(), chunked.asInputStream().readAllBytes());
    }

    @Test
    void replayableProducesIdenticalEncodingAcrossCalls() throws Exception {
        byte[] payload = "stable".getBytes(StandardCharsets.UTF_8);
        var chunked = AwsChunkedDataStream.of(DataStream.ofBytes(payload, null));

        var first = new ByteArrayOutputStream();
        chunked.writeTo(first);
        var second = new ByteArrayOutputStream();
        chunked.writeTo(second);

        assertArrayEquals(first.toByteArray(), second.toByteArray());
        assertThat(chunked.isReplayable(), is(true));
    }

    @Test
    void emptyBodyComputesAllZeroCrc() throws Exception {
        var chunked = AwsChunkedDataStream.of(DataStream.ofBytes(new byte[0], null));
        var out = new ByteArrayOutputStream();
        chunked.writeTo(out);
        // CRC32 of empty bytes is 0 → base64("\0\0\0\0") == "AAAAAA==".
        String expected = "0\r\n\r\n0\r\nx-amz-checksum-crc32:AAAAAA==\r\n\r\n";
        assertArrayEquals(expected.getBytes(StandardCharsets.US_ASCII), out.toByteArray());
    }

    @Test
    void rejectsNonReplayableInput() {
        var nonReplayable = DataStream.ofInputStream(new ByteArrayInputStream("x".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> AwsChunkedDataStream.of(nonReplayable));
    }

    @Test
    void decodedLengthReflectsInnerLength() {
        var chunked = AwsChunkedDataStream.of(DataStream.ofBytes(new byte[42], null));
        assertThat(chunked.decodedLength(), equalTo(42L));
    }

    @Test
    void singleSubscriberCannotBeRequestedTwice() {
        var chunked = AwsChunkedDataStream.of(DataStream.ofBytes(new byte[4], null));
        var calls = new ArrayList<String>();
        chunked.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
                s.request(Long.MAX_VALUE);
                // Second request after first should be a no-op.
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                calls.add("next");
            }

            @Override
            public void onError(Throwable throwable) {
                calls.add("error");
            }

            @Override
            public void onComplete() {
                calls.add("complete");
            }
        });
        // Frames: header + body + footer + complete = 3 onNext + 1 onComplete.
        assertThat(calls, equalTo(List.of("next", "next", "next", "complete")));
    }

    @Test
    void plainInnerIsNotPooled() {
        assertThat(AwsChunkedDataStream.of(DataStream.ofBytes(new byte[4])), not(instanceOf(PooledDataStream.class)));
    }

    @Test
    void writeToAndForEachBufferDoNotAliasAPooledBody() throws Exception {
        byte[] payload = "hello chunked world".getBytes(StandardCharsets.UTF_8);
        var recycled = new AtomicReference<byte[]>();
        PooledDataStream pooled = DataStream.ofLeasedBytes(payload, 0, payload.length, "text/plain", recycled::set);

        var chunked = AwsChunkedDataStream.of(pooled);
        assertThat(chunked, instanceOf(PooledDataStream.class));
        assertThat(chunked.contentType(), equalTo("text/plain"));

        var written = new ByteArrayOutputStream();
        chunked.writeTo(written);
        assertArrayEquals(encode(payload), written.toByteArray());

        var visited = new ByteArrayOutputStream();
        chunked.forEachBuffer(b -> {
            byte[] tmp = new byte[b.remaining()];
            b.get(tmp);
            visited.writeBytes(tmp);
        });
        assertArrayEquals(encode(payload), visited.toByteArray());

        var lease = (PooledDataStream) chunked;
        assertThat(lease.escaped(), is(false));
        assertThat(pooled.escaped(), is(false));
        assertThat(lease.isReleased(), is(false));

        lease.release();
        assertThat(lease.isReleased(), is(true));
        assertThat(pooled.isReleased(), is(true));
        assertThat(recycled.get(), sameInstance(payload));
    }

    @Test
    void readsAfterReleaseThrowBeforeEmittingAnyByte() throws Exception {
        byte[] payload = "late".getBytes(StandardCharsets.UTF_8);
        PooledDataStream pooled = DataStream.ofLeasedBytes(payload, 0, payload.length, null, array -> {});
        var chunked = AwsChunkedDataStream.of(pooled);
        var out = new ByteArrayOutputStream();
        chunked.writeTo(out);
        pooled.release();

        assertThrows(IllegalStateException.class, () -> chunked.writeTo(out));
        assertArrayEquals(encode(payload), out.toByteArray());

        var visited = new ArrayList<ByteBuffer>();
        assertThrows(IllegalStateException.class, () -> chunked.forEachBuffer(visited::add));
        assertThat(visited.isEmpty(), is(true));
        assertThrows(IllegalStateException.class, chunked::asByteBuffer);
        assertThrows(IllegalStateException.class, () -> chunked.subscribe(new CollectingSubscriber()));
        assertThrows(IllegalStateException.class, ((PooledDataStream) chunked)::copy);
    }

    @Test
    void subscribeOnAPooledBodyPublishesTheSharedPrivateCopy() {
        byte[] payload = "publish me".getBytes(StandardCharsets.UTF_8);
        var recycled = new AtomicReference<byte[]>();
        PooledDataStream pooled = DataStream.ofLeasedBytes(payload, 0, payload.length, null, recycled::set);
        var chunked = AwsChunkedDataStream.of(pooled);

        var frames = subscribe(chunked).frames;

        assertArrayEquals(encode(payload), concat(frames));
        assertThat(frames.size(), equalTo(1));
        assertThat(frames.get(0).array(), sameInstance(chunked.asByteBuffer().array()));
        assertThat(pooled.escaped(), is(false));
        assertThat(((PooledDataStream) chunked).escaped(), is(true));

        ((PooledDataStream) chunked).release();
        assertThat(recycled.get(), sameInstance(payload));
    }

    @Test
    void asByteBufferOnAPooledBodyIsOnePrivateCopy() throws Exception {
        byte[] payload = "copy me once".getBytes(StandardCharsets.UTF_8);
        var recycled = new AtomicReference<byte[]>();
        PooledDataStream pooled = DataStream.ofLeasedBytes(payload, 0, payload.length, null, recycled::set);
        var chunked = AwsChunkedDataStream.of(pooled);

        var encoded = chunked.asByteBuffer();
        assertArrayEquals(encode(payload), encoded.array());
        assertThat(chunked.asByteBuffer().array(), sameInstance(encoded.array()));
        assertArrayEquals(encode(payload), chunked.asInputStream().readAllBytes());
        assertThat(((PooledDataStream) chunked).escaped(), is(true));
        assertThat(pooled.escaped(), is(false));

        ((PooledDataStream) chunked).release();
        assertThat(recycled.get(), sameInstance(payload));
    }

    @Test
    void cachedReadsAlsoThrowAfterRelease() {
        byte[] payload = "strict".getBytes(StandardCharsets.UTF_8);
        PooledDataStream pooled = DataStream.ofLeasedBytes(payload, 0, payload.length, null, array -> {});
        var chunked = AwsChunkedDataStream.of(pooled);
        var view = chunked.asByteBuffer();
        pooled.release();

        assertArrayEquals(encode(payload), view.array());
        assertThrows(IllegalStateException.class, chunked::asByteBuffer);
        assertThrows(IllegalStateException.class, chunked::asInputStream);
        assertThrows(IllegalStateException.class, () -> chunked.subscribe(new CollectingSubscriber()));
        assertThrows(IllegalStateException.class, ((PooledDataStream) chunked)::copy);
    }

    @Test
    void copyOfAPooledBodyIsEncodedAndUnpooled() throws Exception {
        byte[] payload = "retain me".getBytes(StandardCharsets.UTF_8);
        var recycled = new AtomicReference<byte[]>();
        PooledDataStream pooled = DataStream.ofLeasedBytes(payload, 0, payload.length, "text/plain", recycled::set);
        var chunked = (PooledDataStream) AwsChunkedDataStream.of(pooled);

        var copy = chunked.copy();
        assertThat(chunked.escaped(), is(false));
        chunked.release();

        assertThat(copy, not(instanceOf(PooledDataStream.class)));
        assertThat(copy.contentType(), equalTo("text/plain"));
        assertThat(copy.contentLength(), equalTo(chunked.contentLength()));
        var out = new ByteArrayOutputStream();
        copy.writeTo(out);
        assertArrayEquals(encode(payload), out.toByteArray());
        assertThat(recycled.get(), sameInstance(payload));
    }

    @Test
    void copyReusesAnExistingEncodedCopy() {
        byte[] payload = "already encoded".getBytes(StandardCharsets.UTF_8);
        PooledDataStream pooled = DataStream.ofLeasedBytes(payload, 0, payload.length, "text/plain", array -> {});
        var chunked = AwsChunkedDataStream.of(pooled);

        var encoded = chunked.asByteBuffer();
        var copy = ((PooledDataStream) chunked).copy();

        assertThat(copy, not(instanceOf(PooledDataStream.class)));
        assertThat(copy.contentType(), equalTo("text/plain"));
        assertThat(copy.contentLength(), equalTo(chunked.contentLength()));
        assertThat(copy.asByteBuffer().array(), sameInstance(encoded.array()));
    }

    @Test
    void subscribePublishesTheCachedCopyWhenPresent() {
        byte[] payload = "cached".getBytes(StandardCharsets.UTF_8);
        var chunked = AwsChunkedDataStream.of(DataStream.ofBytes(payload, null));

        var encoded = chunked.asByteBuffer();
        var frames = subscribe(chunked).frames;

        assertThat(frames.size(), equalTo(1));
        assertThat(frames.get(0).array(), sameInstance(encoded.array()));
    }

    @Test
    void writeToChannelDoesNotAliasAPooledBody() throws Exception {
        byte[] payload = "down the channel".getBytes(StandardCharsets.UTF_8);
        PooledDataStream pooled = DataStream.ofLeasedBytes(payload, 0, payload.length, null, array -> {});
        var chunked = AwsChunkedDataStream.of(pooled);

        var out = new ByteArrayOutputStream();
        chunked.writeTo(Channels.newChannel(out));

        assertArrayEquals(encode(payload), out.toByteArray());
        assertThat(((PooledDataStream) chunked).escaped(), is(false));

        pooled.release();
        var late = new ByteArrayOutputStream();
        assertThrows(IllegalStateException.class, () -> chunked.writeTo(Channels.newChannel(late)));
        assertThat(late.size(), equalTo(0));
    }

    @Test
    void crcIsComputedOnceAcrossReads() throws Exception {
        var inner = new CountingBody("count me".getBytes(StandardCharsets.UTF_8));
        var chunked = AwsChunkedDataStream.of(inner);

        chunked.writeTo(new ByteArrayOutputStream());
        chunked.writeTo(new ByteArrayOutputStream());
        chunked.forEachBuffer(b -> {});
        chunked.asByteBuffer();
        subscribe(chunked);

        // The three visits compute the CRC, call forEachBuffer, and copy the encoded body.
        assertThat(inner.visits, equalTo(3));
    }

    @Test
    void abandonForwardsToThePooledBody() {
        byte[] payload = "abandon".getBytes(StandardCharsets.UTF_8);
        var recycled = new AtomicReference<byte[]>();
        PooledDataStream pooled = DataStream.ofLeasedBytes(payload, 0, payload.length, null, recycled::set);
        var chunked = (PooledDataStream) AwsChunkedDataStream.of(pooled);

        chunked.abandon();
        assertThat(chunked.escaped(), is(true));
        chunked.release();

        assertThat(chunked.isReleased(), is(true));
        assertThat(recycled.get(), nullValue());
    }

    private static final class CountingBody implements DataStream {
        private final DataStream delegate;
        int visits;

        CountingBody(byte[] bytes) {
            this.delegate = DataStream.ofBytes(bytes);
        }

        @Override
        public long contentLength() {
            return delegate.contentLength();
        }

        @Override
        public String contentType() {
            return null;
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
            return true;
        }

        @Override
        public ByteBuffer asByteBuffer() {
            return delegate.asByteBuffer();
        }

        @Override
        public InputStream asInputStream() {
            return delegate.asInputStream();
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            delegate.writeTo(out);
        }

        @Override
        public void forEachBuffer(Consumer<ByteBuffer> action) {
            visits++;
            delegate.forEachBuffer(action);
        }
    }

    private static CollectingSubscriber subscribe(DataStream stream) {
        var subscriber = new CollectingSubscriber();
        stream.subscribe(subscriber);
        assertThat(subscriber.calls.contains("error"), is(false));
        return subscriber;
    }

    private static final class CollectingSubscriber implements Flow.Subscriber<ByteBuffer> {
        final List<String> calls = new ArrayList<>();
        final List<ByteBuffer> frames = new ArrayList<>();

        @Override
        public void onSubscribe(Flow.Subscription s) {
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            calls.add("next");
            frames.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            calls.add("error");
        }

        @Override
        public void onComplete() {
            calls.add("complete");
        }
    }

    private static byte[] concat(List<ByteBuffer> frames) {
        var out = new ByteArrayOutputStream();
        for (var frame : frames) {
            var view = frame.duplicate();
            byte[] tmp = new byte[view.remaining()];
            view.get(tmp);
            out.writeBytes(tmp);
        }
        return out.toByteArray();
    }

    private static byte[] encode(byte[] payload) {
        var crc = new CRC32();
        crc.update(payload);
        long v = crc.getValue();
        byte[] crcBytes = {
                (byte) ((v >>> 24) & 0xff),
                (byte) ((v >>> 16) & 0xff),
                (byte) ((v >>> 8) & 0xff),
                (byte) (v & 0xff)};
        var out = new ByteArrayOutputStream();
        out.writeBytes((Long.toHexString(payload.length) + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(payload);
        out.writeBytes(("\r\n0\r\nx-amz-checksum-crc32:" + Base64.getEncoder().encodeToString(crcBytes) + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }
}
