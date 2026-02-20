/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Microbenchmark comparing StreamRegistry-style lookup vs ConcurrentHashMap.
 *
 * <p>Run with: ./gradlew :http:http-client:jmh -Pjmh.includes="StreamRegistryBenchmark"
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgs = {"-Xms1g", "-Xmx1g"})
@State(Scope.Benchmark)
public class StreamRegistryBenchmark {

    @Param({"10", "100", "1000"})
    private int activeStreams;

    // StreamRegistry-style array lookup
    private static final int SLOTS = 4096;
    private static final int SLOT_MASK = SLOTS - 1;
    private AtomicReferenceArray<Entry> array;

    // CHM baseline
    private ConcurrentHashMap<Integer, Entry> chm;

    private int[] streamIds;

    static final class Entry {
        final int streamId;
        Entry(int streamId) {
            this.streamId = streamId;
        }
    }

    private static int slot(int streamId) {
        return ((streamId - 1) >>> 1) & SLOT_MASK;
    }

    private Entry arrayGet(int streamId) {
        Entry e = array.get(slot(streamId));
        return (e != null && e.streamId == streamId) ? e : null;
    }

    @State(Scope.Thread)
    public static class ThreadState {
        int index;
    }

    @Setup
    public void setup() {
        array = new AtomicReferenceArray<>(SLOTS);
        chm = new ConcurrentHashMap<>();
        streamIds = new int[activeStreams];

        for (int i = 0; i < activeStreams; i++) {
            int streamId = 2 * i + 1; // HTTP/2 client stream IDs: 1, 3, 5, ...
            streamIds[i] = streamId;
            Entry e = new Entry(streamId);
            array.set(slot(streamId), e);
            chm.put(streamId, e);
        }
    }

    @Benchmark
    @Threads(1)
    public Entry arrayGet_1t(ThreadState ts) {
        return arrayGet(streamIds[ts.index++ % activeStreams]);
    }

    @Benchmark
    @Threads(1)
    public Entry chmGet_1t(ThreadState ts) {
        return chm.get(streamIds[ts.index++ % activeStreams]);
    }

    @Benchmark
    @Threads(4)
    public Entry arrayGet_4t(ThreadState ts) {
        return arrayGet(streamIds[ts.index++ % activeStreams]);
    }

    @Benchmark
    @Threads(4)
    public Entry chmGet_4t(ThreadState ts) {
        return chm.get(streamIds[ts.index++ % activeStreams]);
    }
}
