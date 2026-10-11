/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.io.datastream.DataStream;

class OutputBuffersTest {

    private static final int MIN = 4 * 1024;
    private static final int MAX = 1024 * 1024;

    @Test
    void eachSizeClassIsBoundedByBytesAndByCores() {
        var pool = OutputBuffers.create();
        int perCore = StripedPool.logicalSlots(4, Runtime.getRuntime().availableProcessors());
        assertThat(pool.slotsFor(MAX)).isEqualTo(Math.min(perCore, 4));
        assertThat(pool.slotsFor(MAX / 2)).isEqualTo(Math.min(perCore, 8));
        assertThat(pool.slotsFor(MAX / 4)).isEqualTo(Math.min(perCore, 16));
        assertThat(pool.slotsFor(MIN)).isEqualTo(Math.min(perCore, 1024));
        long worstCase = 0;
        for (int size = MIN; size <= MAX; size <<= 1) {
            worstCase += (long) pool.slotsFor(size) * size;
        }
        assertThat(worstCase).isLessThanOrEqualTo(9L * (4 << 20));
    }

    @Test
    void acquireRoundsUpToAPooledSizeClass() {
        var pool = OutputBuffers.create();
        assertThat(pool.acquire(0).length).isEqualTo(MIN);
        assertThat(pool.acquire(1).length).isEqualTo(MIN);
        assertThat(pool.acquire(MIN).length).isEqualTo(MIN);
        assertThat(pool.acquire(MIN + 1).length).isEqualTo(MIN * 2);
        assertThat(pool.acquire(100_000).length).isEqualTo(131_072);
        assertThat(pool.acquire(MAX).length).isEqualTo(MAX);
    }

    @Test
    void arraysAboveTheLargestClassAreExactlySizedAndNotPooled() {
        var pool = OutputBuffers.create();
        byte[] big = pool.acquire(MAX + 1);
        assertThat(big.length).isEqualTo(MAX + 1);
        pool.release(big);
        assertThat(acquireSeveral(pool, MAX + 1)).noneMatch(a -> a == big);
    }

    @Test
    void releasedArraysComeBackFromAcquire() {
        var pool = OutputBuffers.create();
        byte[] a = pool.acquire(8192);
        pool.release(a);
        assertThat(acquireSeveral(pool, 8192)).anyMatch(b -> b == a);
    }

    @Test
    void arraysThatAreNotASizeClassAreDropped() {
        var pool = OutputBuffers.create();
        pool.release(new byte[100]);
        pool.release(new byte[5000]);
        pool.release(new byte[MIN - 1]);
        pool.release(new byte[MAX * 2]);
        assertThat(acquireSeveral(pool, 5000)).allMatch(a -> a.length == 8192);
    }

    @Test
    void growKeepsTheUsedPrefixAndRecyclesTheOldArray() {
        var pool = OutputBuffers.create();
        byte[] a = pool.acquire(MIN);
        for (int i = 0; i < 100; i++) {
            a[i] = (byte) i;
        }
        byte[] grown = pool.grow(a, 100, MIN + 1);
        assertThat(grown).isNotSameAs(a);
        assertThat(grown.length).isGreaterThanOrEqualTo(MIN * 2);
        assertThat(Arrays.copyOf(grown, 100)).isEqualTo(Arrays.copyOf(a, 100));
        assertThat(acquireSeveral(pool, MIN)).anyMatch(b -> b == a);
    }

    @Test
    void growHonorsAMinimumLargerThanDoubling() {
        var pool = OutputBuffers.create();
        byte[] grown = pool.grow(pool.acquire(MIN), 0, 100_000);
        assertThat(grown.length).isGreaterThanOrEqualTo(100_000);
    }

    @Test
    void sizeHintFollowsLargeBodiesAtOnceAndDecaysSlowly() {
        int hint = 8192;
        hint = OutputBuffers.nextSizeHint(hint, 700_000);
        assertThat(hint).isEqualTo(700_000);
        hint = OutputBuffers.nextSizeHint(hint, 200);
        assertThat(hint).isEqualTo(525_000);
        assertThat(OutputBuffers.create().acquire(hint).length).isEqualTo(MAX);
        for (int i = 0; i < 12; i++) {
            hint = OutputBuffers.nextSizeHint(hint, 200);
        }
        assertThat(hint).isLessThan(64 * 1024);
        assertThat(OutputBuffers.nextSizeHint(hint, 5 * MAX)).isEqualTo(MAX);
    }

    @Test
    void recyclerReturnsAReleasedBodyToThePoolThatLentIt() throws Exception {
        var lender = OutputBuffers.create();
        var other = OutputBuffers.create();
        byte[] a = lender.acquire(MIN);
        a[0] = 42;
        var body = DataStream.ofLeasedBytes(a, 0, 1, "application/octet-stream", lender.recycler());
        var out = new ByteArrayOutputStream();
        body.writeTo(out);
        assertThat(out.toByteArray()).containsExactly(42);
        body.release();
        assertThat(acquireSeveral(lender, MIN)).anyMatch(b -> b == a);
        assertThat(acquireSeveral(other, MIN)).noneMatch(b -> b == a);
    }

    @Test
    void clearDropsWhatThePoolHoldsAndKeepsWorking() {
        var pool = OutputBuffers.create();
        byte[] a = pool.acquire(MIN);
        pool.release(a);

        pool.clear();

        assertThat(acquireSeveral(pool, MIN)).noneMatch(b -> b == a);
        byte[] later = pool.acquire(MIN);
        pool.release(later);
        assertThat(acquireSeveral(pool, MIN)).anyMatch(b -> b == later);
    }

    @Test
    void poolsAreIndependent() {
        var one = OutputBuffers.create();
        var two = OutputBuffers.create();
        byte[] a = one.acquire(8192);
        one.release(a);
        assertThat(acquireSeveral(two, 8192)).noneMatch(b -> b == a);
        assertThat(acquireSeveral(one, 8192)).anyMatch(b -> b == a);
        assertThat(OutputBuffers.shared()).isSameAs(OutputBuffers.shared()).isNotSameAs(one);
    }

    // Thread-based slot selection can require several acquisitions to find a specific array.
    private static List<byte[]> acquireSeveral(OutputBuffers pool, int size) {
        List<byte[]> result = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            result.add(pool.acquire(size));
        }
        return result;
    }
}
