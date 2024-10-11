/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
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
    time = 1
)
@Measurement(
    iterations = 3,
    time = 1
)
@BenchmarkMode(Mode.AverageTime)
public class TraitMapBench {

    @Benchmark
    public void getTrait(TraitState s, Blackhole bh) {
        bh.consume(s.traitMap.get(SensitiveTrait.class));
    }

    @State(Scope.Benchmark)
    public static class TraitState {
        // <Size>-<Position of trait to find>
        @Param(
            {
                "1-0",
                "2-0",
                "2-1",
                "3-0",
                "3-1",
                "3-2",
                "4-0",
                "4-1",
                "4-2",
                "4-3",
                "5-0",
                "5-1",
                "5-2",
                "5-3",
                "5-4",
                "6-0",
                "6-1",
                "6-2",
                "6-3",
                "6-4",
                "6-5"
            }
        )
        private String config;

        @Param({"default", "map"})
        private String traitMapType;

        TraitMap traitMap;

        @Setup
        public void setup() throws Exception {
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
                for (int i = 0; i < size; i++) {
                    if (i != position) {
                        traits[i] = remaining.pop();
                    }
                }
            }

            if (traitMapType.equals("map")) {
                var ctor = TraitMap.MapBasedTraitMap.class.getDeclaredConstructors()[0];
                ctor.setAccessible(true);
                this.traitMap = (TraitMap) ctor.newInstance((Object) traits);
            } else {
                this.traitMap = TraitMap.create(traits);
            }
        }
    }
}
