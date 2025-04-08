/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicschemas;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

public class ContentDocumentTest {
    @Test
    public void serializesDocumentsNormallyWithSchema() {
        var schema = Schema.createDocument(ShapeId.from("foo#Bar$baz"));
        var value = Document.of("hi");
        var contentDocument = StructDocument.convertDocument(schema, value, schema.id());

        assertThat(contentDocument.type(), is(ShapeType.DOCUMENT));
        assertThat(contentDocument.asString(), equalTo("hi"));
        assertThat(contentDocument.asObject(), equalTo("hi"));

        contentDocument.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(Schema s1, Document value) {
                assertThat(s1, equalTo(schema));
                assertThat(value, is(contentDocument));
            }
        });

        var codec = JsonCodec.builder().useJsonName(true).build();
        assertThat(codec.serializeToString(contentDocument), equalTo("\"hi\""));
    }

    @Test
    public void providesAccessToMembersOfMap() {
        var stringSchema = Schema.createString(ShapeId.from("foo#Baz"));
        var schema = Schema.mapBuilder(ShapeId.from("foo#Bar"))
                .putMember("key", stringSchema)
                .putMember("value", stringSchema)
                .build();

        Map<String, String> map = new LinkedHashMap<>();
        map.put("foo", "bar");
        map.put("baz", null);
        map.put("bam", "qux");
        var value = Document.ofObject(map);
        var contentDocument = StructDocument.convertDocument(schema, value, schema.id());

        assertThat(contentDocument.type(), is(ShapeType.MAP));

        assertThat(contentDocument.asObject(), equalTo(map));
        assertThat(contentDocument.getMember("foo"), not(nullValue()));
        assertThat(contentDocument.getMemberNames(), equalTo(Set.of("foo", "baz", "bam")));

        contentDocument.serialize(new SpecificShapeSerializer() {
            @Override
            public <T> void writeMap(Schema s1, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
                assertThat(s1, equalTo(schema));
            }
        });

        var codec = JsonCodec.builder().useJsonName(true).build();
        assertThat(codec.serializeToString(contentDocument), equalTo("{\"foo\":\"bar\",\"baz\":null,\"bam\":\"qux\"}"));
    }

    @Test
    public void serializesLists() {
        var schema = Schema.listBuilder(ShapeId.from("foo#Bar"))
                .putMember("member", Schema.createString(ShapeId.from("foo#Baz")))
                .build();
        var value = Document.ofObject(Arrays.asList("foo", null, "bar"));
        var contentDocument = StructDocument.convertDocument(schema, value, schema.id());

        assertThat(contentDocument.type(), is(ShapeType.LIST));
        assertThat(contentDocument.asObject(), equalTo(Arrays.asList("foo", null, "bar")));

        contentDocument.serialize(new SpecificShapeSerializer() {
            @Override
            public <T> void writeList(Schema s1, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
                assertThat(s1, equalTo(schema));
            }
        });

        var codec = JsonCodec.builder().useJsonName(true).build();
        assertThat(codec.serializeToString(contentDocument), equalTo("[\"foo\",null,\"bar\"]"));
    }
}
