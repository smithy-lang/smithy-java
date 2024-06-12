/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.testmodels.Bird;
import software.amazon.smithy.java.runtime.core.testmodels.Person;
import software.amazon.smithy.model.shapes.ShapeId;

public class TypeRegistryTest {
    @Test
    public void deserializesDocumentFromRegistryToSpecificClass() {
        TypeRegistry registry = TypeRegistry.builder()
            .putType(ShapeId.from("smithy.example#Person"), Person.class, Person::builder)
            .build();

        assertThat(registry.create(ShapeId.from("smithy.example#Foo"), Person.class).isPresent(), is(false));
        assertThat(registry.create(ShapeId.from("smithy.example#Person"), Person.class).isPresent(), is(true));
    }

    @Test
    public void deserializesDocumentFromRegistryWithJustId() {
        TypeRegistry registry = TypeRegistry.builder()
            .putType(ShapeId.from("smithy.example#Person"), Person.class, Person::builder)
            .build();

        assertThat(registry.create(ShapeId.from("smithy.example#Person")).isPresent(), is(true));
        assertThat(registry.create(ShapeId.from("smithy.example#Foo")).isPresent(), is(false));
    }

    @Test
    public void throwsIfTypeIsIncompatible() {
        TypeRegistry registry = TypeRegistry.builder()
            .putType(ShapeId.from("smithy.example#Person"), Person.class, Person::builder)
            .build();

        Assertions.assertThrows(
            SerializationException.class,
            () -> registry.create(ShapeId.from("smithy.example#Person"), Bird.class)
        );
    }
}
