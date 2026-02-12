/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StreamRegistryTest {

    private StreamRegistry registry;
    private H2Muxer muxer;

    @BeforeEach
    void setUp() {
        registry = new StreamRegistry();
        // Create a muxer with minimal dependencies
        H2Muxer.ConnectionCallback callback = new H2Muxer.ConnectionCallback() {
            @Override
            public boolean isAcceptingStreams() {
                return true;
            }

            @Override
            public int getRemoteMaxHeaderListSize() {
                return Integer.MAX_VALUE;
            }
        };
        H2FrameCodec codec = new H2FrameCodec(null, null, 16384);
        muxer = new H2Muxer(callback, codec, 4096, "test-muxer", 65535);
    }

    @AfterEach
    void tearDown() {
        if (muxer != null) {
            muxer.shutdownNow();
        }
    }

    private H2Exchange exchange(int streamId) {
        H2Exchange ex = new H2Exchange(muxer, null, 0, 0, 65535);
        ex.setStreamId(streamId);
        return ex;
    }

    @Test
    void putAndGet() {
        var ex = exchange(1);
        registry.put(1, ex);

        assertSame(ex, registry.get(1));
    }

    @Test
    void getReturnsNullForMissing() {
        assertNull(registry.get(1));
    }

    @Test
    void removeFromFastPath() {
        var ex = exchange(1);
        registry.put(1, ex);

        assertTrue(registry.remove(1));
        assertNull(registry.get(1));
    }

    @Test
    void removeReturnsFalseForMissing() {
        assertFalse(registry.remove(1));
    }

    @Test
    void multipleStreams() {
        var ex1 = exchange(1);
        var ex3 = exchange(3);
        var ex5 = exchange(5);

        registry.put(1, ex1);
        registry.put(3, ex3);
        registry.put(5, ex5);

        assertSame(ex1, registry.get(1));
        assertSame(ex3, registry.get(3));
        assertSame(ex5, registry.get(5));
    }

    @Test
    void spilloverOnCollision() {
        // Stream IDs that map to the same slot (4096 slots, so IDs 1 and 1 + 4096*2 = 8193 collide)
        int id1 = 1;
        int id2 = 1 + 4096 * 2; // 8193

        var ex1 = exchange(id1);
        var ex2 = exchange(id2);

        registry.put(id1, ex1);
        registry.put(id2, ex2); // Should spill over

        assertSame(ex1, registry.get(id1));
        assertSame(ex2, registry.get(id2));
    }

    @Test
    void removeFromSpillover() {
        int id1 = 1;
        int id2 = 1 + 4096 * 2;

        var ex1 = exchange(id1);
        var ex2 = exchange(id2);

        registry.put(id1, ex1);
        registry.put(id2, ex2);

        assertTrue(registry.remove(id2)); // Remove from spillover
        assertNull(registry.get(id2));
        assertSame(ex1, registry.get(id1)); // Original still there
    }

    @Test
    void forEach() {
        var ex1 = exchange(1);
        var ex3 = exchange(3);

        registry.put(1, ex1);
        registry.put(3, ex3);

        List<Integer> seen = new ArrayList<>();
        registry.forEach(seen, (ex, list) -> list.add(ex.getStreamId()));

        assertEquals(2, seen.size());
        assertTrue(seen.contains(1));
        assertTrue(seen.contains(3));
    }

    @Test
    void forEachIncludesSpillover() {
        int id1 = 1;
        int id2 = 1 + 4096 * 2;

        registry.put(id1, exchange(id1));
        registry.put(id2, exchange(id2));

        AtomicInteger count = new AtomicInteger();
        registry.forEach(null, (ex, ctx) -> count.incrementAndGet());

        assertEquals(2, count.get());
    }

    @Test
    void forEachMatching() {
        registry.put(1, exchange(1));
        registry.put(3, exchange(3));
        registry.put(5, exchange(5));

        List<Integer> matched = new ArrayList<>();
        registry.forEachMatching(id -> id > 2, ex -> matched.add(ex.getStreamId()));

        assertEquals(2, matched.size());
        assertTrue(matched.contains(3));
        assertTrue(matched.contains(5));
    }

    @Test
    void clearAndClose() {
        registry.put(1, exchange(1));
        registry.put(3, exchange(3));

        int id2 = 1 + 4096 * 2; // Collides with slot for ID 1
        registry.put(id2, exchange(id2)); // spillover

        AtomicInteger closed = new AtomicInteger();
        registry.clearAndClose(ex -> closed.incrementAndGet());

        assertEquals(3, closed.get());
        assertNull(registry.get(1));
        assertNull(registry.get(3));
        assertNull(registry.get(id2));
    }
}
