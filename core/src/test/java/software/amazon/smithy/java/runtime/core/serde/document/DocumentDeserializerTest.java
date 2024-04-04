/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

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

        var document = Document.ofMap(
            Map.of(
                Document.of("name"),
                Document.of("Savage Bob"),
                Document.of("age"),
                Document.of(100),
                Document.of("birthday"),
                Document.of(Instant.EPOCH),
                Document.of("binary"),
                Document.of("hi".getBytes(StandardCharsets.UTF_8))
            )
        );

        var person = document.asShape(builder);

        assertThat(person.name(), is("Savage Bob"));
        assertThat(person.age(), is(100));
        assertThat(person.birthday(), is(Instant.EPOCH));
        assertThat(person.binary(), is("hi".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void deserializesStructIntoBuilder() {
        Person.Builder builder = Person.builder();

        var document = Document.ofStruct(
            Map.of(
                "name",
                Document.of("Savage Bob"),
                "age",
                Document.of(100),
                "birthday",
                Document.of(Instant.EPOCH),
                "binary",
                Document.of("hi".getBytes(StandardCharsets.UTF_8))
            )
        );

        var person = document.asShape(builder);

        assertThat(person.name(), is("Savage Bob"));
        assertThat(person.age(), is(100));
        assertThat(person.birthday(), is(Instant.EPOCH));
        assertThat(person.binary(), is("hi".getBytes(StandardCharsets.UTF_8)));
    }
}
