/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks;

import java.util.Collection;
import java.util.List;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.Aggregator;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ResultRole;
import org.openjdk.jmh.util.SingletonStatistics;

/**
 * Reports completed benchmark operations per process CPU-second.
 */
public final class OpsPerCpuSecondProfiler implements InternalProfiler {

    public static final String METRIC_NAME = "ops_per_cpu_sec";

    private long cpuTimeBefore;

    @Override
    public String getDescription() {
        return "Completed benchmark operations per process CPU-second";
    }

    @Override
    public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
        cpuTimeBefore = ProcessCpuTime.now();
    }

    @Override
    public Collection<? extends Result> afterIteration(
            BenchmarkParams benchmarkParams,
            IterationParams iterationParams,
            IterationResult result
    ) {
        long elapsedCpuNanos = ProcessCpuTime.now() - cpuTimeBefore;
        long operations = result.getMetadata().getAllOps();
        return List.of(new OpsPerCpuSecondResult(operations, elapsedCpuNanos));
    }

    /**
     * Aggregates the numerator and denominator independently so the final score
     * is total operations divided by total CPU time, rather than an unweighted
     * average of per-iteration ratios.
     */
    static final class OpsPerCpuSecondResult extends Result<OpsPerCpuSecondResult> {

        private static final long serialVersionUID = 1L;

        private final long operations;
        private final long cpuNanos;

        OpsPerCpuSecondResult(long operations, long cpuNanos) {
            super(
                    ResultRole.SECONDARY,
                    METRIC_NAME,
                    new SingletonStatistics(ProcessCpuTime.operationsPerSecond(operations, cpuNanos)),
                    "ops/CPU-sec",
                    AggregationPolicy.AVG);
            this.operations = operations;
            this.cpuNanos = cpuNanos;
        }

        @Override
        protected Aggregator<OpsPerCpuSecondResult> getThreadAggregator() {
            return new JoiningAggregator();
        }

        @Override
        protected Aggregator<OpsPerCpuSecondResult> getIterationAggregator() {
            return new JoiningAggregator();
        }

        private static final class JoiningAggregator implements Aggregator<OpsPerCpuSecondResult> {

            @Override
            public OpsPerCpuSecondResult aggregate(Collection<OpsPerCpuSecondResult> results) {
                long operations = 0;
                long cpuNanos = 0;
                for (var result : results) {
                    operations += result.operations;
                    cpuNanos += result.cpuNanos;
                }
                return new OpsPerCpuSecondResult(operations, cpuNanos);
            }
        }
    }
}
