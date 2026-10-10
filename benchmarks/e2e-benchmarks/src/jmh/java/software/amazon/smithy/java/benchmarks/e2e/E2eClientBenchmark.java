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
 * Measures latency and operations per CPU-second with JMH.
 * Use E2eBenchmark for cross-SDK submissions.
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
