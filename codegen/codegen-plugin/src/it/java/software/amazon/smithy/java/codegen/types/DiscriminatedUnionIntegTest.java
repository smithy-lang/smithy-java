/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenFeature;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenStats;
import software.amazon.smithy.java.codegen.test.model.AnimalMascotData;
import software.amazon.smithy.java.codegen.test.model.BareCircle;
import software.amazon.smithy.java.codegen.test.model.BareTagged;
import software.amazon.smithy.java.codegen.test.model.BasketballTeamData;
import software.amazon.smithy.java.codegen.test.model.CharacterMascotData;
import software.amazon.smithy.java.codegen.test.model.DiscriminatedUnionsInput;
import software.amazon.smithy.java.codegen.test.model.SoccerTeamData;
import software.amazon.smithy.java.codegen.test.model.SportsTeam;
import software.amazon.smithy.java.codegen.test.model.TeamMascot;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.json.JsonCodec;

/**
 * Round-trips {@code @discriminated} unions through real generated classes and the JSON codec.
 *
 * <p>Each test runs with runtime code generation both disabled and requested, because runtime codegen generates its
 * own union readers and writers and must not bypass the discriminated encoding. On Java versions that do not support
 * it, the request quietly resolves to disabled.
 */
public class DiscriminatedUnionIntegTest {

    private static JsonCodec codec(boolean runtimeCodegen, boolean useJsonName) {
        return JsonCodec.builder().runtimeCodegen(runtimeCodegen).useJsonName(useJsonName).build();
    }

    private static <T extends SerializableShape> T decode(
            JsonCodec codec,
            String json,
            ShapeBuilder<T> builder
    ) {
        return codec.deserializeShape(json.getBytes(StandardCharsets.UTF_8), builder);
    }

    private static SportsTeam soccerTeam() {
        return new SportsTeam.SoccerMember(SoccerTeamData.builder()
                .name("Rovers")
                .league("Premier")
                .mascot(new TeamMascot.AnimalMember(AnimalMascotData.builder()
                        .species("lion")
                        .nickname("Leo")
                        .build()))
                .build());
    }

