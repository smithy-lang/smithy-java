/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class LeasedDataStreamTest {

    private static final String CONTENT = "HELLO WORLD";

    private final List<byte[]> recycled = new ArrayList<>();
    private final ByteArrayRecycler recycler = recycled::add;
    private final byte[] array = ("xx" + CONTENT + "yyy").getBytes(StandardCharsets.US_ASCII);

    private PooledDataStream stream() {
        return DataStream.ofLeasedBytes(array, 2, CONTENT.length(), "text/plain", recycler);
    }

    private static String ascii(ByteBuffer buffer) {
        return StandardCharsets.US_ASCII.decode(buffer).toString();
    }

    @Test
    void reportsMetadata() {
        var s = stream();
        assertThat(s.contentLength()).isEqualTo(11);
        assertThat(s.hasKnownLength()).isTrue();
        assertThat(s.contentType()).isEqualTo("text/plain");
        assertThat(s.isReplayable()).isTrue();
        assertThat(s.isAvailable()).isTrue();
        assertThat(s.hasByteBuffer()).isTrue();
        assertThat(s.isReleased()).isFalse();
        assertThat(s.escaped()).isFalse();
        assertThat(s.toString()).contains("length=11", "aliased=false", "abandoned=false", "released=false");
    }

    @Test
    void rejectsRangesOutsideTheArray() {
        assertThatThrownBy(() -> DataStream.ofLeasedBytes(array, 10, 10, null, recycler))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> DataStream.ofLeasedBytes(array, -1, 1, null, recycler))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> DataStream.ofLeasedBytes(array, 0, -1, null, recycler))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void writeToReadsThePooledArrayAndReleaseRecyclesOnce() throws IOException {
        var s = stream();
        var out = new ByteArrayOutputStream();
        s.writeTo(out);
        s.writeTo(out);
        assertThat(out.toString(StandardCharsets.US_ASCII)).isEqualTo(CONTENT + CONTENT);
        assertThat(s.escaped()).isFalse();

        s.release();
        assertThat(recycled).containsExactly(array);
        assertThat(s.isReleased()).isTrue();
        assertThat(s.isAvailable()).isFalse();

        s.release();
        assertThat(recycled).hasSize(1);
    }

    @Test
    void channelWriteAndForEachBufferReadThePooledArray() throws IOException {
        var s = stream();
        var out = new ByteArrayOutputStream();
        s.writeTo(Channels.newChannel(out));
        assertThat(out.toString(StandardCharsets.US_ASCII)).isEqualTo(CONTENT);

        long[] seen = {0};
        s.forEachBuffer(b -> {
            assertThat(b.hasArray()).isTrue();
            assertThat(b.array()).isSameAs(array);
            assertThat(b.arrayOffset()).isEqualTo(2);
            assertThat(b.position()).isZero();
            assertThat(b.remaining()).isEqualTo(11);
            assertThat(ascii(b.duplicate())).isEqualTo(CONTENT);
            seen[0] += b.remaining();
        });
        assertThat(seen[0]).isEqualTo(11);
        assertThat(s.escaped()).isFalse();

        s.release();
        assertThat(recycled).containsExactly(array);
    }

    @Test
    void everyReadThrowsAfterRelease() {
        var s = stream();
        s.release();
        assertThatThrownBy(() -> s.writeTo(new ByteArrayOutputStream())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> s.writeTo(Channels.newChannel(new ByteArrayOutputStream())))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> s.forEachBuffer(b -> {})).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(s::asByteBuffer).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(s::asInputStream).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(s::asChannel).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> s.subscribe(new NoopSubscriber())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(s::copy).isInstanceOf(IllegalStateException.class);
        assertThat(recycled).containsExactly(array);
        assertThat(s.escaped()).isFalse();
    }

    @Test
    void aliasingReadsAreServedFromAPrivateCopyAndTheArrayIsStillRecycled() throws IOException {
        var s = stream();
        ByteBuffer view = s.asByteBuffer();
        var in = s.asInputStream();
        assertThat(view.isReadOnly()).isFalse();
        assertThat(view.hasArray()).isTrue();
        assertThat(view.remaining()).isEqualTo(11);
        assertThat(s.escaped()).isTrue();
        assertThat(s.toString()).contains("aliased=true");

        s.release();
        assertThat(recycled).containsExactly(array);
        Arrays.fill(array, (byte) 'J');
        assertThat(ascii(view)).isEqualTo(CONTENT);
        assertThat(new String(in.readAllBytes(), StandardCharsets.US_ASCII)).isEqualTo(CONTENT);

        assertThatThrownBy(s::asByteBuffer).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void channelAndSubscriptionAreServedFromThePrivateCopyToo() throws IOException {
        var c = stream();
        var channel = c.asChannel();
        assertThat(c.escaped()).isTrue();
        c.release();
        Arrays.fill(array, (byte) 'J');
        var buf = ByteBuffer.allocate(32);
        while (channel.read(buf) != -1) {}
        buf.flip();
        assertThat(ascii(buf)).isEqualTo(CONTENT);
        assertThat(recycled).containsExactly(array);
    }

    @Test
    void subscriptionIsServedFromThePrivateCopy() {
        var t = stream();
        var received = new ArrayList<ByteBuffer>();
        var done = new CountDownLatch(1);
        t.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                received.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        assertThat(done.getCount()).isZero();
        assertThat(received).hasSize(1);
        assertThat(t.escaped()).isTrue();
        t.release();
        assertThat(recycled).containsExactly(array);
        Arrays.fill(array, (byte) 'J');
        assertThat(ascii(received.get(0))).isEqualTo(CONTENT);
    }

    @Test
    void nonAliasingReadsKeepUsingTheArrayAfterAnAlias() throws IOException {
        var s = stream();
        s.asByteBuffer();
        s.forEachBuffer(b -> assertThat(b.array()).isSameAs(array));
        var out = new ByteArrayOutputStream();
        s.writeTo(out);
        assertThat(out.toString(StandardCharsets.US_ASCII)).isEqualTo(CONTENT);
        s.release();
        assertThat(recycled).containsExactly(array);
    }

    @Test
    void closeIsNotRelease() throws IOException {
        var s = stream();
        s.close();
        assertThat(s.isReleased()).isFalse();
        assertThat(recycled).isEmpty();
        var out = new ByteArrayOutputStream();
        s.writeTo(out);
        assertThat(out.toString(StandardCharsets.US_ASCII)).isEqualTo(CONTENT);
        s.release();
        assertThat(recycled).containsExactly(array);
    }

    @Test
    void copyOutlivesRelease() {
        var s = stream();
        DataStream copy = s.copy();
        assertThat(s.escaped()).isFalse();
        assertThat(copy).isNotInstanceOf(PooledDataStream.class);
        s.release();
        assertThat(recycled).containsExactly(array);
        Arrays.fill(array, (byte) 'J');
        assertThat(copy.contentType()).isEqualTo("text/plain");
        assertThat(copy.contentLength()).isEqualTo(11);
        assertThat(ascii(copy.asByteBuffer())).isEqualTo(CONTENT);
    }

    @Test
    void metadataWrapperVisitsAndWritesWithoutAliasing() throws IOException {
        var s = stream();
        DataStream wrapped = DataStream.withMetadata(s, "application/octet-stream", -1);
        assertThat(wrapped).isNotSameAs(s);
        var out = new ByteArrayOutputStream();
        wrapped.writeTo(out);
        wrapped.forEachBuffer(b -> out.write(b.get()));
        assertThat(out.toString(StandardCharsets.US_ASCII)).isEqualTo(CONTENT + "H");
        assertThat(s.escaped()).isFalse();
        s.release();
        assertThat(recycled).containsExactly(array);
    }

    @Test
    void metadataWrapperOverAPooledBodyForwardsTheLifecycle() throws IOException {
        var s = stream();
        DataStream wrapped = DataStream.withMetadata(s, "application/octet-stream", -1);
        assertThat(wrapped).isInstanceOf(PooledDataStream.class);
        var pooledWrapper = (PooledDataStream) wrapped;
        assertThat(wrapped.contentType()).isEqualTo("application/octet-stream");
        assertThat(wrapped.contentLength()).isEqualTo(11);

        DataStream copy = pooledWrapper.copy();
        assertThat(copy).isNotInstanceOf(PooledDataStream.class);
        assertThat(copy.contentType()).isEqualTo("application/octet-stream");
        assertThat(ascii(copy.asByteBuffer())).isEqualTo(CONTENT);

        pooledWrapper.close();
        assertThat(s.isReleased()).isFalse();

        pooledWrapper.abandon();
        assertThat(s.escaped()).isTrue();
        assertThat(pooledWrapper.escaped()).isTrue();
        pooledWrapper.release();
        assertThat(s.isReleased()).isTrue();
        assertThat(pooledWrapper.isReleased()).isTrue();
        assertThat(recycled).as("abandoned through the wrapper").isEmpty();
        assertThatThrownBy(() -> wrapped.writeTo(new ByteArrayOutputStream()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void abandonLeavesTheArrayToTheGc() throws IOException {
        var s = stream();
        s.abandon();
        assertThat(s.escaped()).isTrue();
        var out = new ByteArrayOutputStream();
        s.writeTo(out);
        assertThat(out.toString(StandardCharsets.US_ASCII)).isEqualTo(CONTENT);
        s.release();
        assertThat(recycled).isEmpty();

        var u = stream();
        u.asByteBuffer();
        u.abandon();
        u.release();
        assertThat(recycled).isEmpty();

        var t = stream();
        t.release();
        t.abandon();
        assertThat(recycled).containsExactly(array);
    }

    @Test
    void abandonMidWritePreventsRecycling() throws IOException {
        var s = stream();
        s.writeTo(new OutputStream() {
            @Override
            public void write(int b) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void write(byte[] b, int off, int len) {
                s.abandon();
                s.release();
            }
        });
        assertThat(s.escaped()).isTrue();
        assertThat(s.isReleased()).isTrue();
        assertThat(recycled).isEmpty();
    }

    private static final class NoopSubscriber implements Flow.Subscriber<ByteBuffer> {
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {}

        @Override
        public void onError(Throwable throwable) {}

        @Override
        public void onComplete() {}
    }
}
