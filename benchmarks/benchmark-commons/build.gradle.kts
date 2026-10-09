plugins {
    id("smithy-java.java-conventions")
}

description = "Shared, non-published utilities for smithy-java benchmarks."

// Baseline note: the JMH-based OpsPerCpuSecondProfiler is omitted here (the 2026-02 catalog has no jmh
// entry and the baseline measures only the in-process stub via the plain CPU-time loop). ProcessCpuTime
// is pure JDK and is all the e2e harness needs.
