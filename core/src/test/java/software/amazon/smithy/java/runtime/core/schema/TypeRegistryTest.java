/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

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

        assertThat(registry.create(ShapeId.from("smithy.example#Foo"), Person.class), is(nullValue()));
        assertThat(registry.create(ShapeId.from("smithy.example#Person"), Person.class), not(nullValue()));
    }

    @Test
    public void deserializesDocumentFromRegistryWithJustId() {
        TypeRegistry registry = TypeRegistry.builder()
            .putType(ShapeId.from("smithy.example#Person"), Person.class, Person::builder)
            .build();

        assertThat(registry.create(ShapeId.from("smithy.example#Person")), not(nullValue()));
        assertThat(registry.create(ShapeId.from("smithy.example#Foo")), is(nullValue()));
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

    @Test
    public void composesRegistries() {
        TypeRegistry a = TypeRegistry.builder()
            .putType(ShapeId.from("smithy.example#Person"), Person.class, Person::builder)
            .build();

        TypeRegistry b = TypeRegistry.builder()
            .putType(ShapeId.from("smithy.example#Bird"), Bird.class, Bird::builder)
            .build();

        TypeRegistry registry = TypeRegistry.compose(a, b);

        assertThat(registry.create(ShapeId.from("smithy.example#Person")), not(nullValue()));
        assertThat(registry.create(ShapeId.from("smithy.example#Foo")), is(nullValue()));
        assertThat(registry.create(ShapeId.from("smithy.example#Bird")), not(nullValue()));
    }
}
