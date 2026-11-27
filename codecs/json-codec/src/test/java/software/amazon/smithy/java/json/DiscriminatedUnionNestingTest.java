/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.DiscriminatedTrait;

/**
 * Verifies discriminated unions that are nested inside the structures their variants target, and discriminator
 * names that need JSON escaping, on both JSON backends.
 *
 * <p>Values are read into and written from plain maps by a small schema-driven reader and writer, so the tests do
 * not depend on hand-rolled builders for each shape.
 */
public class DiscriminatedUnionNestingTest extends ProviderTestBase {

    // Team := @discriminated("sport") union { soccer: SoccerTeam }
    // SoccerTeam := structure { name: String, mascot: Mascot }
    // Mascot := @discriminated("kind") union { animal: AnimalMascot }
    // AnimalMascot := structure { species: String, nickname: String }
    private static final Schema TEAM = teamSchema();

    private static Schema teamSchema() {
        var animal = Schema.structureBuilder(ShapeId.from("smithy.example#AnimalMascot"));
        animal.putMember("species", Schema.createString(ShapeId.from("smithy.example#AnimalMascot$species")));
        animal.putMember("nickname", Schema.createString(ShapeId.from("smithy.example#AnimalMascot$nickname")));

        var mascot = Schema.unionBuilder(ShapeId.from("smithy.example#Mascot"), new DiscriminatedTrait("kind"));
        mascot.putMember("animal", animal);

        var soccer = Schema.structureBuilder(ShapeId.from("smithy.example#SoccerTeam"));
        soccer.putMember("name", Schema.createString(ShapeId.from("smithy.example#SoccerTeam$name")));
        soccer.putMember("mascot", mascot);

        var team = Schema.unionBuilder(ShapeId.from("smithy.example#Team"), new DiscriminatedTrait("sport"));
        team.putMember("soccer", soccer);
        return team.build();
    }

    private static final String CANONICAL =
            "{\"sport\":\"soccer\",\"name\":\"Rovers\","
                    + "\"mascot\":{\"kind\":\"animal\",\"species\":\"lion\",\"nickname\":\"Leo\"}}";

    private static final Map<String, Object> EXPECTED = Map.of(
            "soccer",
            Map.of("name", "Rovers", "mascot", Map.of("animal", Map.of("species", "lion", "nickname", "Leo"))));

    static List<Arguments> orderings() {
        var inputs = List.of(
                CANONICAL,
                // Outer discriminator first, inner last.
                "{\"sport\":\"soccer\",\"name\":\"Rovers\","
                        + "\"mascot\":{\"species\":\"lion\",\"nickname\":\"Leo\",\"kind\":\"animal\"}}",
                // Outer discriminator last, inner first.
                "{\"name\":\"Rovers\",\"mascot\":{\"kind\":\"animal\",\"species\":\"lion\",\"nickname\":\"Leo\"},"
                        + "\"sport\":\"soccer\"}",
                // Both last.
                "{\"name\":\"Rovers\",\"mascot\":{\"species\":\"lion\",\"nickname\":\"Leo\",\"kind\":\"animal\"},"
                        + "\"sport\":\"soccer\"}",
                // Discriminators in the middle.
                "{\"name\":\"Rovers\",\"sport\":\"soccer\","
                        + "\"mascot\":{\"species\":\"lion\",\"kind\":\"animal\",\"nickname\":\"Leo\"}}");
        return providerInstances().stream()
                .flatMap(provider -> inputs.stream().map(json -> Arguments.of(provider, json)))
                .toList();
    }

