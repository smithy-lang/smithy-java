/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

/**
 * Utilities for measuring operations per process CPU-second.
 */
public final class ProcessCpuTime {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final OperatingSystemMXBean OS_MX_BEAN = loadOperatingSystemMxBean();

    private ProcessCpuTime() {}

    /**
     * Returns CPU time consumed by the current process, in nanoseconds.
     *
     * @return current process CPU time
     * @throws IllegalStateException if process CPU time is unavailable
     */
    public static long now() {
        long cpuTime = OS_MX_BEAN.getProcessCpuTime();
        if (cpuTime < 0) {
            throw new IllegalStateException("Process CPU time is not available on this JVM");
        }
        return cpuTime;
    }

    /**
     * Computes completed operations per process CPU-second.
     *
     * @param operations number of completed operations
     * @param elapsedCpuNanos process CPU time consumed by those operations
     * @return completed operations per process CPU-second
     */
    public static double operationsPerSecond(long operations, long elapsedCpuNanos) {
        if (operations < 0) {
            throw new IllegalArgumentException("operations must not be negative");
        }
        if (elapsedCpuNanos <= 0) {
            throw new IllegalArgumentException("elapsedCpuNanos must be positive");
        }
        return operations * NANOS_PER_SECOND / elapsedCpuNanos;
    }

    private static OperatingSystemMXBean loadOperatingSystemMxBean() {
        var bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof OperatingSystemMXBean osMxBean) {
            return osMxBean;
        }
        throw new IllegalStateException("The current JVM does not expose process CPU time");
    }
}
