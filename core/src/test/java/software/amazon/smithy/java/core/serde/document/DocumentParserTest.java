/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.model.shapes.ShapeId;

public class DocumentParserTest {
    @Test
    public void doesNotDropNullListValues() {
        Schema list = Schema.listBuilder(ShapeId.from("foo#Bar"))
                .putMember("member", PreludeSchemas.STRING)
                .build();

        DocumentParser parser = new DocumentParser();
        parser.writeList(list, null, 4, (_ignore, ser) -> {
            ser.writeString(list.listMember(), "Hi");
            ser.writeNull(list.listMember());
            ser.writeString(list.listMember(), "There");
            ser.writeNull(list.listMember());
        });

        assertThat(parser.getResult().asList(), hasSize(4));
        assertThat(parser.getResult().asList().get(0).asString(), equalTo("Hi"));
        assertThat(parser.getResult().asList().get(1), nullValue());
        assertThat(parser.getResult().asList().get(2).asString(), equalTo("There"));
        assertThat(parser.getResult().asList().get(3), nullValue());
    }

    @Test
    void writesMapWithDocumentSchema() {
        DocumentParser parser = new DocumentParser();
        parser.writeMap(PreludeSchemas.DOCUMENT, null, 2, (_ignore, ser) -> {
            ser.writeEntry(PreludeSchemas.STRING, "key1", null, (state, valueSer) -> {
                valueSer.writeString(PreludeSchemas.STRING, "value1");
            });
            ser.writeEntry(PreludeSchemas.STRING, "key2", null, (state, valueSer) -> {
                valueSer.writeInteger(PreludeSchemas.INTEGER, 42);
            });
        });

        assertThat(parser.getResult().asStringMap(), aMapWithSize(2));
        assertThat(parser.getResult().asStringMap().get("key1").asString(), equalTo("value1"));
        assertThat(parser.getResult().asStringMap().get("key2").asNumber().intValue(), equalTo(42));
    }
}
