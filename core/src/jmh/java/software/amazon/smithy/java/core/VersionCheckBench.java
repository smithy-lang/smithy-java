/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures the startup cost of the version compatibility check.
 *
 * <p>In production, {@code VersionCheck.check()} runs exactly once during class
 * initialization. This benchmark measures the per-invocation cost to quantify
 * the one-time startup impact.
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class VersionCheckBench {

    private static final String VERSION = Version.VERSION;

    @Setup
    @SuppressFBWarnings(value = "LG_LOST_LOGGER_DUE_TO_WEAK_REFERENCE", justification = "Intentional for benchmark")
    public void setup() {
        Logger.getLogger(VersionCheck.class.getName()).setLevel(Level.OFF);
    }

    @Benchmark
    public void versionCheckEnabled() {
        VersionCheck.check(VERSION);
    }

    @Benchmark
    public void versionCheckSkipped() {
        System.setProperty("smithy.java.skipVersionCheck", "true");
        try {
            VersionCheck.check(VERSION);
        } finally {
            System.clearProperty("smithy.java.skipVersionCheck");
        }
    }
}
