/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ActiveConnectionLimitTest {

    @Test
    void fixedClampsToMaxTotalConnections() {
        var state = ActiveConnectionLimit.fixed(32).newState(16);

        assertEquals(16, state.limit());
    }

    @Test
    void fixedValidatesSettings() {
        assertThrows(IllegalArgumentException.class, () -> ActiveConnectionLimit.fixed(0));
    }

    @Test
    void banditPeriodicallyProbesCandidates() {
        var state = ActiveConnectionLimit.adaptiveBandit()
                .candidates(4, 8, 16)
                .initial(4)
                .windowSize(4)
                .explorationInterval(2)
                .build()
                .newState(16);

        fillWindow(state, 4, Duration.ofMillis(5).toNanos());
        assertEquals(4, state.limit());

        fillWindow(state, 4, Duration.ofMillis(5).toNanos());
        assertEquals(8, state.limit());
    }

    @Test
    void banditReturnsToBestCandidateAfterBadProbe() {
        var state = ActiveConnectionLimit.adaptiveBandit()
                .candidates(4, 8)
                .initial(4)
                .windowSize(4)
                .explorationInterval(2)
                .tailRatioWeight(100)
                .ewmaAlpha(1)
                .build()
                .newState(8);

        fillWindow(state, 4, Duration.ofMillis(5).toNanos());
        fillWindow(state, 4, Duration.ofMillis(5).toNanos());
        assertEquals(8, state.limit());

        state.onSample(Duration.ofMillis(5).toNanos(), 8);
        state.onSample(Duration.ofMillis(5).toNanos(), 8);
        state.onSample(Duration.ofMillis(5).toNanos(), 8);
        state.onSample(Duration.ofMillis(100).toNanos(), 8);

        assertEquals(4, state.limit());
    }

    @Test
    void banditClampsCandidatesToMaxTotalConnections() {
        var state = ActiveConnectionLimit.adaptiveBandit()
                .candidates(32, 64)
                .initial(32)
                .build()
                .newState(16);

        assertEquals(16, state.limit());
    }

    @Test
    void banditValidatesSettings() {
        assertThrows(IllegalArgumentException.class, () -> ActiveConnectionLimit.adaptiveBandit()
                .candidates()
                .build());
        assertThrows(IllegalArgumentException.class, () -> ActiveConnectionLimit.adaptiveBandit()
                .candidates(8, 16)
                .initial(12)
                .build());
        assertThrows(IllegalArgumentException.class, () -> ActiveConnectionLimit.adaptiveBandit()
                .explorationInterval(1)
                .build());
        assertThrows(IllegalArgumentException.class, () -> ActiveConnectionLimit.adaptiveBandit()
                .ewmaAlpha(0)
                .build());
    }

    @Test
    void latencyProbesUpAfterFirstUtilizedWindow() {
        var state = ActiveConnectionLimit.adaptiveLatency()
                .min(1)
                .initial(8)
                .max(16)
                .windowSize(4)
                .initialStep(2)
                .build()
                .newState(16);

        fillWindow(state, 8, Duration.ofMillis(5).toNanos());

        assertEquals(10, state.limit());
    }

    @Test
    void latencyReversesWhenTailRatioDriftsFromBaseline() {
        var state = ActiveConnectionLimit.adaptiveLatency()
                .min(1)
                .initial(8)
                .max(16)
                .windowSize(4)
                .initialStep(2)
                .tailRatioWeight(100)
                .throughputNoiseFloor(0)
                .build()
                .newState(16);

        fillWindow(state, 8, Duration.ofMillis(5).toNanos());
        assertEquals(10, state.limit());

        state.onSample(Duration.ofMillis(5).toNanos(), 10);
        state.onSample(Duration.ofMillis(5).toNanos(), 10);
        state.onSample(Duration.ofMillis(5).toNanos(), 10);
        state.onSample(Duration.ofMillis(100).toNanos(), 10);

        assertEquals(9, state.limit());
    }

    @Test
    void latencyDoesNotMoveWhenUnderutilized() {
        var state = ActiveConnectionLimit.adaptiveLatency()
                .min(1)
                .initial(8)
                .max(16)
                .windowSize(4)
                .initialStep(2)
                .build()
                .newState(16);

        fillWindow(state, 3, Duration.ofMillis(5).toNanos());

        assertEquals(8, state.limit());
    }

    @Test
    void latencyValidatesSettings() {
        assertThrows(IllegalArgumentException.class, () -> ActiveConnectionLimit.adaptiveLatency()
                .min(0)
                .build());
        assertThrows(IllegalArgumentException.class, () -> ActiveConnectionLimit.adaptiveLatency()
                .initialStep(0)
                .build());
        assertThrows(IllegalArgumentException.class, () -> ActiveConnectionLimit.adaptiveLatency()
                .tailRatioWeight(-1)
                .build());
    }

    private static void fillWindow(ActiveConnectionLimit.State state, int peakInflight, long nanos) {
        for (int i = 0; i < 4; i++) {
            state.onSample(nanos, peakInflight);
        }
    }
}
