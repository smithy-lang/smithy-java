/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types;

import static org.assertj.core.api.Assertions.assertThat;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.codegen.test.model.DoubleMembersInput;
import software.amazon.smithy.java.codegen.test.model.FloatMembersInput;

class EqualsTest {

    private static FloatMembersInput floatInput(float required, float defaultVal) {
        return FloatMembersInput.builder().requiredFloat(required).defaultFloat(defaultVal).build();
    }

    private static DoubleMembersInput doubleInput(double required, double defaultVal) {
        return DoubleMembersInput.builder().requiredDouble(required).defaultDouble(defaultVal).build();
    }

    static Stream<Arguments> equalsContract() {
        return Stream.of(
                Arguments.of("float NaN",
                        floatInput(Float.NaN, Float.NaN),
                        floatInput(Float.NaN, Float.NaN),
                        floatInput(Float.NaN, Float.NaN)),
                Arguments.of("double NaN",
                        doubleInput(Double.NaN, Double.NaN),
                        doubleInput(Double.NaN, Double.NaN),
                        doubleInput(Double.NaN, Double.NaN)),
                Arguments.of("float normal", floatInput(1.5f, 2.5f), floatInput(1.5f, 2.5f), floatInput(1.5f, 2.5f)),
                Arguments.of("double normal", doubleInput(1.5, 2.5), doubleInput(1.5, 2.5), doubleInput(1.5, 2.5)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    @SuppressFBWarnings
    void equalsContract(String label, Object a, Object b, Object c) {
        // Reflexivity
        assertThat(a.equals(a)).isTrue();
        // Symmetry
        assertThat(a.equals(b)).isTrue();
        assertThat(b.equals(a)).isTrue();
        // Transitivity
        assertThat(b.equals(c)).isTrue();
        assertThat(a.equals(c)).isTrue();
        // Consistency
        assertThat(a.equals(b)).isTrue();
        // Null
        assertThat(a.equals(null)).isFalse();
        // hashCode
        assertThat(a.hashCode()).isEqualTo(b.hashCode()).isEqualTo(c.hashCode());
    }

    static Stream<Arguments> inequality() {
        return Stream.of(
                Arguments.of("float different values", floatInput(1.5f, 2.5f), floatInput(3.0f, 2.5f)),
                Arguments.of("double different values", doubleInput(1.5, 2.5), doubleInput(3.0, 2.5)),
                Arguments.of("float +0.0 vs -0.0", floatInput(+0.0f, +0.0f), floatInput(-0.0f, -0.0f)),
                Arguments.of("double +0.0 vs -0.0", doubleInput(+0.0, +0.0), doubleInput(-0.0, -0.0)),
                Arguments.of("float NaN vs non-NaN", floatInput(Float.NaN, 1.0f), floatInput(1.0f, 1.0f)),
                Arguments.of("double NaN vs non-NaN", doubleInput(Double.NaN, 1.0), doubleInput(1.0, 1.0)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void inequality(String label, Object a, Object b) {
        assertThat(a.equals(b)).isFalse();
        assertThat(b.equals(a)).isFalse();
    }
}
