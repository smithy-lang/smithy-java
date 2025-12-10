/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.client.ByteAllocator;

class ByteAllocatorTest {

    @Test
    void borrowReturnsBufferOfRequestedSize() {
        ByteAllocator pool = new ByteAllocator(10, 1024, 1024, 128);

        byte[] buffer = pool.borrow(256);

        assertNotNull(buffer);
        assertTrue(buffer.length >= 256);
    }

    @Test
    void borrowReturnsDefaultSizeWhenRequestedSizeIsSmaller() {
        ByteAllocator pool = new ByteAllocator(10, 1024, 1024, 128);

        byte[] buffer = pool.borrow(64);

        assertNotNull(buffer);
        assertEquals(128, buffer.length); // Default size
    }

    @Test
    void releasedBufferCanBeReused() {
        ByteAllocator pool = new ByteAllocator(10, 1024, 1024, 128);

        byte[] buffer1 = pool.borrow(128);
        pool.release(buffer1);
        byte[] buffer2 = pool.borrow(128);

        assertSame(buffer1, buffer2, "Should reuse the same buffer");
    }

    @Test
    void poolSizeIncreasesOnRelease() {
        ByteAllocator pool = new ByteAllocator(10, 1024, 1024, 128);
        assertEquals(0, pool.size());

        byte[] buffer = pool.borrow(128);
        pool.release(buffer);

        assertEquals(1, pool.size());
    }

    @Test
    void poolSizeDecreasesOnBorrow() {
        ByteAllocator pool = new ByteAllocator(10, 1024, 1024, 128);

        byte[] buffer1 = pool.borrow(128);
        pool.release(buffer1);
        assertEquals(1, pool.size());

        pool.borrow(128);
        assertEquals(0, pool.size());
    }

    @Test
    void poolRespectsMaxSize() {
        ByteAllocator pool = new ByteAllocator(2, 1024, 1024, 128);

        // Fill pool to max
        pool.release(new byte[128]);
        pool.release(new byte[128]);
        assertEquals(2, pool.size());

        // Try to add one more - should be discarded
        pool.release(new byte[128]);
        assertEquals(2, pool.size());
    }

    @Test
    void buffersLargerThanMaxPoolableSizeAreNotPooled() {
        ByteAllocator pool = new ByteAllocator(10, 1024, 256, 128);

        byte[] largeBuffer = new byte[512]; // Larger than maxPoolableSize (256)
        pool.release(largeBuffer);

        assertEquals(0, pool.size(), "Buffer larger than maxPoolableSize should not be pooled");
    }

    @Test
    void borrowThrowsWhenRequestedSizeExceedsMaxBufferSize() {
        ByteAllocator pool = new ByteAllocator(10, 256, 256, 128);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> pool.borrow(512) // Larger than maxBufferSize (256)
        );

        assertTrue(ex.getMessage().contains("512"));
        assertTrue(ex.getMessage().contains("256"));
    }

    @Test
    void nullBufferIsIgnored() {
        ByteAllocator pool = new ByteAllocator(10, 1024, 1024, 128);

        pool.release(null); // Should not throw

        assertEquals(0, pool.size());
    }

    @Test
    void clearRemovesAllBuffers() {
        ByteAllocator pool = new ByteAllocator(10, 1024, 1024, 128);

        pool.release(new byte[128]);
        pool.release(new byte[128]);
        pool.release(new byte[128]);
        assertEquals(3, pool.size());

        pool.clear();

        assertEquals(0, pool.size());
    }

    @Test
    void tooSmallPooledBufferIsDropped() {
        ByteAllocator pool = new ByteAllocator(10, 1024, 1024, 128);

        // Release a small buffer
        byte[] smallBuffer = new byte[64];
        pool.release(smallBuffer);
        assertEquals(1, pool.size());

        // Borrow a larger buffer - small one is dropped (best-effort, no re-pooling)
        byte[] buffer = pool.borrow(256);
        assertEquals(0, pool.size()); // Small buffer was dropped
        assertTrue(buffer.length >= 256);
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
        // maxPoolableSize must be > 0
        assertThrows(IllegalArgumentException.class, () -> new ByteAllocator(10, 1024, 0, 128));
        // maxPoolableSize must be <= maxBufferSize
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

        byte[] buffer1 = new byte[128];
        byte[] buffer2 = new byte[128];
        byte[] buffer3 = new byte[128];

        pool.release(buffer1);
        pool.release(buffer2);
        pool.release(buffer3);

        // LIFO: should get buffer3, buffer2, buffer1 back
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
                        byte[] buffer = pool.borrow(128);
                        assertNotNull(buffer);
                        // Simulate some work
                        buffer[0] = (byte) i;
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
        assertTrue(pool.size() <= 100, "Pool size should not exceed max");
    }
}
