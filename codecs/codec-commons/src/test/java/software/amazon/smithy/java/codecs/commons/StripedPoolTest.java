/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StripedPoolTest {

    @Test
    void slotCountIsAPowerOfTwoAndNeverZero() {
        assertThat(StripedPool.logicalSlots(1, 1)).isEqualTo(1);
        assertThat(StripedPool.logicalSlots(2, 1)).isEqualTo(2);
        assertThat(StripedPool.logicalSlots(4, 1)).isEqualTo(4);
        assertThat(StripedPool.logicalSlots(1, 2)).isEqualTo(2);
        assertThat(StripedPool.logicalSlots(1, 3)).isEqualTo(4);
        assertThat(StripedPool.logicalSlots(4, 10)).isEqualTo(64);
    }

    @Test
    void slotCountRoundsUpToAPowerOfTwoAndIsNeverZero() {
        assertThat(slotPool(0).logicalSlotCount()).isEqualTo(1);
        assertThat(slotPool(1).logicalSlotCount()).isEqualTo(1);
        assertThat(slotPool(3).logicalSlotCount()).isEqualTo(4);
        assertThat(slotPool(8).logicalSlotCount()).isEqualTo(8);
        assertThat(slotPool(9).logicalSlotCount()).isEqualTo(16);
    }

    private static StripedPool<byte[], Void> slotPool(int slots) {
        return new StripedPool<>(slots) {
            @Override
            protected byte[] create(Void context) {
                return new byte[8];
            }

            @Override
            protected boolean canPool(byte[] item) {
                return true;
            }

            @Override
            protected boolean reset(byte[] item, Void context) {
                return true;
            }
        };
    }

    @Test
    void clearDropsPooledItemsButNotAcquiredOnes() {
        var pool = new StripedPool<byte[], Void>(8) {
            @Override
            protected byte[] create(Void context) {
                return new byte[8];
            }

            @Override
            protected boolean canPool(byte[] item) {
                return true;
            }

            @Override
            protected boolean reset(byte[] item, Void context) {
                return true;
            }
        };
        byte[] pooled = pool.acquire(null);
        byte[] inUse = pool.acquire(null);
        pool.release(pooled);

        pool.clear();

        assertThat(pool.acquire(null)).isNotSameAs(pooled);
        pool.release(inUse);
        assertThat(pool.acquire(null)).isSameAs(inUse);
    }

    @Test
    void singleSlotPoolRoundTrips() {
        var pool = new StripedPool<byte[], Void>(1) {
            @Override
            protected byte[] create(Void context) {
                return new byte[8];
            }

            @Override
            protected boolean canPool(byte[] item) {
                return true;
            }

            @Override
            protected boolean reset(byte[] item, Void context) {
                return true;
            }
        };
        byte[] first = pool.acquire(null);
        pool.release(first);
        assertThat(pool.acquire(null)).isSameAs(first);
        assertThat(pool.acquire(null)).isNotSameAs(first);
    }
}
