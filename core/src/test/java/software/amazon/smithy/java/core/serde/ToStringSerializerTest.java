/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import static java.nio.ByteBuffer.wrap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.TestHelper;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.testmodels.Bird;
import software.amazon.smithy.java.core.testmodels.Person;
import software.amazon.smithy.model.shapes.ShapeId;
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
                .binary(wrap("hello".getBytes(StandardCharsets.UTF_8)))
                .queryParams(Map.of("a", List.of("1", "2")))
                .build();

        assertThat(
                person.toString(),
                equalTo("Person[name=Mike, age=102, binary=68656c6c6f, birthday=*REDACTED*, queryParams={a=[1, 2]}]"));
    }

    @Test
    public void redactsSensitiveKeys() {
        var mapMemberSchema = Schema.createString(ShapeId.from("smithy.example#Str"), new SensitiveTrait());
        var mapSchema = Schema.mapBuilder(ShapeId.from("smithy.example#Map"))
                .putMember("key", mapMemberSchema)
                .putMember("value", mapMemberSchema)
                .build();
        var schema = Schema.structureBuilder(ShapeId.from("smithy.example#Struct"))
                .putMember("foo", mapSchema)
                .build();

        var str = ToStringSerializer.serialize(e -> {
            e.writeStruct(schema, TestHelper.create(schema, (s, ser) -> {
                ser.writeMap(s.member("foo"), mapSchema, 2, (innerMapSchema, map) -> {
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
        var blobSchema = Schema.createBlob(ShapeId.from("smithy.example#Blob"), new SensitiveTrait());
        var schema = Schema.structureBuilder(ShapeId.from("smithy.example#Struct"))
                .putMember("foo", blobSchema)
                .build();

        var str = ToStringSerializer.serialize(e -> {
            e.writeStruct(schema, TestHelper.create(schema, (s, ser) -> {
                ser.writeBlob(s.member("foo"), wrap("abc".getBytes(StandardCharsets.UTF_8)));
            }));
        });

        assertThat(str, equalTo("Struct[foo=*REDACTED*]"));
    }

    @Test
    public void redactsFullListIfSensitive() {
        var mapSchema = Schema.mapBuilder(ShapeId.from("smithy.example#Map"))
                .putMember("key", PreludeSchemas.STRING)
                .putMember("value", PreludeSchemas.STRING)
                .build();
        var schema = Schema.structureBuilder(ShapeId.from("smithy.example#Struct"))
                .putMember("foo", mapSchema, new SensitiveTrait())
                .build();

        var str = ToStringSerializer.serialize(e -> {
            e.writeStruct(schema, TestHelper.create(schema, (s, ser) -> {
                ser.writeMap(s.member("foo"), mapSchema, 2, (innerMapSchema, map) -> {
                    map.writeEntry(innerMapSchema.member("key"), "a", innerMapSchema, (mapSchema2, ms) -> {
                        ms.writeString(mapSchema2.member("value"), "hi");
                    });
                    map.writeEntry(innerMapSchema.member("key"), "b", innerMapSchema, (mapSchema2, ms) -> {
                        ms.writeNull(mapSchema2.member("value"));
                    });
                });
            }));
        });
    }

    @Test
    public void redactsFullMapIfSensitive() {
        var listSchema = Schema.listBuilder(ShapeId.from("smithy.example#Map"))
                .putMember("member", PreludeSchemas.STRING)
                .build();
        var schema = Schema.structureBuilder(ShapeId.from("smithy.example#Struct"))
                .putMember("foo", listSchema, new SensitiveTrait())
                .build();

        var str = ToStringSerializer.serialize(e -> {
            e.writeStruct(schema, TestHelper.create(schema, (s, ser) -> {
                ser.writeList(s.member("foo"), new ArrayList<>(), 2, (innerListSchema, ls) -> {
                    ls.writeString(listSchema.member("member"), "a");
                    ls.writeString(listSchema.member("member"), "b");
                });
            }));
        });
        assertEquals(str, "Struct[foo=[*REDACTED*]]");
    }

    @Test
    public void redactsFullStructIfSensitive() {
        var nestedStruct = Schema.structureBuilder(ShapeId.from("smithy.example#Nested"))
                .putMember("bar", PreludeSchemas.STRING)
                .build();
        var schema = Schema.structureBuilder(ShapeId.from("smithy.example#Struct"))
                .putMember("foo", nestedStruct, new SensitiveTrait())
                .build();

        var str = ToStringSerializer.serialize(e -> {
            e.writeStruct(schema, TestHelper.create(schema, (s, ser) -> {
                ser.writeStruct(s.member("foo"), TestHelper.create(nestedStruct, (ns, nser) -> {
                    nser.writeString(ns.member("bar"), "baz");
                }));
            }));
        });
        assertEquals(str, "Struct[foo=Nested[*REDACTED*]]");
    }
}
