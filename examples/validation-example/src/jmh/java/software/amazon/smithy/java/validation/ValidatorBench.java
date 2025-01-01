/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.validation;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
import software.amazon.smithy.java.core.schema.ValidationError;
import software.amazon.smithy.java.core.schema.Validator;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.validation.example.model.Person;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Measurement(
        iterations = 3,
        time = 3)
@BenchmarkMode(Mode.AverageTime)
public class ValidatorBench {

    private Person person;
    private Validator validator;
    private JsonCodec codec;

    @Setup
    public void setup() {
        validator = Validator.builder().build();
        person = Person.builder().name("Luka").age(77).favoriteColor("Blue").birthday(Instant.now()).build();
        codec = JsonCodec.builder().build();
    }

    //    @Benchmark
    //    public List<ValidationError> person() {
    //        return validator.validate(person);
    //    }
    //
    //    @Benchmark
    //    public List<ValidationError> personOldValidate() {
    //        return validator.oldValidate(person);
    //    }

    @Benchmark
    public List<ValidationError> personDeserializeAndValidate2Pass() {
        var deser = codec.createDeserializer("""
                {"name":"Luka", "age":77, "favoriteColor":"Blue", "birthday":1736124309,
                "queryParams":{"a":["a","b","c"], "c" : ["c", "d", "e"]}}
                """.getBytes(StandardCharsets.UTF_8));
        return validator.validate(Person.builder().deserialize(deser).build());
    }

    @Benchmark
    public List<ValidationError> personDeserializeAndValidate1Pass() {
        var deser = codec.createDeserializer("""
                {"name":"Luka", "age":77, "favoriteColor":"Blue", "birthday":1736124309,
                "queryParams":{ "a":["a","b","c"], "c" : ["c", "d", "e"]}}
                """.getBytes(StandardCharsets.UTF_8));
        return validator.deserializeAndValidate(Person.builder(), deser);
    }

    public static void main(String[] args) {
        ValidatorBench bench = new ValidatorBench();
        bench.setup();
        while (true) {
            bench.personDeserializeAndValidate1Pass();
        }
    }

}
