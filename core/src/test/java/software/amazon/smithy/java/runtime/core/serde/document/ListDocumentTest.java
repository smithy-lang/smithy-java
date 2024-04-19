/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

public class ListDocumentTest {

    @Test
    public void createsDocument() {
        List<Document> values = List.of(Document.of(1), Document.of(2));
        var document = Document.of(values);

        assertThat(document.type(), equalTo(ShapeType.LIST));
        assertThat(document.asList(), equalTo(values));
        assertThat(document, equalTo(Document.of(values)));
    }

    @Test
    public void serializesShape() {
        var document = Document.of(List.of(Document.of("a"), Document.of("b")));

        document.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(Schema schema, Document value) {
                assertThat(value, is(document));
            }
        });
    }

    @Test
    public void serializesContents() {
        List<Document> values = List.of(Document.of("a"), Document.of("b"));
        var document = Document.of(values);

        List<String> writtenStrings = new ArrayList<>();

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            public void writeList(Schema schema, Consumer<ShapeSerializer> consumer) {
                assertThat(schema, equalTo(PreludeSchemas.DOCUMENT));
                consumer.accept(new SpecificShapeSerializer() {
                    @Override
                    public void writeString(Schema schema, String value) {
                        assertThat(schema, equalTo(PreludeSchemas.STRING));
                        writtenStrings.add(value);
                    }
                });
            }
        };

        document.serializeContents(serializer);

        assertThat(writtenStrings, contains("a", "b"));
    }
}