    @ParameterizedTest
    @MethodSource("orderings")
    public void decodesNestedUnionsInAnyFieldOrder(JsonSerdeProvider provider, String json) {
        assertThat(read(provider, TEAM, json), equalTo(EXPECTED));
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void nestedUnionsEncodeWithEachDiscriminatorFirst(JsonSerdeProvider provider) {
        assertThat(write(provider, TEAM, EXPECTED), equalTo(CANONICAL));
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void fieldAfterNestedUnionIsStillRead(JsonSerdeProvider provider) {
        // The nested union's closing brace must not be taken for the variant's own closing brace.
        var json = "{\"sport\":\"soccer\",\"mascot\":{\"kind\":\"animal\",\"species\":\"lion\"},\"name\":\"Rovers\"}";
        assertThat(
                read(provider, TEAM, json),
                equalTo(Map.of("soccer",
                        Map.of("name", "Rovers", "mascot", Map.of("animal", Map.of("species", "lion"))))));
    }

    // ----- Discriminator names that need escaping or are unusual -----

    private static final List<String> NAMES = List.of(
            "type",
            "@type",
            "$type",
            "@odata.type",
            "a b",
            "a-b",
            "a/b",
            "a\"b",
            "a\\b",
            "t\u00e9st",
            "\u65e5\u672c");

    static List<Arguments> names() {
        return providerInstances().stream()
                .flatMap(provider -> NAMES.stream().map(name -> Arguments.of(provider, name)))
                .toList();
    }

    private static Schema circleUnion(String discriminator) {
        return circleUnion(new DiscriminatedTrait(discriminator));
    }

    private static Schema circleUnion(DiscriminatedTrait trait) {
        var circle = Schema.structureBuilder(ShapeId.from("smithy.example#Circle"));
        circle.putMember("radius", Schema.createString(ShapeId.from("smithy.example#Circle$radius")));
        var shape = Schema.unionBuilder(ShapeId.from("smithy.example#Shape"), trait);
        shape.putMember("circle", circle);
        return shape.build();
    }

    private static String quote(String name) {
        return "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Escapes every character as a unicode escape, which forces decoders off any raw-byte fast path. */
    private static String quoteWithUnicodeEscapes(String name) {
        var sb = new StringBuilder("\"");
        for (char c : name.toCharArray()) {
            sb.append(String.format("\\u%04x", (int) c));
        }
        return sb.append('"').toString();
    }

    @ParameterizedTest
    @MethodSource("names")
    public void encodesAnyDiscriminatorName(JsonSerdeProvider provider, String name) {
        var written = write(provider, circleUnion(name), Map.of("circle", Map.of("radius", "5")));
        assertThat(written, equalTo("{" + quote(name) + ":\"circle\",\"radius\":\"5\"}"));
    }

    @ParameterizedTest
    @MethodSource("names")
    public void decodesAnyDiscriminatorNameWhenFirst(JsonSerdeProvider provider, String name) {
        var json = "{" + quote(name) + ":\"circle\",\"radius\":\"5\"}";
        assertThat(read(provider, circleUnion(name), json), equalTo(Map.of("circle", Map.of("radius", "5"))));
    }

    @ParameterizedTest
    @MethodSource("names")
    public void decodesAnyDiscriminatorNameWhenLast(JsonSerdeProvider provider, String name) {
        var json = "{\"radius\":\"5\"," + quote(name) + ":\"circle\"}";
        assertThat(read(provider, circleUnion(name), json), equalTo(Map.of("circle", Map.of("radius", "5"))));
    }

    @ParameterizedTest
    @MethodSource("names")
    public void decodesDiscriminatorNameWrittenWithUnicodeEscapes(JsonSerdeProvider provider, String name) {
        var json = "{" + quoteWithUnicodeEscapes(name) + ":\"circle\",\"radius\":\"5\"}";
        assertThat(read(provider, circleUnion(name), json), equalTo(Map.of("circle", Map.of("radius", "5"))));
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void decodesDiscriminatorValueWrittenWithUnicodeEscapes(JsonSerdeProvider provider) {
        // "circle" with every character escaped.
        var json = "{\"type\":" + quoteWithUnicodeEscapes("circle") + ",\"radius\":\"5\"}";
        assertThat(read(provider, circleUnion("type"), json), equalTo(Map.of("circle", Map.of("radius", "5"))));
    }

    // ----- Wire behavior described by the design -----

    @ParameterizedTest
    @MethodSource("providers")
    public void missingDiscriminatorIsAnError(JsonSerdeProvider provider) {
        assertThrows(SerializationException.class,
                () -> read(provider, circleUnion("type"), "{\"radius\":\"5\"}"));
        // An empty object is not a special case.
        assertThrows(SerializationException.class, () -> read(provider, circleUnion("type"), "{}"));
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void emptyNestedUnionIsAnErrorWhateverTheOuterDiscriminatorPosition(JsonSerdeProvider provider) {
        // The outer discriminator is last, so the inner union is read after the outer union rewinds (native) or
        // from a captured document (Jackson).
        assertThrows(SerializationException.class,
                () -> read(provider, TEAM, "{\"name\":\"Rovers\",\"mascot\":{},\"sport\":\"soccer\"}"));
        // And with the outer discriminator first, the inner union is read from the stream.
        assertThrows(SerializationException.class,
                () -> read(provider, TEAM, "{\"sport\":\"soccer\",\"name\":\"Rovers\",\"mascot\":{}}"));
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void nonStringDiscriminatorIsAnError(JsonSerdeProvider provider) {
        assertThrows(SerializationException.class,
                () -> read(provider, circleUnion("type"), "{\"type\":5,\"radius\":\"5\"}"));
        assertThrows(SerializationException.class,
                () -> read(provider, circleUnion("type"), "{\"radius\":\"5\",\"type\":5}"));
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void nullDiscriminatorIsAnError(JsonSerdeProvider provider) {
        assertThrows(SerializationException.class,
                () -> read(provider, circleUnion("type"), "{\"type\":null,\"radius\":\"5\"}"));
        assertThrows(SerializationException.class,
                () -> read(provider, circleUnion("type"), "{\"radius\":\"5\",\"type\":null}"));
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void unknownDiscriminatorValueIsReportedAsUnknownMember(JsonSerdeProvider provider) {
        for (var json : List.of(
                "{\"type\":\"triangle\",\"base\":\"2\"}",
                "{\"base\":\"2\",\"type\":\"triangle\"}")) {
            List<String> unknown = new ArrayList<>();
            try (var codec = codec(provider)) {
                var de = codec.createDeserializer(json.getBytes(StandardCharsets.UTF_8));
                de.readStruct(circleUnion("type"), null, new ShapeDeserializer.StructMemberConsumer<Object>() {
                    @Override
                    public void accept(Object state, Schema member, ShapeDeserializer memberDe) {
                        throw new AssertionError("No member should be resolved for an unknown value");
                    }

                    @Override
                    public void unknownMember(Object state, String memberName) {
                        unknown.add(memberName);
                    }
                });
                de.close();
            }
            assertThat(json, unknown, equalTo(List.of("triangle")));
        }
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void strayTypeFieldIsIgnoredWhenDiscriminatorHasAnotherName(JsonSerdeProvider provider) {
        // "__type" is only the discriminator when that is the configured name; otherwise it is an unknown field.
        for (var json : List.of(
                "{\"type\":\"circle\",\"__type\":\"other\",\"radius\":\"5\"}",
                "{\"__type\":\"other\",\"radius\":\"5\",\"type\":\"circle\"}")) {
            assertThat(json,
                    read(provider, circleUnion("type"), json),
                    equalTo(Map.of("circle", Map.of("radius", "5"))));
        }
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void defaultDiscriminatorNameIsTheDiscriminator(JsonSerdeProvider provider) {
        var union = circleUnion(new DiscriminatedTrait());
        assertThat(
                read(provider, union, "{\"__type\":\"circle\",\"radius\":\"5\"}"),
                equalTo(Map.of("circle", Map.of("radius", "5"))));
        assertThat(
                write(provider, union, Map.of("circle", Map.of("radius", "5"))),
                equalTo("{\"__type\":\"circle\",\"radius\":\"5\"}"));
    }

    // ----- Schema-driven reading and writing of plain maps -----

    private static Object readValue(ShapeDeserializer de, Schema schema) {
        return switch (schema.type()) {
            case STRING -> de.readString(schema);
            case STRUCTURE, UNION -> {
                Map<String, Object> result = new LinkedHashMap<>();
                de.readStruct(schema, result, (state, member, memberDe) -> {
                    state.put(member.memberName(), readValue(memberDe, member));
                });
                yield result;
            }
            default -> throw new IllegalArgumentException("Unsupported type " + schema.type());
        };
    }

    private static Object read(JsonSerdeProvider provider, Schema schema, String json) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer(json.getBytes(StandardCharsets.UTF_8));
            var result = readValue(de, schema);
            de.close();
            return result;
        }
    }

    private static String write(JsonSerdeProvider provider, Schema schema, Map<String, Object> value) {
        try (
                var codec = codec(provider);
                var output = new ByteArrayOutputStream();
                var serializer = codec.createSerializer(output)) {
            new MapStruct(schema, value).serialize(serializer);
            serializer.flush();
            return output.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Serializes a map according to a schema, in schema member order. */
    private record MapStruct(Schema schema, Map<String, Object> values) implements SerializableStruct {
        @Override
        @SuppressWarnings("unchecked")
        public void serializeMembers(ShapeSerializer serializer) {
            for (Schema member : schema.members()) {
                Object value = values.get(member.memberName());
                if (value == null) {
                    continue;
                }
                switch (member.type()) {
                    case STRING -> serializer.writeString(member, (String) value);
                    case STRUCTURE, UNION -> serializer.writeStruct(
                            member,
                            new MapStruct(member.memberTarget(), (Map<String, Object>) value));
                    default -> throw new IllegalArgumentException("Unsupported type " + member.type());
                }
            }
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }
    }
}
