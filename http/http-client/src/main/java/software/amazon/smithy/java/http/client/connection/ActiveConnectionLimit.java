/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.util.Arrays;

/**
 * Controls how many HTTP/1.1 connections can be actively leased for a route.
 *
 * <p>This is separate from {@link HttpConnectionPoolBuilder#maxConnectionsPerRoute(int)}, which controls how many
 * idle connections may be retained for reuse.
 */
public sealed interface ActiveConnectionLimit
        permits ActiveConnectionLimit.Fixed,
                ActiveConnectionLimit.Bandit,
                ActiveConnectionLimit.Latency,
                ActiveConnectionLimit.Unlimited {

    /**
     * No route-specific active limit. Active HTTP/1.1 concurrency is limited by max total connections.
     *
     * @return an unlimited active connection policy.
     */
    static ActiveConnectionLimit unlimited() {
        return Unlimited.INSTANCE;
    }

    /**
     * Fixed active connection limit per route.
     *
     * @param limit maximum leased HTTP/1.1 connections per route.
     * @return a fixed active connection policy.
     */
    static ActiveConnectionLimit fixed(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("active connection limit must be positive: " + limit);
        }
        return new Fixed(limit);
    }

    /**
     * Create a target-free bandit adaptive active connection limit builder.
     *
     * @return a bandit builder.
     */
    static Bandit.Builder adaptiveBandit() {
        return new Bandit.Builder();
    }

    /**
     * Create a target-free latency adaptive active connection limit builder.
     *
     * @return a latency adaptive builder.
     */
    static Latency.Builder adaptiveLatency() {
        return new Latency.Builder();
    }

    State newState(int maxTotalConnections);

    /**
     * Per-route active connection limit state.
     */
    interface State {
        int limit();

        default void onSample(long leasedNanos, int peakInflight) {}
    }

    record Fixed(int value) implements ActiveConnectionLimit {
        public Fixed {
            if (value <= 0) {
                throw new IllegalArgumentException("active connection limit must be positive: " + value);
            }
        }

        @Override
        public State newState(int maxTotalConnections) {
            return new FixedState(Math.min(value, maxTotalConnections));
        }
    }

    enum Unlimited implements ActiveConnectionLimit {
        INSTANCE;

        @Override
        public State newState(int maxTotalConnections) {
            return new FixedState(maxTotalConnections);
        }
    }

    /**
     * Target-free candidate-racing adaptive active connection limit.
     *
     * <p>This controller keeps a fixed set of candidate limits, tracks an EWMA score for each, and periodically probes
     * alternatives instead of walking one step at a time. The score uses observed completion throughput with a penalty
     * for widening tail latency, expressed as {@code p99/p50}.
     */
    record Bandit(
            int[] candidates,
            int initial,
            int windowSize,
            int explorationInterval,
            double ewmaAlpha,
            double tailRatioWeight)
            implements ActiveConnectionLimit {

        public Bandit {
            candidates = normalizeCandidates(candidates);
            if (Arrays.binarySearch(candidates, initial) < 0) {
                throw new IllegalArgumentException("initial must be one of the candidates: " + initial);
            }
            if (windowSize <= 1) {
                throw new IllegalArgumentException("windowSize must be > 1: " + windowSize);
            }
            if (explorationInterval <= 1) {
                throw new IllegalArgumentException("explorationInterval must be > 1: " + explorationInterval);
            }
            if (ewmaAlpha <= 0 || ewmaAlpha > 1) {
                throw new IllegalArgumentException("ewmaAlpha must be > 0 and <= 1: " + ewmaAlpha);
            }
            if (tailRatioWeight < 0) {
                throw new IllegalArgumentException("tailRatioWeight must be >= 0: " + tailRatioWeight);
            }
        }

        @Override
        public State newState(int maxTotalConnections) {
            int[] effectiveCandidates = effectiveCandidates(maxTotalConnections);
            int initialIndex = nearestCandidateIndex(effectiveCandidates, Math.min(initial, maxTotalConnections));
            return new BanditState(
                    effectiveCandidates,
                    initialIndex,
                    windowSize,
                    explorationInterval,
                    ewmaAlpha,
                    tailRatioWeight);
        }

        private int[] effectiveCandidates(int maxTotalConnections) {
            int[] copy = Arrays.stream(candidates)
                    .filter(candidate -> candidate <= maxTotalConnections)
                    .toArray();
            if (copy.length == 0) {
                return new int[] {maxTotalConnections};
            }
            return copy;
        }

        private static int[] normalizeCandidates(int[] candidates) {
            if (candidates == null || candidates.length == 0) {
                throw new IllegalArgumentException("candidates must not be empty");
            }
            int[] copy = Arrays.copyOf(candidates, candidates.length);
            Arrays.sort(copy);
            int write = 0;
            for (int candidate : copy) {
                if (candidate <= 0) {
                    throw new IllegalArgumentException("candidate must be positive: " + candidate);
                }
                if (write == 0 || copy[write - 1] != candidate) {
                    copy[write++] = candidate;
                }
            }
            return Arrays.copyOf(copy, write);
        }

        private static int nearestCandidateIndex(int[] candidates, int value) {
            int index = Arrays.binarySearch(candidates, value);
            if (index >= 0) {
                return index;
            }
            int insertion = -index - 1;
            if (insertion == 0) {
                return 0;
            }
            if (insertion == candidates.length) {
                return candidates.length - 1;
            }
            int lower = candidates[insertion - 1];
            int upper = candidates[insertion];
            return value - lower <= upper - value ? insertion - 1 : insertion;
        }

        public static final class Builder {
            private int[] candidates = {16, 24, 32, 40, 48, 64, 96};
            private int initial = 32;
            private int windowSize = 200;
            private int explorationInterval = 6;
            private double ewmaAlpha = 0.25;
            private double tailRatioWeight = 0.20;

            private Builder() {}

            public Builder candidates(int... candidates) {
                this.candidates = Arrays.copyOf(candidates, candidates.length);
                return this;
            }

            public Builder initial(int initial) {
                this.initial = initial;
                return this;
            }

            public Builder windowSize(int windowSize) {
                this.windowSize = windowSize;
                return this;
            }

            public Builder explorationInterval(int explorationInterval) {
                this.explorationInterval = explorationInterval;
                return this;
            }

            public Builder ewmaAlpha(double ewmaAlpha) {
                this.ewmaAlpha = ewmaAlpha;
                return this;
            }

            public Builder tailRatioWeight(double tailRatioWeight) {
                this.tailRatioWeight = tailRatioWeight;
                return this;
            }

            public ActiveConnectionLimit build() {
                return new Bandit(candidates, initial, windowSize, explorationInterval, ewmaAlpha, tailRatioWeight);
            }
        }
    }

    /**
     * Target-free latency adaptive active connection limit.
     *
     * <p>This controller probes active concurrency up and down and scores each window against the best tail ratio
     * observed for the route. This gives the controller memory of the least-queued regime without requiring an absolute
     * p99 target.
     */
    record Latency(
            int min,
            int initial,
            int max,
            int windowSize,
            int initialStep,
            double tailRatioWeight,
            double throughputNoiseFloor)
            implements ActiveConnectionLimit {

        public Latency {
            if (min <= 0) {
                throw new IllegalArgumentException("min must be positive: " + min);
            }
            if (max < min) {
                throw new IllegalArgumentException("max must be >= min: " + max + " < " + min);
            }
            if (initial < min || initial > max) {
                throw new IllegalArgumentException("initial must be between min and max: " + initial);
            }
            if (windowSize <= 1) {
                throw new IllegalArgumentException("windowSize must be > 1: " + windowSize);
            }
            if (initialStep <= 0) {
                throw new IllegalArgumentException("initialStep must be positive: " + initialStep);
            }
            if (tailRatioWeight < 0) {
                throw new IllegalArgumentException("tailRatioWeight must be >= 0: " + tailRatioWeight);
            }
            if (throughputNoiseFloor < 0) {
                throw new IllegalArgumentException("throughputNoiseFloor must be >= 0: " + throughputNoiseFloor);
            }
        }

        @Override
        public State newState(int maxTotalConnections) {
            int effectiveMin = Math.min(min, maxTotalConnections);
            int effectiveMax = Math.min(max, maxTotalConnections);
            int effectiveInitial = Math.min(Math.max(initial, effectiveMin), effectiveMax);
            return new LatencyState(
                    effectiveMin,
                    effectiveInitial,
                    effectiveMax,
                    windowSize,
                    Math.min(initialStep, Math.max(1, effectiveMax - effectiveMin)),
                    tailRatioWeight,
                    throughputNoiseFloor);
        }

        public static final class Builder {
            private int min = 8;
            private int initial = 32;
            private int max = 128;
            private int windowSize = 200;
            private int initialStep = 4;
            private double tailRatioWeight = 0.25;
            private double throughputNoiseFloor = 0.01;

            private Builder() {}

            public Builder min(int min) {
                this.min = min;
                return this;
            }

            public Builder initial(int initial) {
                this.initial = initial;
                return this;
            }

            public Builder max(int max) {
                this.max = max;
                return this;
            }

            public Builder windowSize(int windowSize) {
                this.windowSize = windowSize;
                return this;
            }

            public Builder initialStep(int initialStep) {
                this.initialStep = initialStep;
                return this;
            }

            public Builder tailRatioWeight(double tailRatioWeight) {
                this.tailRatioWeight = tailRatioWeight;
                return this;
            }

            public Builder throughputNoiseFloor(double throughputNoiseFloor) {
                this.throughputNoiseFloor = throughputNoiseFloor;
                return this;
            }

            public ActiveConnectionLimit build() {
                return new Latency(min, initial, max, windowSize, initialStep, tailRatioWeight, throughputNoiseFloor);
            }
        }
    }

    final class FixedState implements State {
        private final int limit;

        FixedState(int limit) {
            this.limit = limit;
        }

        @Override
        public int limit() {
            return limit;
        }
    }

    final class BanditState implements State {
        private final int[] candidates;
        private final int windowSize;
        private final int explorationInterval;
        private final double ewmaAlpha;
        private final double tailRatioWeight;
        private final long[] samples;
        private final double[] scores;
        private final boolean[] sampled;
        private int currentIndex;
        private int nextProbeIndex;
        private int count;
        private int windows;
        private long windowStartedNanos;
        private int peakInflight;

        BanditState(
                int[] candidates,
                int initialIndex,
                int windowSize,
                int explorationInterval,
                double ewmaAlpha,
                double tailRatioWeight) {
            this.candidates = candidates;
            this.currentIndex = initialIndex;
            this.nextProbeIndex = (initialIndex + 1) % candidates.length;
            this.windowSize = windowSize;
            this.explorationInterval = explorationInterval;
            this.ewmaAlpha = ewmaAlpha;
            this.tailRatioWeight = tailRatioWeight;
            this.samples = new long[windowSize];
            this.scores = new double[candidates.length];
            this.sampled = new boolean[candidates.length];
        }

        @Override
        public int limit() {
            return candidates[currentIndex];
        }

        @Override
        public void onSample(long leasedNanos, int currentPeakInflight) {
            if (count == 0) {
                windowStartedNanos = System.nanoTime();
                peakInflight = 0;
            }
            samples[count++] = leasedNanos;
            peakInflight = Math.max(peakInflight, currentPeakInflight);
            if (count == windowSize) {
                updateLimit();
                count = 0;
            }
        }

        private void updateLimit() {
            double score = score();
            sampled[currentIndex] = true;
            scores[currentIndex] = scores[currentIndex] == 0
                    ? score
                    : scores[currentIndex] * (1 - ewmaAlpha) + score * ewmaAlpha;

            if (peakInflight * 2 < limit()) {
                return;
            }

            windows++;
            if (windows % explorationInterval == 0) {
                currentIndex = nextProbeIndex();
            } else {
                currentIndex = bestIndex();
            }
        }

        private double score() {
            long elapsedNanos = Math.max(1, System.nanoTime() - windowStartedNanos);
            double throughput = (double) windowSize / elapsedNanos;
            long[] copy = Arrays.copyOf(samples, windowSize);
            Arrays.sort(copy);
            long p50 = copy[Math.min(windowSize - 1, windowSize / 2)];
            long p99 = copy[Math.min(windowSize - 1, (int) Math.ceil(windowSize * 0.99) - 1)];
            double tailRatio = p50 == 0 ? 1 : (double) p99 / p50;
            double penalty = 1 + tailRatioWeight * Math.max(0, tailRatio - 1);
            return throughput / penalty;
        }

        private int bestIndex() {
            int best = currentIndex;
            double bestScore = sampled[best] ? scores[best] : Double.NEGATIVE_INFINITY;
            for (int i = 0; i < scores.length; i++) {
                if (sampled[i] && scores[i] > bestScore) {
                    best = i;
                    bestScore = scores[i];
                }
            }
            return best;
        }

        private int nextProbeIndex() {
            for (int i = 0; i < candidates.length; i++) {
                int index = (nextProbeIndex + i) % candidates.length;
                if (!sampled[index]) {
                    nextProbeIndex = (index + 1) % candidates.length;
                    return index;
                }
            }
            int best = bestIndex();
            int offset = 1 + (windows / explorationInterval) % Math.max(1, candidates.length - 1);
            int probe = (best + offset) % candidates.length;
            nextProbeIndex = (probe + 1) % candidates.length;
            return probe;
        }
    }

    final class LatencyState implements State {
        private final int min;
        private final int max;
        private final int windowSize;
        private final double tailRatioWeight;
        private final double throughputNoiseFloor;
        private final long[] samples;
        private int limit;
        private int step;
        private int direction = 1;
        private int count;
        private long windowStartedNanos;
        private int peakInflight;
        private double bestTailRatio = Double.POSITIVE_INFINITY;
        private double lastScore = Double.NaN;

        LatencyState(
                int min,
                int initial,
                int max,
                int windowSize,
                int initialStep,
                double tailRatioWeight,
                double throughputNoiseFloor) {
            this.min = min;
            this.limit = initial;
            this.max = max;
            this.windowSize = windowSize;
            this.step = initialStep;
            this.tailRatioWeight = tailRatioWeight;
            this.throughputNoiseFloor = throughputNoiseFloor;
            this.samples = new long[windowSize];
        }

        @Override
        public int limit() {
            return limit;
        }

        @Override
        public void onSample(long leasedNanos, int currentPeakInflight) {
            if (count == 0) {
                windowStartedNanos = System.nanoTime();
                peakInflight = 0;
            }
            samples[count++] = leasedNanos;
            peakInflight = Math.max(peakInflight, currentPeakInflight);
            if (count == windowSize) {
                updateLimit();
                count = 0;
            }
        }

        private void updateLimit() {
            WindowScore window = WindowScore.create(samples, windowSize, windowStartedNanos);
            bestTailRatio = Math.min(bestTailRatio, window.tailRatio);

            if (peakInflight * 2 < limit) {
                lastScore = window.score(bestTailRatio, tailRatioWeight);
                return;
            }

            double score = window.score(bestTailRatio, tailRatioWeight);
            if (!Double.isNaN(lastScore) && score < lastScore * (1 - throughputNoiseFloor)) {
                direction = -direction;
                step = Math.max(1, step / 2);
            }

            lastScore = score;
            move();
        }

        private void move() {
            int next = Math.max(min, Math.min(max, limit + direction * step));
            if (next == limit) {
                direction = -direction;
                next = Math.max(min, Math.min(max, limit + direction * step));
            }
            limit = next;
        }

        private record WindowScore(double throughput, double tailRatio) {
            static WindowScore create(long[] samples, int windowSize, long windowStartedNanos) {
                long elapsedNanos = Math.max(1, System.nanoTime() - windowStartedNanos);
                double throughput = (double) windowSize / elapsedNanos;
                long[] copy = Arrays.copyOf(samples, windowSize);
                Arrays.sort(copy);
                long p50 = copy[Math.min(windowSize - 1, windowSize / 2)];
                long p99 = copy[Math.min(windowSize - 1, (int) Math.ceil(windowSize * 0.99) - 1)];
                double tailRatio = p50 == 0 ? 1 : (double) p99 / p50;
                return new WindowScore(throughput, tailRatio);
            }

            double score(double baselineTailRatio, double tailRatioWeight) {
                double excessTail = Math.max(0, tailRatio - baselineTailRatio);
                double penalty = 1 + tailRatioWeight * excessTail;
                return throughput / penalty;
            }
        }
    }
}
