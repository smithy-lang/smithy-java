/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Samples CPU and memory usage at a fixed interval. Mirrors the metrics shape
 * of the reference runner so the result tables line up across SDKs.
 */
final class ResourceMonitor {

    private final OperatingSystemMXBean osMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final List<Sample> samples = new ArrayList<>();
    private ScheduledExecutorService scheduler;

    static final class Sample {
        final double cpuPercent;
        final long memoryBytes;

        Sample(double cpu, long mem) {
            this.cpuPercent = cpu;
            this.memoryBytes = mem;
        }
    }

    static final class Stats {
        final double cpuMean;
        final double cpuMax;
        final long memMean;
        final long memMax;
        final int sampleCount;

        Stats(double cpuMean, double cpuMax, long memMean, long memMax, int sampleCount) {
            this.cpuMean = cpuMean;
            this.cpuMax = cpuMax;
            this.memMean = memMean;
            this.memMax = memMax;
            this.sampleCount = sampleCount;
        }

        void print() {
            System.out.println("\n=== Resource Usage Statistics ===");
            System.out.printf("Samples collected: %d%n", sampleCount);
            System.out.println("\nCPU Usage:");
            System.out.printf("  Mean: %.1f%%%n", cpuMean);
            System.out.printf("  Max:  %.1f%%%n", cpuMax);
            System.out.println("\nMemory Usage:");
            System.out.printf("  Mean: %.1f MB%n", memMean / 1024.0 / 1024.0);
            System.out.printf("  Max:  %.1f MB%n", memMax / 1024.0 / 1024.0);
            System.out.println("=================================");
        }

        void printCompact(String prefix) {
            System.out.printf("%sCPU: %.1f%% (max: %.1f%%), Memory: %.1f MB (max: %.1f MB)%n",
                    prefix,
                    cpuMean,
                    cpuMax,
                    memMean / 1024.0 / 1024.0,
                    memMax / 1024.0 / 1024.0);
        }
    }

    void start(long intervalMs) {
        samples.clear();
        // First call returns -1 as a baseline; ignore it.
        osMXBean.getProcessCpuLoad();
        scheduler = Executors.newScheduledThreadPool(1, r -> {
            var t = new Thread(r, "e2e-resource-monitor");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::sample, 0, intervalMs, TimeUnit.MILLISECONDS);
        System.out.println("Started resource monitoring (interval: " + intervalMs + "ms)");
    }

    Stats stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                scheduler.shutdownNow();
            }
        }
        System.out.println("Stopped resource monitoring");
        return computeStats(0);
    }

    synchronized Stats statsSnapshot(int startIndex) {
        return computeStats(startIndex);
    }

    synchronized int sampleCount() {
        return samples.size();
    }

    private synchronized Stats computeStats(int startIndex) {
        if (startIndex >= samples.size()) {
            return new Stats(0, 0, 0, 0, 0);
        }
        var window = new ArrayList<>(samples.subList(startIndex, samples.size()));
        if (window.isEmpty()) {
            return new Stats(0, 0, 0, 0, 0);
        }
        double cpuSum = 0;
        double cpuMax = 0;
        long memSum = 0;
        long memMax = 0;
        for (var s : window) {
            cpuSum += s.cpuPercent;
            cpuMax = Math.max(cpuMax, s.cpuPercent);
            memSum += s.memoryBytes;
            memMax = Math.max(memMax, s.memoryBytes);
        }
        int count = window.size();
        return new Stats(cpuSum / count, cpuMax, memSum / count, memMax, count);
    }

    private synchronized void sample() {
        double cpu = osMXBean.getProcessCpuLoad();
        if (cpu < 0) {
            cpu = 0;
        }
        long mem = memoryMXBean.getHeapMemoryUsage().getUsed() + memoryMXBean.getNonHeapMemoryUsage().getUsed();
        samples.add(new Sample(cpu * 100.0, mem));
    }
}
