/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.validation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import software.amazon.smithy.java.runtime.core.schema.ValidationError;
import software.amazon.smithy.java.runtime.core.schema.Validator;
import software.amazon.smithy.java.runtime.core.schema.model.Person;
import software.amazon.smithy.java.runtime.core.testmodels.PojoWithValidatedCollection;
import software.amazon.smithy.java.runtime.core.testmodels.UnvalidatedPojo;
import software.amazon.smithy.java.runtime.core.testmodels.ValidatedPojo;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Measurement(
    iterations = 3,
    time = 3
)
@BenchmarkMode(Mode.AverageTime)
public class ValidatorBench {

    private Person person;
    private ValidatedPojo validatedPojo;
    private UnvalidatedPojo unvalidatedPojo;
    private PojoWithValidatedCollection pojoWithValidatedCollection;
    private Validator validator;

    @Setup
    public void prepare() {
        validatedPojo = ValidatedPojo.builder().string("hi").integer(1).boxedInteger(2).build();
        person = Person.builder().name("Luka").age(77).favoriteColor("Blue").birthday(Instant.now()).build();
        unvalidatedPojo = UnvalidatedPojo.builder().string("hi").integer(1).boxedInteger(2).build();

        pojoWithValidatedCollection = PojoWithValidatedCollection.builder()
            .list(List.of(validatedPojo, validatedPojo, validatedPojo))
            .map(
                Map.of(
                    "abc",
                    validatedPojo,
                    "def",
                    validatedPojo,
                    "hij",
                    validatedPojo
                )
            )
            .build();
        validator = Validator.builder().build();
    }

    @Benchmark
    public List<ValidationError> validatedPojo() {
        return validator.validate(validatedPojo);
    }

    @Benchmark
    public List<ValidationError> unvalidatedPojo() {
        return validator.validate(unvalidatedPojo);
    }

    @Benchmark
    public List<ValidationError> person() {
        return validator.validate(person);
    }

    @Benchmark
    public List<ValidationError> pojoWithValidatedCollections() {
        return validator.validate(pojoWithValidatedCollection);
    }
}
