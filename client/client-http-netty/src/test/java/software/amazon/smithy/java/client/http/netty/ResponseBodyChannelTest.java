/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ResponseBodyChannelTest {

    private static final int HIGH = 32;
    private static final int LOW = 8;

    private ResponseBodyChannel newChannel(AtomicReference<Boolean> autoRead) {
        var err = new AtomicReference<Throwable>();
        return new ResponseBodyChannel(err, b -> autoRead.set(b), null, HIGH, LOW);
    }

    private static ByteBuf buf(byte[] data) {
        return Unpooled.wrappedBuffer(data);
    }

    private static byte[] bytes(int n) {
        byte[] a = new byte[n];
        for (int i = 0; i < n; i++)
            a[i] = (byte) (i % 251);
        return a;
    }

    @Test
    void readsQueuedChunks() throws Exception {
        var ar = new AtomicReference<Boolean>();
        var ch = newChannel(ar);
        ch.publish(buf(new byte[] {1, 2, 3}));
        ch.publish(buf(new byte[] {4, 5}));
        ch.publishEos();
        byte[] out = ch.readAllBytes();
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, out);
    }

    @Test
    void readSmallerThanChunkLeavesResidual() throws Exception {
        var ch = newChannel(new AtomicReference<>());
        ch.publish(buf(new byte[] {1, 2, 3, 4, 5}));
        ch.publishEos();
        byte[] b = new byte[2];
        assertEquals(2, ch.read(b));
        assertArrayEquals(new byte[] {1, 2}, b);
        assertEquals(3, ch.read(new byte[5]));
    }

    @Test
    void eosOnlyReturnsMinusOne() throws Exception {
        var ch = newChannel(new AtomicReference<>());
        ch.publishEos();
        assertEquals(-1, ch.read());
    }

    @Test
    void errorIsPropagated() {
        var ch = newChannel(new AtomicReference<>());
        var cause = new RuntimeException("boom");
        ch.publishError(cause);
        var ex = assertThrows(IOException.class, ch::read);
        assertEquals(cause, ex.getCause());
    }

    @Test
    void closeReleasesPendingBuffers() {
        var ch = newChannel(new AtomicReference<>());
        ByteBuf b1 = Unpooled.buffer().writeBytes(new byte[] {1, 2});
        ByteBuf b2 = Unpooled.buffer().writeBytes(new byte[] {3, 4});
        ch.publish(b1);
        ch.publish(b2);
        ch.close();
        assertEquals(0, b1.refCnt());
        assertEquals(0, b2.refCnt());
    }

    @Test
    void publishAfterCloseReleases() {
        var ch = newChannel(new AtomicReference<>());
        ch.close();
        ByteBuf b = Unpooled.buffer().writeBytes(new byte[] {1});
        ch.publish(b);
        assertEquals(0, b.refCnt());
    }

    @Test
    void inlineHandoffWhenConsumerParked() throws Exception {
        var ch = newChannel(new AtomicReference<>());
        var started = new CountDownLatch(1);
        byte[] payload = bytes(1024);
        var buf = new byte[2048];
        var nRef = new java.util.concurrent.atomic.AtomicInteger();
        var consumer = Thread.ofVirtual().start(() -> {
            try {
                started.countDown();
                nRef.set(ch.read(buf));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        started.await();
        Thread.sleep(50); // let the VT park
        ch.publish(buf(payload));
        consumer.join(2000);
        assertEquals(payload.length, nRef.get());
        byte[] trimmed = new byte[nRef.get()];
        System.arraycopy(buf, 0, trimmed, 0, nRef.get());
        assertArrayEquals(payload, trimmed);
    }

    @Test
    void handoffWithResidualWhenChunkBiggerThanRead() throws Exception {
        var ch = newChannel(new AtomicReference<>());
        var started = new CountDownLatch(1);
        byte[] payload = bytes(200);
        var firstN = new AtomicInteger();
        var consumer = Thread.ofVirtual().start(() -> {
            try {
                byte[] buf = new byte[64];
                started.countDown();
                firstN.set(ch.read(buf));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        started.await();
        Thread.sleep(50);
        ch.publish(buf(payload));
        consumer.join(2000);
        assertEquals(64, firstN.get());
        // Remaining 136 bytes should be readable afterwards.
        ch.publishEos();
        byte[] rest = ch.readAllBytes();
        assertEquals(200 - 64, rest.length);
    }

    @Test
    void stressManyChunksSingleConsumer() throws Exception {
        var ch = newChannel(new AtomicReference<>());
        int chunks = 5000;
        var totalRead = new AtomicInteger();
        var expectedTotal = new AtomicInteger();
        var rng = new Random(42);

        var consumer = Thread.ofVirtual().start(() -> {
            byte[] buf = new byte[4096];
            try {
                while (true) {
                    int n = ch.read(buf);
                    if (n < 0)
                        break;
                    totalRead.addAndGet(n);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        var producer = new Thread(() -> {
            for (int i = 0; i < chunks; i++) {
                int size = 1 + rng.nextInt(1024);
                expectedTotal.addAndGet(size);
                ch.publish(buf(new byte[size]));
            }
            ch.publishEos();
        }, "producer");
        producer.start();

        producer.join(10_000);
        consumer.join(10_000);
        assertEquals(expectedTotal.get(), totalRead.get());
    }

    @Test
    void autoReadTogglesOnHighAndLowWatermarks() {
        var ar = new AtomicReference<Boolean>();
        var ch = newChannel(ar);
        // Fill up to high watermark — no consumer, so every publish goes to fallback.
        for (int i = 0; i < HIGH - 1; i++) {
            ch.publish(buf(new byte[] {(byte) i}));
        }
        assertEquals(null, ar.get(), "autoRead not toggled before high-water");
        ch.publish(buf(new byte[] {42}));
        assertEquals(Boolean.FALSE, ar.get(), "autoRead paused at high-water");

        // Drain until we cross low-water.
        try {
            byte[] buf = new byte[1];
            // Drain down to exactly LOW entries — need to read (HIGH - LOW) chunks.
            for (int i = 0; i < HIGH - LOW; i++) {
                ch.read(buf);
            }
            assertEquals(Boolean.TRUE, ar.get(), "autoRead resumed at low-water");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void concurrentProducerAndVtConsumerDeliversAllBytes() throws Exception {
        var ch = newChannel(new AtomicReference<>());
        int chunks = 2000;
        byte[] payload = bytes(1000);

        var producer = new Thread(() -> {
            for (int i = 0; i < chunks; i++) {
                ch.publish(buf(payload.clone()));
            }
            ch.publishEos();
        });
        producer.setDaemon(true);

        var totalRead = new AtomicInteger();
        var consumerVt = Thread.ofVirtual().start(() -> {
            byte[] buf = new byte[4096];
            try {
                while (true) {
                    int n = ch.read(buf);
                    if (n < 0)
                        break;
                    totalRead.addAndGet(n);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        producer.start();
        producer.join(10_000);
        consumerVt.join(10_000);
        assertEquals(chunks * payload.length, totalRead.get());
    }

    @Test
    void noByteBufLeaksAfterFullConsumption() throws Exception {
        var ch = newChannel(new AtomicReference<>());
        var bufs = new java.util.ArrayList<ByteBuf>();
        for (int i = 0; i < 50; i++) {
            ByteBuf b = Unpooled.buffer().writeBytes(bytes(64));
            bufs.add(b);
            ch.publish(b);
        }
        ch.publishEos();
        ch.readAllBytes();
        for (ByteBuf b : bufs) {
            assertEquals(0, b.refCnt(), "ByteBuf not released");
        }
    }

    /**
     * Regression: the consumer arms WAITING, produces EOS via wakeWaiter (which CAS'd WAITING→IDLE),
     * then the consumer re-polled and found the EOS marker. The consumer's CAS(WAITING→IDLE) fails
     * because state is already IDLE. It must NOT assume a handoff happened. Previously this caused
     * the consumer to spin in awaitPendingRead() forever.
     */
    @Test
    void eosRaceDuringArmDoesNotFakeHandoff() throws Exception {
        // Deterministically trigger: use a single-shot channel with no data, just EOS.
        // Consumer threads arm, immediately after EOS publish — the re-poll returns the EOS marker,
        // CAS fails because wakeWaiter CAS'd first. Expected: return -1, not hang.
        for (int trial = 0; trial < 100; trial++) {
            var ch = newChannel(new AtomicReference<>());
            var started = new CountDownLatch(1);
            var result = new AtomicInteger(-2);
            var consumer = Thread.ofVirtual().start(() -> {
                try {
                    started.countDown();
                    byte[] buf = new byte[16];
                    result.set(ch.read(buf));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            started.await();
            Thread.sleep(1);
            ch.publishEos();
            consumer.join(2000);
            assertEquals(false, consumer.isAlive(), "consumer hung on trial " + trial);
            assertEquals(-1, result.get(), "wrong read result on trial " + trial);
        }
    }

    /**
     * Race the producer hitting high-water and the consumer draining below low-water repeatedly.
     * Verifies autoRead ends up resumed (true) at the end — i.e., we never "stick" on paused
     * due to a lost write to {@code autoReadPaused}.
     */
    @Test
    void autoReadNeverStucksPausedUnderRace() throws Exception {
        var lastToggle = new AtomicReference<Boolean>();
        var err = new AtomicReference<Throwable>();
        var ch = new ResponseBodyChannel(err, lastToggle::set, null, HIGH, LOW);
        int totalChunks = 10_000;
        var consumerDone = new CountDownLatch(1);

        var consumer = Thread.ofVirtual().start(() -> {
            byte[] buf = new byte[64];
            try {
                while (true) {
                    int n = ch.read(buf);
                    if (n < 0)
                        break;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                consumerDone.countDown();
            }
        });

        // Producer: burst publishes fast enough to outpace consumer at times (triggers pause),
        // then slower to let consumer catch up (triggers resume).
        var producer = new Thread(() -> {
            var rng = new Random(7);
            for (int i = 0; i < totalChunks; i++) {
                ch.publish(Unpooled.wrappedBuffer(new byte[1 + rng.nextInt(64)]));
                if (i % 100 == 0) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
            ch.publishEos();
        });
        producer.start();
        producer.join(15_000);
        assertTrue(consumerDone.await(15, TimeUnit.SECONDS), "consumer did not finish; autoRead likely stuck");
        // Final toggle (if any occurred) should be TRUE — or null if never crossed watermarks.
        Boolean last = lastToggle.get();
        if (last != null) {
            assertEquals(Boolean.TRUE, last, "autoRead stuck in paused state after drain");
        }
    }

    /**
     * Simulate many concurrent streams (as would happen with c=100 on an H2 connection): one
     * ResponseBodyChannel per stream, a shared "event loop" thread producing across all channels,
     * and a mix of fast and slow VT consumers. Verifies no deadlocks and no byte loss.
     */
    @Test
    void manyConcurrentChannelsWithMixedConsumerSpeeds() throws Exception {
        int streams = 100;
        int chunksPerStream = 200;
        int chunkSize = 2048;

        var channels = new ResponseBodyChannel[streams];
        var expected = new int[streams];
        var actual = new AtomicInteger[streams];
        var consumers = new Thread[streams];

        // Use real ByteBufs so ref-counting is exercised; track them for leak check.
        var allBufs = java.util.Collections.synchronizedList(new java.util.ArrayList<ByteBuf>());

        for (int i = 0; i < streams; i++) {
            channels[i] = new ResponseBodyChannel(new AtomicReference<>(), x -> {}, null, HIGH, LOW);
            actual[i] = new AtomicInteger();
            final int idx = i;
            final ResponseBodyChannel c = channels[i];
            final AtomicInteger cnt = actual[i];
            // Mix fast (no sleep) and slow (sleep per read) consumers.
            final boolean slow = (i % 4 == 0);
            consumers[i] = Thread.ofVirtual().unstarted(() -> {
                byte[] buf = new byte[4096];
                try {
                    while (true) {
                        int n = c.read(buf);
                        if (n < 0)
                            break;
                        cnt.addAndGet(n);
                        if (slow)
                            Thread.sleep(0, 100_000);
                    }
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            consumers[i].start();
        }

        // Single producer thread round-robins publishes across all channels, simulating an
        // event loop serving multiplexed streams.
        var producer = new Thread(() -> {
            var rng = new Random(123);
            for (int c = 0; c < chunksPerStream; c++) {
                for (int i = 0; i < streams; i++) {
                    int size = 1 + rng.nextInt(chunkSize);
                    expected[i] += size;
                    ByteBuf b = Unpooled.buffer(size).writeBytes(new byte[size]);
                    allBufs.add(b);
                    channels[i].publish(b);
                }
            }
            for (int i = 0; i < streams; i++)
                channels[i].publishEos();
        }, "producer");
        producer.start();

        producer.join(30_000);
        assertEquals(false, producer.isAlive(), "producer did not finish");
        for (int i = 0; i < streams; i++) {
            consumers[i].join(30_000);
            assertEquals(false, consumers[i].isAlive(), "consumer " + i + " did not finish");
            assertEquals(expected[i], actual[i].get(), "stream " + i + " byte count mismatch");
        }
        for (ByteBuf b : allBufs) {
            assertEquals(0, b.refCnt(), "ByteBuf not released");
        }
    }
}
