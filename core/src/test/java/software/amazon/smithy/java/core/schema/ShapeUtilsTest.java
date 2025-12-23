/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.testmodels.Bird;
import software.amazon.smithy.java.core.testmodels.Person;

class ShapeUtilsTest {
    @Test
    void filtersMembers() {
        var struct = Bird.builder().name("foo").build();
        var filtered = ShapeUtils.withFilteredMembers(Bird.SCHEMA, struct, member -> false);

        var serializer = new SpecificShapeSerializer() {
            private boolean wroteStruct;

            @Override
            public void writeStruct(Schema schema, SerializableStruct struct) {
                wroteStruct = true;
                struct.serializeMembers(this);
            }
        };

        // The filtered serializer doesn't serialize anything, so there's no exception.
        filtered.serialize(serializer);
        assertThat(serializer.wroteStruct).isTrue();

        // If anything is serialized to the serializer, it'll throw.
        assertThatThrownBy(() -> struct.serialize(serializer))
                .isInstanceOf(RuntimeException.class);
    }

    @RepeatedTest(10)
    void generateRandomCreatesValidBird() {
        Bird bird = ShapeUtils.generateRandom(Bird.builder());
        assertThat(bird).isNotNull();
    }

    @RepeatedTest(10)
    void generateRandomCreatesValidPerson() {
        Person person = ShapeUtils.generateRandom(Person.builder());
        assertThat(person).isNotNull();
        // name is required, should always be populated
        assertThat(person.name()).isNotNull();
    }

    @RepeatedTest(10)
    void generateRandomProducesDifferentResults() {
        // Person has required name field, so it's always populated
        Person person1 = ShapeUtils.generateRandom(Person.builder());
        Person person2 = ShapeUtils.generateRandom(Person.builder());
        assertThat(person1).isNotEqualTo(person2);
    }
}
