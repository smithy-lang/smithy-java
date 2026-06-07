/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Thread)
public class StripedPoolBenchmark {

    private static final int BUF_SIZE = 1024;
    private static final int MAX_CACHEABLE_BUF = BUF_SIZE * 4;

    static final class FakeSerializer {
        byte[] buf;
        int pos;

        FakeSerializer() {
            this.buf = new byte[BUF_SIZE];
        }
    }

    private static final StripedPool<FakeSerializer, Void> STRIPED_POOL = new StripedPool<>() {
        @Override
        protected FakeSerializer create(Void ctx) {
            return new FakeSerializer();
        }

        @Override
        protected boolean canPool(FakeSerializer s) {
            return true;
        }

        @Override
        protected void prepareForPool(FakeSerializer s) {
            if (s.buf.length > MAX_CACHEABLE_BUF) {
                s.buf = new byte[BUF_SIZE];
            }
        }

        @Override
        protected boolean reset(FakeSerializer s, Void ctx) {
            s.pos = 0;
            return true;
        }
    };

    @Benchmark
    public void stripedPoolAcquireRelease(Blackhole bh) {
        FakeSerializer s = STRIPED_POOL.acquire(null);
        bh.consume(s.pos);
        STRIPED_POOL.release(s);
    }

    @Benchmark
    public void newAllocEachTime(Blackhole bh) {
        FakeSerializer s = new FakeSerializer();
        bh.consume(s.pos);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djmh.executor=VIRTUAL")
    public void stripedPoolVirtualThread(Blackhole bh) {
        FakeSerializer s = STRIPED_POOL.acquire(null);
        bh.consume(s.pos);
        STRIPED_POOL.release(s);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djmh.executor=VIRTUAL")
    public void newAllocVirtualThread(Blackhole bh) {
        FakeSerializer s = new FakeSerializer();
        bh.consume(s.pos);
    }

    @Benchmark
    @Group("stripedContended")
    @GroupThreads(8)
    public void stripedPoolContended(Blackhole bh) {
        FakeSerializer s = STRIPED_POOL.acquire(null);
        bh.consume(s.pos);
        STRIPED_POOL.release(s);
    }

    @Benchmark
    @Group("newAllocContended")
    @GroupThreads(8)
    public void newAllocContended(Blackhole bh) {
        FakeSerializer s = new FakeSerializer();
        bh.consume(s.pos);
    }
}
