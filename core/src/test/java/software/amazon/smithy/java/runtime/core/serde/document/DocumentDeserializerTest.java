/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import static java.nio.ByteBuffer.wrap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.testmodels.Person;

public class DocumentDeserializerTest {
    @Test
    public void deserializesMapIntoBuilder() {
        Person.Builder builder = Person.builder();

        var document = Document.createStringMap(
            Map.of(
                "name",
                Document.createString("Savage Bob"),
                "age",
                Document.createInteger(100),
                "birthday",
                Document.createTimestamp(Instant.EPOCH),
                "binary",
                Document.createBlob(wrap("hi".getBytes(StandardCharsets.UTF_8)))
            )
        );

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

        var bobDocument = Document.createTyped(person);
        var personCopy = bobDocument.asShape(Person.builder());

        assertThat(personCopy.name(), is("Savage Bob"));
        assertThat(personCopy.age(), is(100));
        assertThat(personCopy.birthday(), is(Instant.EPOCH));
        assertThat(personCopy.binary(), is(wrap("hi".getBytes(StandardCharsets.UTF_8))));
    }
}
