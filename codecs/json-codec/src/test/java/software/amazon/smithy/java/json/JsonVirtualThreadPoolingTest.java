/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.json.bench.model.SimpleStruct;
import software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider;

/**
 * Stresses the serializer pool and the deserializer string-dedup cache pool from many
 * virtual threads concurrently. Both pools are shared across platform and virtual threads;
 * if a pooled serializer or cache were ever handed to two threads at once, concurrent writes
 * would corrupt output and the per-thread round-trip equality check below would fail.
 */
public class JsonVirtualThreadPoolingTest {

    private static final JsonCodec CODEC = JsonCodec.builder()
            .overrideSerdeProvider(new SmithyJsonSerdeProvider())
            .useTimestampFormat(true)
            .build();

    @Test
    void concurrentVirtualThreadRoundtripsAreIsolated() throws Exception {
        int threads = 2_000;
        int iterationsPerThread = 50;

        // Each thread serializes a struct unique to it (distinct string fields) so that any
        // cross-thread cache/serializer bleed produces a value mismatch, not a silent pass.
        var startGate = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        var mismatches = new AtomicInteger();
        var completed = new AtomicInteger();

        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>(threads);
            for (int t = 0; t < threads; t++) {
                final int id = t;
                futures.add(exec.submit(() -> {
                    try {
                        startGate.await();
                        for (int i = 0; i < iterationsPerThread; i++) {
                            var original = SimpleStruct.builder()
                                    .name("thread-" + id + "-iter-" + i)
                                    .age(id)
                                    .active((id & 1) == 0)
                                    .score(id + i / 100.0)
                                    .build();
                            ByteBuffer serialized = CODEC.serialize(original);
                            byte[] bytes = new byte[serialized.remaining()];
                            serialized.get(bytes);
                            var roundtripped = CODEC.deserializeShape(bytes, SimpleStruct.builder());
                            if (!roundtripped.equals(original)) {
                                mismatches.incrementAndGet();
                            }
                            completed.incrementAndGet();
                        }
                    } catch (Throwable e) {
                        failure.compareAndSet(null, e);
                    }
                }));
            }
            startGate.countDown();
            for (var f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
        }

        if (failure.get() != null) {
            throw new AssertionError("Virtual thread task threw", failure.get());
        }
        assertThat(completed.get()).isEqualTo(threads * iterationsPerThread);
        assertThat(mismatches.get()).as("round-trip mismatches under concurrent VT pooling").isZero();
    }
}
