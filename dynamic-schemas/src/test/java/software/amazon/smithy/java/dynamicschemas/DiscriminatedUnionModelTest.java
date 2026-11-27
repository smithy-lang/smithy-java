/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicschemas;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Exercises {@code @discriminated} through the real dynamic client path using models loaded from Smithy
 * source, covering the cases that depend on how the model is loaded: a bare trait that takes the default
 * discriminator, and unions nested inside the structures that variants target.
 */
public class DiscriminatedUnionModelTest {

    private static final JsonCodec CODEC = JsonCodec.builder().build();

    private static Schema load(String modelText, String shapeName) {
        var model = Model.assembler().addUnparsedModel("test.smithy", modelText).assemble().unwrap();
        return new SchemaConverter(model).getSchema(model.expectShape(ShapeId.from("smithy.example#" + shapeName)));
    }

    private static StructDocument decode(Schema schema, String json) {
        var builder = SchemaConverter.createDocumentBuilder(schema, schema.id());
        return CODEC.deserializeShape(json.getBytes(StandardCharsets.UTF_8), builder);
    }

    // ----- A bare @discriminated uses the default discriminator, "__type" -----

    private static final String BARE_MODEL = """
            $version: "2"
            namespace smithy.example

            @discriminated
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
            """;

    @Test
    public void bareTraitDecodesAndEncodesUsingDefaultDiscriminator() {
        var shape = load(BARE_MODEL, "Shape");
        var result = decode(shape, "{\"__type\":\"rectangle\",\"width\":3,\"height\":4}");

        assertThat(result.asObject(), equalTo(Map.of("rectangle", Map.of("width", 3, "height", 4))));
        assertThat(CODEC.serializeToString(result), equalTo("{\"__type\":\"rectangle\",\"width\":3,\"height\":4}"));
    }

    @Test
    public void bareTraitDecodesWhenDefaultDiscriminatorIsNotFirst() {
        var shape = load(BARE_MODEL, "Shape");
        var result = decode(shape, "{\"width\":3,\"height\":4,\"__type\":\"rectangle\"}");

        assertThat(result.asObject(), equalTo(Map.of("rectangle", Map.of("width", 3, "height", 4))));
        assertThat(CODEC.serializeToString(result), equalTo("{\"__type\":\"rectangle\",\"width\":3,\"height\":4}"));
    }

    @Test
    public void bareTraitDoesNotTreatTypeAsTheDiscriminator() {
        // "type" is just an unknown field when the discriminator is "__type", so the variant ignores it.
        var shape = load(BARE_MODEL, "Shape");
        var result = decode(shape, "{\"__type\":\"circle\",\"type\":\"rectangle\",\"radius\":5}");

        assertThat(result.asObject(), equalTo(Map.of("circle", Map.of("radius", 5))));
    }

    // ----- A union nested in the structure that a variant targets is a nested object -----

    private static final String TEAM_MODEL = """
            $version: "2"
            namespace smithy.example

            @discriminated("sport")
            union Team {
                soccer: SoccerTeam
                basketball: BasketballTeam
            }

            structure SoccerTeam {
                name: String
                league: String
                mascot: Mascot
            }

            structure BasketballTeam {
                name: String
                arena: String
                mascot: Mascot
            }

            @discriminated("kind")
            union Mascot {
                animal: AnimalMascot
                character: CharacterMascot
            }

            structure AnimalMascot {
                species: String
                nickname: String
            }

            structure CharacterMascot {
                name: String
                costume: String
            }
            """;

    private static final String TEAM_JSON =
            "{\"sport\":\"soccer\",\"name\":\"Rovers\",\"league\":\"Premier\","
                    + "\"mascot\":{\"kind\":\"animal\",\"species\":\"lion\",\"nickname\":\"Leo\"}}";

    private static final Map<String, Object> TEAM_OBJECT = Map.of(
            "soccer",
            Map.of(
                    "name",
                    "Rovers",
                    "league",
                    "Premier",
                    "mascot",
                    Map.of("animal", Map.of("species", "lion", "nickname", "Leo"))));

    @Test
    public void nestedDiscriminatedUnionRoundTrips() {
        var team = load(TEAM_MODEL, "Team");
        var result = decode(team, TEAM_JSON);

        assertThat(result.asObject(), equalTo(TEAM_OBJECT));
        assertThat(CODEC.serializeToString(result), equalTo(TEAM_JSON));
    }

    @Test
    public void nestedDiscriminatedUnionDecodesWithBothDiscriminatorsLast() {
        var team = load(TEAM_MODEL, "Team");
        var result = decode(
                team,
                "{\"name\":\"Rovers\",\"league\":\"Premier\","
                        + "\"mascot\":{\"species\":\"lion\",\"nickname\":\"Leo\",\"kind\":\"animal\"},"
                        + "\"sport\":\"soccer\"}");

        assertThat(result.asObject(), equalTo(TEAM_OBJECT));
        // Serialization always writes each discriminator first.
        assertThat(CODEC.serializeToString(result), equalTo(TEAM_JSON));
    }

    @Test
    public void nestedDiscriminatedUnionDecodesWithOuterFirstAndInnerLast() {
        var team = load(TEAM_MODEL, "Team");
        var result = decode(
                team,
                "{\"sport\":\"soccer\",\"name\":\"Rovers\",\"league\":\"Premier\","
                        + "\"mascot\":{\"species\":\"lion\",\"nickname\":\"Leo\",\"kind\":\"animal\"}}");

        assertThat(result.asObject(), equalTo(TEAM_OBJECT));
    }

    @Test
    public void fieldAfterNestedUnionStillReadsCorrectly() {
        // The nested union's closing brace must not be confused with the variant's closing brace.
        var team = load(TEAM_MODEL, "Team");
        var result = decode(
                team,
                "{\"sport\":\"basketball\",\"mascot\":{\"kind\":\"character\",\"name\":\"Hoops\"},\"arena\":\"The Dome\"}");

        assertThat(
                result.asObject(),
                equalTo(Map.of(
                        "basketball",
                        Map.of("arena", "The Dome", "mascot", Map.of("character", Map.of("name", "Hoops"))))));
    }
}
