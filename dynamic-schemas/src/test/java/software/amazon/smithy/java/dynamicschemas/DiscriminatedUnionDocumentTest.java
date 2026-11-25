/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicschemas;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * End-to-end verification that the dynamic-client path ({@link SchemaGuidedDocumentBuilder} +
 * {@link StructDocument}) both decodes the flat {@code @discriminated} encoding and re-encodes it
 * back to the same flat form, driven only by the {@code @discriminated} trait carried onto the
 * runtime schema by {@link SchemaConverter}.
 *
 * <p>This exercises the real dynamic-client consumer rather than a hand-rolled stand-in, and in
 * particular covers the dynamic re-encode of a document-backed value, which the json-codec unit
 * tests cannot reach (those serialize hand-rolled SerializableStruct wrappers, not StructDocuments).
 * It runs against whichever JSON provider the codec resolves by default (the native smithy backend
 * in a normal build); the json-codec unit tests already cover the codec mechanism on both backends.
 */
public class DiscriminatedUnionDocumentTest {

    private static final ShapeId SHAPE_ID = ShapeId.from("smithy.example#Shape");

    private static Schema shapeSchema;

    @BeforeAll
    public static void setup() {
        var model = Model.assembler()
                .addUnparsedModel("test.smithy", """
                        $version: "2"
                        namespace smithy.example

                        @discriminated("type")
                        union Shape {
                            circle: CircleData
                            rectangle: RectangleData
                        }

                        structure CircleData {
                            radius: Integer
                        }

                        structure RectangleData {
                            width: Integer
                            height: Integer
                        }
                        """)
                .assemble()
                .unwrap();
        var converter = new SchemaConverter(model);
        shapeSchema = converter.getSchema(model.expectShape(SHAPE_ID));
    }

    @Test
    public void traitIsCarriedOntoRuntimeSchema() {
        assertThat(
                shapeSchema.hasTrait(TraitKey.get(
                        software.amazon.smithy.model.traits.DiscriminatedTrait.class)),
                equalTo(true));
    }

    @Test
    public void decodesFlatEncodingIntoDocument() {
        var result = decode("{\"type\":\"rectangle\",\"width\":3,\"height\":4}");
        // The document presents the union by its single set member name, with the discriminator stripped.
        assertThat(result.asObject(), equalTo(Map.of("rectangle", Map.of("width", 3, "height", 4))));
        assertThat(result.schema(), equalTo(shapeSchema));
    }

    @Test
    public void decodesFallbackDiscriminatorLast() {
        var result = decode("{\"width\":3,\"height\":4,\"type\":\"rectangle\"}");
        assertThat(result.asObject(), equalTo(Map.of("rectangle", Map.of("width", 3, "height", 4))));
    }

    @Test
    public void reEncodesDocumentBackToFlatForm() {
        var result = decode("{\"type\":\"rectangle\",\"width\":3,\"height\":4}");
        // The crux of this test: re-serializing the document-backed discriminated union must emit the
        // flat internally tagged encoding, not the standard externally tagged {"rectangle":{...}} form.
        assertThat(
                JsonCodec.builder().build().serializeToString(result),
                equalTo("{\"type\":\"rectangle\",\"width\":3,\"height\":4}"));
    }

    @Test
    public void reEncodesSingleFieldVariant() {
        var result = decode("{\"type\":\"circle\",\"radius\":5}");
        assertThat(result.asObject(), equalTo(Map.of("circle", Map.of("radius", 5))));
        assertThat(
                JsonCodec.builder().build().serializeToString(result),
                equalTo("{\"type\":\"circle\",\"radius\":5}"));
    }

    private StructDocument decode(String json) {
        var codec = JsonCodec.builder().build();
        var builder = SchemaConverter.createDocumentBuilder(shapeSchema, SHAPE_ID);
        return codec.deserializeShape(json.getBytes(StandardCharsets.UTF_8), builder);
    }
}
