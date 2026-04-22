/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ByteAllocatorTest {

    @Test
    void borrowReturnsBufferOfRequestedSize() {
        ByteAllocator pool = new ByteAllocator(10, 1024, 1024, 128);

        ByteBuffer buffer = pool.borrow(256);

        assertNotNull(buffer);
        assertTrue(buffer.capacity() >= 256);
    }

    @Test
    void borrowReturnsDefaultSizeWhenRequestedSizeIsSmaller() {
        ByteAllocator pool = new ByteAllocator(10, 1024, 1024, 128);

        ByteBuffer buffer = pool.borrow(64);

        assertNotNull(buffer);
        assertEquals(128, buffer.capacity());
    }

    @Test
    void releasedBufferCanBeReused() {
        ByteAllocator pool = new ByteAllocator(10, 1024, 1024, 128);

        ByteBuffer buffer1 = pool.borrow(128);
        pool.release(buffer1);
        ByteBuffer buffer2 = pool.borrow(128);

        assertSame(buffer1, buffer2, "Should reuse the same buffer");
    }

    @Test
    void poolSizeIncreasesOnRelease() {
        ByteAllocator pool = new ByteAllocator(10, 1024, 1024, 128);
        assertEquals(0, pool.size());

        ByteBuffer buffer = pool.borrow(128);
        pool.release(buffer);

        assertEquals(1, pool.size());
    }

    @Test
    void poolSizeDecreasesOnBorrow() {
        ByteAllocator pool = new ByteAllocator(10, 1024, 1024, 128);

        ByteBuffer buffer1 = pool.borrow(128);
        pool.release(buffer1);
        assertEquals(1, pool.size());

        pool.borrow(128);
        assertEquals(0, pool.size());
    }

    @Test
    void poolRespectsMaxSize() {
        ByteAllocator pool = new ByteAllocator(2, 1024, 1024, 128);

        pool.release(ByteBuffer.allocate(128));
        pool.release(ByteBuffer.allocate(128));
        assertEquals(2, pool.size());

        pool.release(ByteBuffer.allocate(128));
        assertEquals(2, pool.size());
    }

    @Test
    void buffersLargerThanMaxPoolableSizeAreNotPooled() {
        ByteAllocator pool = new ByteAllocator(10, 1024, 256, 128);

        ByteBuffer largeBuffer = ByteBuffer.allocate(512);
        pool.release(largeBuffer);

        assertEquals(0, pool.size());
    }

    @Test
    void borrowThrowsWhenRequestedSizeExceedsMaxBufferSize() {
        ByteAllocator pool = new ByteAllocator(10, 256, 256, 128);

        assertThrows(IllegalArgumentException.class, () -> pool.borrow(512));
    }

    @Test
    void nullBufferIsIgnored() {
        ByteAllocator pool = new ByteAllocator(10, 1024, 1024, 128);

        pool.release(null);

        assertEquals(0, pool.size());
    }

    @Test
    void clearRemovesAllBuffers() {
        ByteAllocator pool = new ByteAllocator(10, 1024, 1024, 128);

        pool.release(ByteBuffer.allocate(128));
        pool.release(ByteBuffer.allocate(128));
        pool.release(ByteBuffer.allocate(128));
        assertEquals(3, pool.size());

        pool.clear();

        assertEquals(0, pool.size());
    }

    @Test
    void tooSmallPooledBufferIsDropped() {
        ByteAllocator pool = new ByteAllocator(10, 1024, 1024, 128);

        ByteBuffer smallBuffer = ByteBuffer.allocate(64);
        pool.release(smallBuffer);
        assertEquals(1, pool.size());

        ByteBuffer buffer = pool.borrow(256);
        assertEquals(0, pool.size());
        assertTrue(buffer.capacity() >= 256);
        assertNotSame(smallBuffer, buffer);
    }

    @Test
    void constructorValidatesMaxPoolCount() {
        assertThrows(IllegalArgumentException.class, () -> new ByteAllocator(0, 1024, 1024, 128));
        assertThrows(IllegalArgumentException.class, () -> new ByteAllocator(-1, 1024, 1024, 128));
    }

    @Test
    void constructorValidatesDefaultBufferSize() {
        assertThrows(IllegalArgumentException.class, () -> new ByteAllocator(10, 1024, 1024, 0));
        assertThrows(IllegalArgumentException.class, () -> new ByteAllocator(10, 1024, 1024, -1));
    }

    @Test
    void constructorValidatesMaxPoolableSize() {
        assertThrows(IllegalArgumentException.class, () -> new ByteAllocator(10, 1024, 0, 128));
        assertThrows(IllegalArgumentException.class, () -> new ByteAllocator(10, 256, 512, 128));
    }

    @Test
    void borrowThrowsWhenMinSizeIsZeroOrNegative() {
        ByteAllocator pool = new ByteAllocator(10, 1024, 1024, 128);

        assertThrows(IllegalArgumentException.class, () -> pool.borrow(0));
        assertThrows(IllegalArgumentException.class, () -> pool.borrow(-1));
    }

    @Test
    void lifoOrderPreserved() {
        ByteAllocator pool = new ByteAllocator(10, 1024, 1024, 128);

        ByteBuffer buffer1 = ByteBuffer.allocate(128);
        ByteBuffer buffer2 = ByteBuffer.allocate(128);
        ByteBuffer buffer3 = ByteBuffer.allocate(128);

        pool.release(buffer1);
        pool.release(buffer2);
        pool.release(buffer3);

        assertSame(buffer3, pool.borrow(128));
        assertSame(buffer2, pool.borrow(128));
        assertSame(buffer1, pool.borrow(128));
    }

    @Test
    void concurrentBorrowAndReleaseIsThreadSafe() throws InterruptedException {
        ByteAllocator pool = new ByteAllocator(100, 1024, 1024, 128);
        int threadCount = 10;
        int operationsPerThread = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Throwable> errors = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        ByteBuffer buffer = pool.borrow(128);
                        assertNotNull(buffer);
                        buffer.put(0, (byte) i);
                        pool.release(buffer);
                    }
                } catch (Throwable e) {
                    synchronized (errors) {
                        errors.add(e);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertTrue(errors.isEmpty(), "Concurrent operations should not throw: " + errors);
        assertTrue(pool.size() <= 100);
    }
}
