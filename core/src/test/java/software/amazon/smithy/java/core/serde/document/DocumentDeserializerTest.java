/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.document;

import static java.nio.ByteBuffer.wrap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.testmodels.Person;
import software.amazon.smithy.model.shapes.ShapeId;

public class DocumentDeserializerTest {
    @Test
    public void deserializesMapIntoBuilder() {
        Person.Builder builder = Person.builder();

        var document = Document.of(
                Map.of(
                        "name",
                        Document.of("Savage Bob"),
                        "age",
                        Document.of(100),
                        "birthday",
                        Document.of(Instant.EPOCH),
                        "binary",
                        Document.of(wrap("hi".getBytes(StandardCharsets.UTF_8)))));

        var person = document.asShape(builder);

        assertThat(person.name(), is("Savage Bob"));
        assertThat(person.age(), is(100));
        assertThat(person.birthday(), is(Instant.EPOCH));
        assertThat(person.binary(), is(wrap("hi".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void deserializesStructIntoBuilder() {
        Person person = Person.builder()
                .name("Savage Bob")
                .age(100)
                .birthday(Instant.EPOCH)
                .binary(wrap("hi".getBytes(StandardCharsets.UTF_8)))
                .build();

        var bobDocument = Document.of(person);
        var personCopy = bobDocument.asShape(Person.builder());

        assertThat(personCopy.name(), is("Savage Bob"));
        assertThat(personCopy.age(), is(100));
        assertThat(personCopy.birthday(), is(Instant.EPOCH));
        assertThat(personCopy.binary(), is(wrap("hi".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void discriminatorThrowsWithNiceMessageWhenTextIsNull() {
        var e = Assertions.assertThrows(
                DiscriminatorException.class,
                () -> DocumentDeserializer.parseDiscriminator(null, null));

        assertThat(e.getMessage(), equalTo("Unable to find a document discriminator"));
    }

    @Test
    public void discriminatorThrowsWhenShapeIdInvalid() {
        var e = Assertions.assertThrows(
                DiscriminatorException.class,
                () -> DocumentDeserializer.parseDiscriminator("com.foo#Bar!!", null));

        assertThat(
                e.getMessage(),
                equalTo("Unable to parse the document discriminator into a valid shape ID: com.foo#Bar!!"));
    }

    @Test
    public void discriminatorThrowsWhenShapeIdInvalidAndRelative() {
        var e = Assertions.assertThrows(
                DiscriminatorException.class,
                () -> DocumentDeserializer.parseDiscriminator("Bar", null));

        assertThat(
                e.getMessage(),
                equalTo(
                        "Attempted to parse a document discriminator that only provides a "
                                + "shape name, but no default namespace was configured: Bar"));
    }

    @Test
    public void discriminatorParsesAbsolute() {
        assertThat(DocumentDeserializer.parseDiscriminator("foo#Bar", null), equalTo(ShapeId.from("foo#Bar")));
    }

    @Test
    public void discriminatorParsesRelative() {
        assertThat(DocumentDeserializer.parseDiscriminator("Bar", "foo"), equalTo(ShapeId.from("foo#Bar")));
    }
}
