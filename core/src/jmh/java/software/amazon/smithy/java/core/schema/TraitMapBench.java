/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
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
import org.openjdk.jmh.infra.Blackhole;
import software.amazon.smithy.model.traits.DeprecatedTrait;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.HttpChecksumRequiredTrait;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.HttpPrefixHeadersTrait;
import software.amazon.smithy.model.traits.HttpQueryParamsTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.SensitiveTrait;
import software.amazon.smithy.model.traits.Trait;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(
        iterations = 2,
        time = 3)
@Measurement(
        iterations = 3,
        time = 3)
@BenchmarkMode(Mode.AverageTime)
@Fork(2)
public class TraitMapBench {
    @Benchmark
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void getTrait(Blackhole bh, TraitState s) {
        Class[] t = s.traitClasses;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        TraitMap[] maps = s.defaultMaps;
        int ti = random.nextInt(maps.length);
        int idx = random.nextInt(t.length);
        bh.consume(maps[ti].get(TraitKey.get(t[idx])));
    }

    @Benchmark
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void getTraitWithKey(Blackhole bh, TraitState s) {
        TraitKey[] keys = s.traitKeys;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        TraitMap[] maps = s.defaultMaps;
        int ti = random.nextInt(maps.length);
        int idx = random.nextInt(keys.length);
        bh.consume(maps[ti].get(keys[idx]));
    }

    @State(Scope.Benchmark)
    public static class TraitState {
        TraitMap[] defaultMaps;
        Trait[] allTraits;

        @SuppressWarnings("rawtypes")
        Class[] traitClasses;

        @SuppressWarnings("rawtypes")
        TraitKey[] traitKeys;

        @Setup
        public void setup() {
            var configs = List.of("1-0", "4-2", "5-4", "3-1", "2-0", "6-2", "6-5");
            defaultMaps = new TraitMap[configs.size()];

            for (int i = 0; i < configs.size(); i++) {
                setupConfig(configs.get(i), i);
            }
        }

        @SuppressWarnings("unchecked")
        private void setupConfig(String config, int i) {
            String[] split = config.split("-");
            int size = Integer.parseInt(split[0]);
            int position = Integer.parseInt(split[1]);
            Trait[] traits = new Trait[size];
            traits[position] = new SensitiveTrait();
            Deque<Trait> remaining = new ArrayDeque<>(size);
            remaining.add(DeprecatedTrait.builder().build());
            remaining.add(new ErrorTrait("client"));
            remaining.add(new HttpLabelTrait());
            remaining.add(new HttpQueryTrait("foo"));
            remaining.add(new HttpHeaderTrait("foo"));
            remaining.add(new HttpPayloadTrait());
            remaining.add(new HttpPrefixHeadersTrait("a"));
            remaining.add(new HttpQueryParamsTrait());
            remaining.add(new HttpChecksumRequiredTrait());

            if (size > 1) {
                for (int j = 0; j < size; j++) {
                    if (j != position) {
                        traits[j] = remaining.pop();
                    }
                }
            }

            allTraits = Arrays.copyOf(traits, traits.length + remaining.size());
            int rem = remaining.size();
            for (int j = 0; j < rem; j++) {
                allTraits[allTraits.length - j - 1] = remaining.pop();
            }

            traitClasses = new Class[allTraits.length];
            traitKeys = new TraitKey[allTraits.length];
            for (var j = 0; j < allTraits.length; j++) {
                traitClasses[j] = allTraits[j].getClass();
                traitKeys[j] = TraitKey.get(traitClasses[j]);
            }

            this.defaultMaps[i] = TraitMap.create(traits);
        }
    }
}