    private static final String SOCCER_JSON =
            "{\"sport\":\"soccer\",\"name\":\"Rovers\",\"league\":\"Premier\","
                    + "\"mascot\":{\"kind\":\"animal\",\"species\":\"lion\",\"nickname\":\"Leo\"}}";

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void encodesFlatWithEachDiscriminatorFirst(boolean runtimeCodegen) {
        assertEquals(SOCCER_JSON, codec(runtimeCodegen, false).serializeToString(soccerTeam()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void encodesUsingJsonNameForVariantFields(boolean runtimeCodegen) {
        // The discriminator and its value are unaffected by jsonName; only the variant's fields use it.
        assertEquals(
                SOCCER_JSON.replace("\"league\"", "\"League\""),
                codec(runtimeCodegen, true).serializeToString(soccerTeam()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void decodesTheFlatEncoding(boolean runtimeCodegen) {
        assertEquals(soccerTeam(), decode(codec(runtimeCodegen, false), SOCCER_JSON, SportsTeam.builder()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void decodesWhateverTheFieldOrder(boolean runtimeCodegen) {
        var codec = codec(runtimeCodegen, false);
        for (var json : new String[] {
                // Outer discriminator last, inner first.
                "{\"name\":\"Rovers\",\"league\":\"Premier\","
                        + "\"mascot\":{\"kind\":\"animal\",\"species\":\"lion\",\"nickname\":\"Leo\"},"
                        + "\"sport\":\"soccer\"}",
                // Both last.
                "{\"name\":\"Rovers\",\"league\":\"Premier\","
                        + "\"mascot\":{\"species\":\"lion\",\"nickname\":\"Leo\",\"kind\":\"animal\"},"
                        + "\"sport\":\"soccer\"}",
                // Outer first, inner last.
                "{\"sport\":\"soccer\",\"name\":\"Rovers\",\"league\":\"Premier\","
                        + "\"mascot\":{\"species\":\"lion\",\"nickname\":\"Leo\",\"kind\":\"animal\"}}",
                // Discriminators in the middle.
                "{\"name\":\"Rovers\",\"sport\":\"soccer\",\"league\":\"Premier\","
                        + "\"mascot\":{\"species\":\"lion\",\"kind\":\"animal\",\"nickname\":\"Leo\"}}"}) {
            assertEquals(soccerTeam(), decode(codec, json, SportsTeam.builder()), json);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void decodesTheOtherVariants(boolean runtimeCodegen) {
        var codec = codec(runtimeCodegen, false);
        var basketball = new SportsTeam.BasketballMember(BasketballTeamData.builder()
                .name("Hoopers")
                .arena("The Dome")
                .mascot(new TeamMascot.CharacterMember(CharacterMascotData.builder()
                        .name("Slam")
                        .costume("blue")
                        .build()))
                .build());
        var json = "{\"sport\":\"basketball\",\"name\":\"Hoopers\",\"arena\":\"The Dome\","
                + "\"mascot\":{\"kind\":\"character\",\"name\":\"Slam\",\"costume\":\"blue\"}}";

        assertEquals(json, codec.serializeToString(basketball));
        assertEquals(basketball, decode(codec, json, SportsTeam.builder()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bareTraitUsesTheDefaultDiscriminator(boolean runtimeCodegen) {
        var codec = codec(runtimeCodegen, false);
        var circle = new BareTagged.CircleMember(BareCircle.builder().radius(5).build());
        var json = "{\"__type\":\"circle\",\"radius\":5}";

        assertEquals(json, codec.serializeToString(circle));
        assertEquals(circle, decode(codec, json, BareTagged.builder()));
        assertEquals(circle, decode(codec, "{\"radius\":5,\"__type\":\"circle\"}", BareTagged.builder()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unionsAsMembersOfAStructureAreNestedObjects(boolean runtimeCodegen) {
        var codec = codec(runtimeCodegen, false);
        var input = DiscriminatedUnionsInput.builder()
                .team(soccerTeam())
                .bare(new BareTagged.CircleMember(BareCircle.builder().radius(5).build()))
                .build();
        var json = "{\"team\":" + SOCCER_JSON + ",\"bare\":{\"__type\":\"circle\",\"radius\":5}}";

        assertEquals(json, codec.serializeToString(input));
        assertEquals(input, decode(codec, json, DiscriminatedUnionsInput.builder()));
    }

    @Test
    void runtimeCodegenDeclinesDiscriminatedUnions() {
        // Generated union codecs use the standard tagged encoding, so runtime codegen must decline these shapes and
        // fall back to the native serde. The other tests check the resulting wire format; this checks the reason.
        assumeTrue(RuntimeCodegenFeature.available(), "Runtime codegen requires a newer Java version");
        RuntimeCodegenStats.reset();

        var codec = codec(true, false);
        codec.serializeToString(soccerTeam());
        decode(codec, SOCCER_JSON, SportsTeam.builder());

        var stats = RuntimeCodegenStats.snapshot("json");
        assertTrue(stats.unsupported() > 0, "Expected the discriminated union to be declined: " + stats);
        assertEquals(0, stats.failed(), "Generation should be declined, not fail: " + stats);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unknownDiscriminatorValueBecomesTheUnknownVariant(boolean runtimeCodegen) {
        var codec = codec(runtimeCodegen, false);
        for (var json : new String[] {
                "{\"sport\":\"hockey\",\"name\":\"Pucks\"}",
                "{\"name\":\"Pucks\",\"sport\":\"hockey\"}"}) {
            var unknown = assertInstanceOf(SportsTeam.$Unknown.class, decode(codec, json, SportsTeam.builder()), json);
            assertEquals("hockey", unknown.memberName());
        }
    }
}
