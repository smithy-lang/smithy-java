/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.BufferPoolMXBean;
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
    private final BufferPoolMXBean directBufferPool = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)
            .stream()
            .filter(b -> "direct".equals(b.getName()))
            .findFirst()
            .orElse(null);
    private final List<Sample> samples = new ArrayList<>();
    private ScheduledExecutorService scheduler;

    static final class Sample {
        final double cpuPercent;
        final long heapBytes;
        final long nonHeapBytes;
        final long directBytes;

        Sample(double cpu, long heap, long nonHeap, long direct) {
            this.cpuPercent = cpu;
            this.heapBytes = heap;
            this.nonHeapBytes = nonHeap;
            this.directBytes = direct;
        }
    }

    static final class Stats {
        final double cpuMean;
        final double cpuMax;
        final long heapMean;
        final long heapMax;
        final long nonHeapMean;
        final long nonHeapMax;
        final long directMean;
        final long directMax;
        final int sampleCount;

        Stats(
                double cpuMean,
                double cpuMax,
                long heapMean,
                long heapMax,
                long nonHeapMean,
                long nonHeapMax,
                long directMean,
                long directMax,
                int sampleCount
        ) {
            this.cpuMean = cpuMean;
            this.cpuMax = cpuMax;
            this.heapMean = heapMean;
            this.heapMax = heapMax;
            this.nonHeapMean = nonHeapMean;
            this.nonHeapMax = nonHeapMax;
            this.directMean = directMean;
            this.directMax = directMax;
            this.sampleCount = sampleCount;
        }

        long memMean() {
            return heapMean + nonHeapMean + directMean;
        }

        long memMax() {
            return heapMax + nonHeapMax + directMax;
        }

        void print() {
            System.out.println("\n=== Resource Usage Statistics ===");
            System.out.printf("Samples collected: %d%n", sampleCount);
            System.out.println("\nCPU Usage:");
            System.out.printf("  Mean: %.1f%%%n", cpuMean);
            System.out.printf("  Max:  %.1f%%%n", cpuMax);
            System.out.println("\nMemory Usage (mean / max):");
            System.out.printf("  Heap:     %6.1f / %6.1f MB%n", heapMean / 1024.0 / 1024.0, heapMax / 1024.0 / 1024.0);
            System.out.printf("  Non-heap: %6.1f / %6.1f MB%n",
                    nonHeapMean / 1024.0 / 1024.0,
                    nonHeapMax / 1024.0 / 1024.0);
            System.out.printf("  Direct:   %6.1f / %6.1f MB%n",
                    directMean / 1024.0 / 1024.0,
                    directMax / 1024.0 / 1024.0);
            System.out.printf("  Total:    %6.1f / %6.1f MB%n",
                    memMean() / 1024.0 / 1024.0,
                    memMax() / 1024.0 / 1024.0);
            System.out.println("=================================");
        }

        void printCompact(String prefix) {
            System.out.printf(
                    "%sCPU: %.1f%% (max: %.1f%%), Heap: %.1f MB (max: %.1f MB), Direct: %.1f MB (max: %.1f MB)%n",
                    prefix,
                    cpuMean,
                    cpuMax,
                    heapMean / 1024.0 / 1024.0,
                    heapMax / 1024.0 / 1024.0,
                    directMean / 1024.0 / 1024.0,
                    directMax / 1024.0 / 1024.0);
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
            return new Stats(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        var window = new ArrayList<>(samples.subList(startIndex, samples.size()));
        if (window.isEmpty()) {
            return new Stats(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        double cpuSum = 0;
        double cpuMax = 0;
        long heapSum = 0;
        long heapMax = 0;
        long nonHeapSum = 0;
        long nonHeapMax = 0;
        long directSum = 0;
        long directMax = 0;
        for (var s : window) {
            cpuSum += s.cpuPercent;
            cpuMax = Math.max(cpuMax, s.cpuPercent);
            heapSum += s.heapBytes;
            heapMax = Math.max(heapMax, s.heapBytes);
            nonHeapSum += s.nonHeapBytes;
            nonHeapMax = Math.max(nonHeapMax, s.nonHeapBytes);
            directSum += s.directBytes;
            directMax = Math.max(directMax, s.directBytes);
        }
        int count = window.size();
        return new Stats(cpuSum / count,
                cpuMax,
                heapSum / count,
                heapMax,
                nonHeapSum / count,
                nonHeapMax,
                directSum / count,
                directMax,
                count);
    }

    private synchronized void sample() {
        double cpu = osMXBean.getProcessCpuLoad();
        if (cpu < 0) {
            cpu = 0;
        }
        long heap = memoryMXBean.getHeapMemoryUsage().getUsed();
        long nonHeap = memoryMXBean.getNonHeapMemoryUsage().getUsed();
        long direct = directBufferPool != null ? directBufferPool.getMemoryUsed() : 0;
        samples.add(new Sample(cpu * 100.0, heap, nonHeap, direct));
    }
}
