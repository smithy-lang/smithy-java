package software.amazon.smithy.java.client.http.netty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.http.netty.mocks.MockChannel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NettyDataStreamTest {

    @Test
    public void helloWorldInEnglishProducedContentEqualsConsumed() throws Exception {
        // -- Arrange
        var dataStream = new NettyDataStream("text/plain", -1, MockChannel.builder().build());
        var expected = "Hello World!";
        var data = List.of(ByteBuffer.wrap(expected.getBytes()));
        var producerSubscriber = dataStream.producerSubscriber();
        var producerWorker = new ProducerWorker(producerSubscriber, data);
        producerSubscriber.onSubscribe(new ProducerSubscription(producerWorker));
        var consumerSubscriber = new ConsumerSubscriber();
        dataStream.subscribe(consumerSubscriber);

        // -- Act
        var producerFuture = CompletableFuture.runAsync(producerWorker);
        producerFuture.get(2, TimeUnit.SECONDS);

        // -- Assert
        var expectedBytes = expectedBytes(data);
        var actualBytes = consumerSubscriber.toByteArray();
        assertArrayEquals(expectedBytes, actualBytes);
        assertTrue(consumerSubscriber.isCompleted());
        assertFalse(consumerSubscriber.isErrored());
        assertEquals(expected, new String(actualBytes, StandardCharsets.UTF_8));
    }

    @Test
    public void helloWorldInChineseProducedContentEqualsConsumed() throws Exception {
        // -- Arrange
        var dataStream = new NettyDataStream("text/plain", -1, MockChannel.builder().build());
        var bytes = "你好世界".getBytes(StandardCharsets.UTF_8);
        var data = new ArrayList<ByteBuffer>();
        // Each char takes 3 bytes, split the chunks in a non code-point boundary.
        for (var idx = 0; idx < bytes.length; idx += 2) {
            var buf = ByteBuffer.allocate(2).put(bytes[idx]).put(bytes[idx + 1]).flip();
            data.add(buf);
        }
        var producerSubscriber = dataStream.producerSubscriber();
        var producerWorker = new ProducerWorker(producerSubscriber, data);
        producerSubscriber.onSubscribe(new ProducerSubscription(producerWorker));
        var consumerSubscriber = new ConsumerSubscriber();
        dataStream.subscribe(consumerSubscriber);

        // -- Act
        var producerFuture = CompletableFuture.runAsync(producerWorker);
        producerFuture.get(2, TimeUnit.SECONDS);

        // -- Assert
        var expectedBytes = expectedBytes(data);
        var actualBytes = consumerSubscriber.toByteArray();
        assertArrayEquals(expectedBytes, actualBytes);
        assertTrue(consumerSubscriber.isCompleted());
        assertFalse(consumerSubscriber.isErrored());
        assertEquals("你好世界", new String(actualBytes, StandardCharsets.UTF_8));
    }

    @Test
    public void randomizedProducedContentEqualsConsumed() throws Exception {
        // -- Arrange
        var dataStream = new NettyDataStream("text/plain", -1, MockChannel.builder().build());
        var data = createData(53, 97, 101);
        var producerSubscriber = dataStream.producerSubscriber();
        var producerWorker = new ProducerWorker(producerSubscriber, data);
        producerSubscriber.onSubscribe(new ProducerSubscription(producerWorker));
        var consumerSubscriber = new ConsumerSubscriber();
        dataStream.subscribe(consumerSubscriber);

        // -- Act
        var producerFuture = CompletableFuture.runAsync(producerWorker);
        producerFuture.get(2, TimeUnit.SECONDS);

        // -- Assert
        var expectedBytes = expectedBytes(data);
        var actualBytes = consumerSubscriber.toByteArray();
        assertArrayEquals(expectedBytes, actualBytes);
        assertTrue(consumerSubscriber.isCompleted());
        assertFalse(consumerSubscriber.isErrored());
    }

    @Test
    public void cancellingConsumerCancelsProducer() throws Exception {
        // -- Arrange
        var dataStream = new NettyDataStream("text/plain", -1, MockChannel.builder().build());
        var data = createData(53, 97, 101);
        var producerSubscriber = dataStream.producerSubscriber();
        var producerWorker = new ProducerWorker(producerSubscriber, data);
        producerSubscriber.onSubscribe(new ProducerSubscription(producerWorker));
        var consumerSubscriber = new ConsumerSubscriber();
        dataStream.subscribe(consumerSubscriber);

        // -- Act
        var producerFuture = CompletableFuture.runAsync(producerWorker);
        consumerSubscriber.cancel();
        producerFuture.get(2, TimeUnit.SECONDS);

        // -- Assert
        assertTrue(producerWorker.isCancelled());
    }

    @Test
    public void producerOnErrorPropagatesToConsumer() throws Exception {
        // -- Arrange
        var dataStream = new NettyDataStream("text/plain", -1, MockChannel.builder().build());
        var data = createData(53, 97, 101);
        var producerSubscriber = dataStream.producerSubscriber();
        var producerWorker = new ProducerWorker(producerSubscriber, data, new IOException("boom"));
        producerSubscriber.onSubscribe(new ProducerSubscription(producerWorker));
        var consumerSubscriber = new ConsumerSubscriber();
        dataStream.subscribe(consumerSubscriber);

        // -- Act
        var producerFuture = CompletableFuture.runAsync(producerWorker);
        consumerSubscriber.cancel();
        producerFuture.get(2, TimeUnit.SECONDS);

        // -- Assert
        assertTrue(consumerSubscriber.isErrored());
    }

    private byte[] expectedBytes(List<ByteBuffer> buffers) throws IOException {
        var out = new ByteArrayOutputStream();
        for (var buffer : buffers) {
            buffer.flip();
            var bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            out.write(bytes);
        }
        return out.toByteArray();
    }

    /**
     * Run async to simulate how Netty works where the request to read more bytes happens
     * in a different thread. Otherwise, we hit the classic reactive streams problem where
     * synchronous `request()` → `onNext()` → `request()` creates infinite recursion
     */
    static class ProducerWorker implements Runnable {
        private final Flow.Subscriber<? super ByteBuffer> subscriber;
        private final List<ByteBuffer> chunks;
        private int delivered;
        private volatile boolean done = false;
        private boolean cancelled = false;
        private final AtomicLong requested = new AtomicLong();
        private final Throwable error;

        ProducerWorker(Flow.Subscriber<? super ByteBuffer> subscriber, List<ByteBuffer> chunks, Throwable error) {
            this.subscriber = subscriber;
            this.chunks = chunks;
            this.error = error;
        }

        ProducerWorker(Flow.Subscriber<? super ByteBuffer> subscriber, List<ByteBuffer> chunks) {
            this(subscriber, chunks, null);
        }

        public void request(long count) {
            // No need to check for overflows
            requested.addAndGet(count);
        }

        public void cancel() {
            cancelled = true;
            done = true;
        }

        @Override
        public void run() {
            while (!done) {
                while (requested.get() > 0) {
                    if (delivered < chunks.size()) {
                        subscriber.onNext(chunks.get(delivered++));
                    } else {
                        done = true;
                        break;
                    }
                    requested.decrementAndGet();
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            if (error != null) {
                subscriber.onError(error);
            } else {
                subscriber.onComplete();
            }
        }

        boolean isCancelled() {
            return cancelled;
        }
    }


    static class ProducerSubscription implements Flow.Subscription {
        private final ProducerWorker producerWorker;

        ProducerSubscription(ProducerWorker producerWorker) {
            this.producerWorker = producerWorker;
        }

        @Override
        public void request(long n) {
            // request to the worker running in a different thread.
            producerWorker.request(n);
        }

        @Override
        public void cancel() {
            producerWorker.cancel();
        }
    }

    static class ConsumerSubscriber implements Flow.Subscriber<ByteBuffer> {
        private Flow.Subscription subscription;
        private boolean completed;
        private Throwable error;
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(ByteBuffer item) {
            byte[] temp = new byte[item.remaining()];
            item.get(temp);
            try {
                out.write(temp);
            } catch (IOException e) {
                subscription.cancel();
                return;
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
        }

        @Override
        public void onComplete() {
            completed = true;
        }

        public void cancel() {
            subscription.cancel();
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }

        boolean isCompleted() {
            return completed;
        }

        Throwable getError() {
            return error;
        }

        boolean isErrored() {
            return error != null;
        }
    }


    private static List<ByteBuffer> createData(
            int chunkMinSize,
            int chunkMaxSize,
            int chunkCount
    ) {
        var random = ThreadLocalRandom.current();
        var result = new ArrayList<ByteBuffer>();
        for (var c = 0; c < chunkCount; c++) {
            int size = random.nextInt(chunkMinSize, chunkMaxSize);
            var buf = new byte[size];
            for (var i = 0; i < size; i++) {
                buf[i] = (byte) random.nextInt(-128, 128);
                result.add(ByteBuffer.wrap(buf));
            }
        }
        return result;
    }

    private static void sleepingTicker() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
