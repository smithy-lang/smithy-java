/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.testmodels.Bird;
import software.amazon.smithy.java.runtime.core.testmodels.Person;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.SensitiveTrait;

public class ToStringSerializerTest {
    @Test
    public void writesStructToString() {
        var bird = Bird.builder().name("foo").build();

        assertThat(bird.toString(), equalTo("Bird[name=foo]"));
    }

    @Test
    public void writesStructToStringMultipleValues() {
        Map<String, List<String>> tags = new LinkedHashMap<>();
        tags.put("a", List.of("b", "c"));
        tags.put("b", List.of("d"));
        tags.put("c", List.of());

        var person = Person.builder()
            .name("Mike")
            .age(102)
            .birthday(Instant.EPOCH)
            .binary("hello".getBytes(StandardCharsets.UTF_8))
            .queryParams(Map.of("a", List.of("1", "2")))
            .build();

        assertThat(
            person.toString(),
            equalTo("Person[name=Mike, age=102, binary=68656c6c6f, birthday=*REDACTED*, queryParams={a=[1, 2]}]")
        );
    }

    @Test
    public void redactsSensitiveKeys() {
        var mapMemberSchema = Schema.builder()
            .type(ShapeType.STRING)
            .id("smithy.example#Str")
            .traits(new SensitiveTrait())
            .build();
        var mapSchema = Schema.builder()
            .type(ShapeType.MAP)
            .id("smithy.example#Map")
            .members(
                Schema.memberBuilder("key", mapMemberSchema),
                Schema.memberBuilder("value", mapMemberSchema)
            )
            .build();
        var schema = Schema.builder()
            .id("smithy.example#Struct")
            .type(ShapeType.STRUCTURE)
            .members(Schema.memberBuilder("foo", mapSchema))
            .build();

        var str = ToStringSerializer.serialize(e -> {
            e.writeStruct(schema, SerializableStruct.create(schema, (s, ser) -> {
                ser.writeMap(s.member("foo"), mapSchema, (innerMapSchema, map) -> {
                    map.writeEntry(innerMapSchema.member("key"), "a", innerMapSchema, (mapSchema2, ms) -> {
                        ms.writeString(mapSchema2.member("value"), "hi");
                    });
                    map.writeEntry(innerMapSchema.member("key"), "b", innerMapSchema, (mapSchema2, ms) -> {
                        ms.writeNull(mapSchema2.member("value"));
                    });
                });
            }));
        });

        assertThat(str, equalTo("Struct[foo={*REDACTED*=*REDACTED*, *REDACTED*=null}]"));
    }

    @Test
    public void redactsSensitiveBlobs() {
        var blobSchema = Schema.builder()
            .type(ShapeType.BLOB)
            .id("smithy.example#Blob")
            .traits(new SensitiveTrait())
            .build();
        var schema = Schema.builder()
            .id("smithy.example#Struct")
            .type(ShapeType.STRUCTURE)
            .members(Schema.memberBuilder("foo", blobSchema))
            .build();

        var str = ToStringSerializer.serialize(e -> {
            e.writeStruct(schema, SerializableStruct.create(schema, (s, ser) -> {
                ser.writeBlob(s.member("foo"), "abc".getBytes(StandardCharsets.UTF_8));
            }));
        });

        assertThat(str, equalTo("Struct[foo=*REDACTED*]"));
    }
}
