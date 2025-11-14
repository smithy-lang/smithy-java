/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.context;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

// This benchmark isn't actually implementing Context, but is useful to compare the chunk store vs a normal map.
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class MapBench {

    private MapStorageContext context;

    // Pre-create keys to prevent memory leak
    private static final Context.Key<String> K1 = Context.key("1");
    private static final Context.Key<String> K2 = Context.key("2");
    private static final Context.Key<String> K3 = Context.key("3");
    private static final Context.Key<String> K4 = Context.key("4");
    private static final Context.Key<String> K5 = Context.key("5");
    private static final Context.Key<String> K6 = Context.key("6");
    private static final Context.Key<String> K7 = Context.key("7");
    private static final Context.Key<String> K8 = Context.key("8");
    private static final Context.Key<String> K9 = Context.key("9");
    private static final Context.Key<String> K10 = Context.key("10");
    private static final Context.Key<String> K11 = Context.key("11");
    private static final Context.Key<String> K12 = Context.key("12");
    private static final Context.Key<String> K13 = Context.key("13");
    private static final Context.Key<String> K14 = Context.key("14");
    private static final Context.Key<String> K15 = Context.key("15");
    private static final Context.Key<String> K16 = Context.key("16");

    @SuppressWarnings("rawtypes")
    private static final Context.Key[] KEYS = new Context.Key[] {
            K1,
            K2,
            K3,
            K4,
            K5,
            K6,
            K7,
            K8,
            K9,
            K10,
            K11,
            K12,
            K13,
            K14,
            K15,
            K16
    };

    private static final Context.Key<String> MISSING_KEY = Context.key("missing");

    @Setup
    public void setup() {
        context = new MapStorageContext();
        context.put(K1, "a");
        context.put(K2, "b");
        context.put(K3, "c");
        context.put(K4, "d");
        context.put(K5, "e");
        context.put(K6, "f");
        context.put(K7, "g");
        context.put(K8, "h");
        context.put(K9, "i");
        context.put(K10, "j");
        context.put(K11, "k");
        context.put(K12, "l");
        context.put(K13, "m");
        context.put(K14, "n");
        context.put(K15, "o");
        context.put(K16, "p");
    }

    @Benchmark
    public void getRandomKey(Blackhole bh) {
        int idx = ThreadLocalRandom.current().nextInt(KEYS.length);
        bh.consume(context.get(KEYS[idx]));
    }

    @Benchmark
    public void getFirstKey(Blackhole bh) {
        bh.consume(context.get(K1));
    }

    @Benchmark
    public void getLastKey(Blackhole bh) {
        bh.consume(context.get(K16));
    }

    @Benchmark
    public void getMissingKey(Blackhole bh) {
        bh.consume(context.get(MISSING_KEY));
    }

    @Benchmark
    public void putRandomKey(Blackhole bh) {
        MapStorageContext ctx = new MapStorageContext();
        int idx = ThreadLocalRandom.current().nextInt(KEYS.length);
        ctx.put(KEYS[idx], "value");
        bh.consume(ctx);
    }

    @Benchmark
    public void putMultipleKeys(Blackhole bh) {
        MapStorageContext ctx = new MapStorageContext();
        ctx.put(K1, "a");
        ctx.put(K2, "b");
        ctx.put(K3, "c");
        ctx.put(K4, "d");
        ctx.put(K5, "e");
        ctx.put(K6, "f");
        ctx.put(K7, "g");
        ctx.put(K8, "h");
        bh.consume(ctx);
    }

    @Benchmark
    public void copyContext(Blackhole bh) {
        MapStorageContext copy = new MapStorageContext();
        context.copyTo(copy);
        bh.consume(copy);
    }

    @Benchmark
    public void createEmptyContext(Blackhole bh) {
        bh.consume(new MapStorageContext());
    }

    static final class MapStorageContext {

        private final Map<Context.Key<?>, Object> attributes = new HashMap<>();

        public <T> MapStorageContext put(Context.Key<T> key, T value) {
            attributes.put(key, value);
            return this;
        }

        @SuppressWarnings("unchecked")
        public <T> T get(Context.Key<T> key) {
            return (T) attributes.get(key);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public void copyTo(MapStorageContext target) {
            for (var entry : attributes.entrySet()) {
                var key = (Context.Key) entry.getKey();
                target.put(key, key.copyValue(entry.getValue()));
            }
        }
    }
}
