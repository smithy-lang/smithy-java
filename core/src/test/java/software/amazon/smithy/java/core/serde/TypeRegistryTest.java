/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.testmodels.Bird;
import software.amazon.smithy.java.core.testmodels.Person;
import software.amazon.smithy.model.shapes.ShapeId;

public class TypeRegistryTest {
    @Test
    public void deserializesFromRegistryToSpecificClass() {
        TypeRegistry registry = TypeRegistry.builder()
            .putType(ShapeId.from("smithy.example#Person"), Person.class, Person::builder)
            .build();

        assertThat(registry.createBuilder(ShapeId.from("smithy.example#Foo"), Person.class), is(nullValue()));
        assertThat(registry.createBuilder(ShapeId.from("smithy.example#Person"), Person.class), not(nullValue()));
    }

    @Test
    public void deserializesFromRegistryWithJustId() {
        TypeRegistry registry = TypeRegistry.builder()
            .putType(ShapeId.from("smithy.example#Person"), Person.class, Person::builder)
            .build();

        assertThat(registry.createBuilder(ShapeId.from("smithy.example#Person")), not(nullValue()));
        assertThat(registry.createBuilder(ShapeId.from("smithy.example#Foo")), is(nullValue()));
    }

    @Test
    public void throwsIfTypeIsIncompatible() {
        TypeRegistry registry = TypeRegistry.builder()
            .putType(ShapeId.from("smithy.example#Person"), Person.class, Person::builder)
            .build();

        Assertions.assertThrows(
            SerializationException.class,
            () -> registry.createBuilder(ShapeId.from("smithy.example#Person"), Bird.class)
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

        assertThat(registry.createBuilder(ShapeId.from("smithy.example#Person")), not(nullValue()));
        assertThat(registry.createBuilder(ShapeId.from("smithy.example#Foo")), is(nullValue()));
        assertThat(registry.createBuilder(ShapeId.from("smithy.example#Bird")), not(nullValue()));
    }

    @Test
    public void deserializesDocumentsFromRegistry() {
        TypeRegistry registry = TypeRegistry.builder()
            .putType(ShapeId.from("smithy.example#Person"), Person.class, Person::builder)
            .build();
        var person = Person.builder().name("Phreddie").build();
        var document = Document.createTyped(person);
        var deserialized = registry.deserialize(document);

        assertThat(deserialized, instanceOf(Person.class));
        assertThat(((Person) deserialized).name(), equalTo("Phreddie"));
    }
}
