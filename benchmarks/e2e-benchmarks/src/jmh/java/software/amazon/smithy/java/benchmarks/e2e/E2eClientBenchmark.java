/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH view of the same end-to-end benchmarks: the same generated clients, inputs and mock transport as the
 * CPU-time runner, driven by JMH for latency percentiles (sample mode) plus {@code ops_per_cpu_sec} from the
 * shared {@code OpsPerCpuSecondProfiler}.
 *
 * <p>Use this for local regression work and profiling. The number submitted to the cross-SDK results comes from
 * {@link E2eBenchmark}, whose loop and stop rule are what the other SDKs run.
 *
 * <p>{@code testCaseId} values are supplied by the Gradle {@code jmh} task from {@code canonical-benchmarks.txt}
 * (override with {@code -Pjmh.testCaseId=a,b,c}).
 */
@State(Scope.Benchmark)
public class E2eClientBenchmark {

    @Param({})
    public String testCaseId;

    private BenchmarkClient client;
    private BenchmarkClient.Call call;

    @Setup(Level.Trial)
    public void setup() {
        BenchmarkCase benchmarkCase = BenchmarkCases.build(testCaseId);
        client = new BenchmarkClient(benchmarkCase.protocol(), new MockHttpTransport(), BenchmarkProtocol.ENDPOINT);
        call = client.prepare(benchmarkCase);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        client.close();
    }

    @Benchmark
    public void call(Blackhole bh) throws Throwable {
        bh.consume(call.invoke());
    }
}
