/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import smithy.java.codegen.types.test.model.InterleavedRequiredMembers;
import smithy.java.codegen.types.test.model.RequiredMembers64;
import smithy.java.codegen.types.test.model.RequiredMembers65;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(1)
public class BuilderPresenceBench {

    private static final String V = "v";

    /** Three required members, the common shape size. */
    @Benchmark
    public InterleavedRequiredMembers few() {
        return InterleavedRequiredMembers.builder()
                .requiredA(V)
                .requiredB(V)
                .requiredC(V)
                .build();
    }

    /** 64 required members: the largest shape that tracks presence in a long. */
    @Benchmark
    public RequiredMembers64 mask64() {
        return RequiredMembers64.builder()
                .m0(V)
                .m1(V)
                .m2(V)
                .m3(V)
                .m4(V)
                .m5(V)
                .m6(V)
                .m7(V)
                .m8(V)
                .m9(V)
                .m10(V)
                .m11(V)
                .m12(V)
                .m13(V)
                .m14(V)
                .m15(V)
                .m16(V)
                .m17(V)
                .m18(V)
                .m19(V)
                .m20(V)
                .m21(V)
                .m22(V)
                .m23(V)
                .m24(V)
                .m25(V)
                .m26(V)
                .m27(V)
                .m28(V)
                .m29(V)
                .m30(V)
                .m31(V)
                .m32(V)
                .m33(V)
                .m34(V)
                .m35(V)
                .m36(V)
                .m37(V)
                .m38(V)
                .m39(V)
                .m40(V)
                .m41(V)
                .m42(V)
                .m43(V)
                .m44(V)
                .m45(V)
                .m46(V)
                .m47(V)
                .m48(V)
                .m49(V)
                .m50(V)
                .m51(V)
                .m52(V)
                .m53(V)
                .m54(V)
                .m55(V)
                .m56(V)
                .m57(V)
                .m58(V)
                .m59(V)
                .m60(V)
                .m61(V)
                .m62(V)
                .m63(V)
                .build();
    }

    /** 65 required members: the smallest shape that falls back to a PresenceTracker. */
    @Benchmark
    public RequiredMembers65 tracker65() {
        return RequiredMembers65.builder()
                .m0(V)
                .m1(V)
                .m2(V)
                .m3(V)
                .m4(V)
                .m5(V)
                .m6(V)
                .m7(V)
                .m8(V)
                .m9(V)
                .m10(V)
                .m11(V)
                .m12(V)
                .m13(V)
                .m14(V)
                .m15(V)
                .m16(V)
                .m17(V)
                .m18(V)
                .m19(V)
                .m20(V)
                .m21(V)
                .m22(V)
                .m23(V)
                .m24(V)
                .m25(V)
                .m26(V)
                .m27(V)
                .m28(V)
                .m29(V)
                .m30(V)
                .m31(V)
                .m32(V)
                .m33(V)
                .m34(V)
                .m35(V)
                .m36(V)
                .m37(V)
                .m38(V)
                .m39(V)
                .m40(V)
                .m41(V)
                .m42(V)
                .m43(V)
                .m44(V)
                .m45(V)
                .m46(V)
                .m47(V)
                .m48(V)
                .m49(V)
                .m50(V)
                .m51(V)
                .m52(V)
                .m53(V)
                .m54(V)
                .m55(V)
                .m56(V)
                .m57(V)
                .m58(V)
                .m59(V)
                .m60(V)
                .m61(V)
                .m62(V)
                .m63(V)
                .m64(V)
                .build();
    }
}
