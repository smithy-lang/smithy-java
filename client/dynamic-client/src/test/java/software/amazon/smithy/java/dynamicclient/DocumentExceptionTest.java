/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;

import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicschemas.StructDocument;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.ErrorTrait;

public class DocumentExceptionTest {

    private static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("foo#Error"), new ErrorTrait("client"))
            .putMember("message", Schema.createString(ShapeId.from("foo#Str")))
            .build();

    @Test
    public void addsMessageWhenFound() {
        var ex = new DocumentException(SCHEMA,
                "hello: oh my!",
                StructDocument.of(SCHEMA,
                        Document.of(Map.of(
                                "message",
                                Document.of("oh my!")))));

        assertThat(ex.toString(), endsWith("hello: oh my!"));
    }

    @Test
    public void doesNotAddMessageTwice() {
        var ex = new DocumentException(SCHEMA,
                "hello: oh my!",
                StructDocument.of(SCHEMA,
                        Document.of(Map.of(
                                "message",
                                Document.of("oh my!")))));

        assertThat(ex.toString(), endsWith("hello: oh my!"));
    }

    @Test
    public void addsNothingWhenNoMessage() {
        var ex = new DocumentException(SCHEMA, "hello", StructDocument.of(SCHEMA, Document.of(Map.of())));

        assertThat(ex.toString(), endsWith("hello"));
    }
}
