/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.validation;

import java.util.List;
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
import software.amazon.smithy.java.core.schema.ValidationError;
import software.amazon.smithy.java.core.schema.Validator;
import software.amazon.smithy.java.core.testmodels.StringCompositePojo;
import software.amazon.smithy.java.core.testmodels.StringEnumPojo;
import software.amazon.smithy.java.core.testmodels.StringLengthPojo;
import software.amazon.smithy.java.core.testmodels.StringNoValidationPojo;
import software.amazon.smithy.java.core.testmodels.StringPatternPojo;

/**
 * Benchmarks for string validation dispatch in {@link Validator}.
 *
 * <p>Measures the overhead of string validation dispatch
 * across different constraint combinations: none, length-only, pattern-only,
 * enum-only, and composite (length + pattern).
 */
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
public class StringValidationBench {

    private StringNoValidationPojo noValidation;
    private StringLengthPojo lengthOnly;
    private StringPatternPojo patternOnly;
    private StringEnumPojo enumOnly;
    private StringCompositePojo composite;
    private Validator validator;

    @Setup
    public void prepare() {
        noValidation = StringNoValidationPojo.builder().value("hello123").build();
        lengthOnly = StringLengthPojo.builder().value("hello123").build();
        patternOnly = StringPatternPojo.builder().value("hello123").build();
        enumOnly = StringEnumPojo.builder().value("RED").build();
        composite = StringCompositePojo.builder().value("hello123").build();
        validator = Validator.builder().build();
    }

    @Benchmark
    public List<ValidationError> stringNoValidation() {
        return validator.validate(noValidation);
    }

    @Benchmark
    public List<ValidationError> stringLengthOnly() {
        return validator.validate(lengthOnly);
    }

    @Benchmark
    public List<ValidationError> stringPatternOnly() {
        return validator.validate(patternOnly);
    }

    @Benchmark
    public List<ValidationError> stringEnumOnly() {
        return validator.validate(enumOnly);
    }

    @Benchmark
    public List<ValidationError> stringComposite() {
        return validator.validate(composite);
    }
}
