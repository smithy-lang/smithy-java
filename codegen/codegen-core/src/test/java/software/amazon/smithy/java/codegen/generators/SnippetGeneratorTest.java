/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.utils.TestJavaCodegenPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;

public class SnippetGeneratorTest {

    private static CodeGenerationContext context;

    @BeforeAll
    public static void setUp() {
        var model = Model.assembler()
                .addImport(Objects.requireNonNull(SnippetGeneratorTest.class.getResource("snippet-test.smithy")))
                .assemble()
                .unwrap();

        var plugin = new TestJavaCodegenPlugin();
        var pluginContext = PluginContext.builder()
                .fileManifest(new MockManifest())
                .settings(
                        ObjectNode.builder()
                                .withMember("service", "smithy.java.codegen#TestService")
                                .withMember("namespace", "software.amazon.smithy.java.codegen.test")
                                .build())
                .model(model)
                .build();
        plugin.execute(pluginContext);

        context = plugin.capturedContext;
    }

    public static Stream<Arguments> cases() {
        return Stream.of(
                arguments("smithy.api#Blob", Node.from("foo"), "DataStream.ofString(\"foo\")"),
                arguments("smithy.api#Blob",
                        Node.from("Zm9v"),
                        "DataStream.ofBytes(Base64.getDecoder().decode(\"Zm9v\"))"),
                arguments("smithy.api#Blob", Node.nullNode(), "null"),

                arguments("smithy.api#Boolean", Node.from(true), "true"),
                arguments("smithy.api#Boolean", Node.from(false), "false"),
                arguments("smithy.api#Boolean", Node.nullNode(), "null"),

                arguments("smithy.api#Byte", Node.from(1), "1"),
                arguments("smithy.api#Byte", Node.nullNode(), "null"),

                arguments("smithy.api#Short", Node.from(1), "1"),
                arguments("smithy.api#Short", Node.nullNode(), "null"),

                arguments("smithy.api#Integer", Node.from(1), "1"),
                arguments("smithy.api#Integer", Node.nullNode(), "null"),

                arguments("smithy.java.codegen#IntEnum", Node.from(1), "IntEnum.BAR"),
                arguments("smithy.java.codegen#IntEnum", Node.from(8), "IntEnum.unknown(8)"),
                arguments("smithy.java.codegen#IntEnum", Node.nullNode(), "null"),

                arguments("smithy.api#Long", Node.from(1), "1"),
                arguments("smithy.api#Long", Node.nullNode(), "null"),

                arguments("smithy.api#BigInteger", Node.from(1), "new BigInteger(\"1\")"),
                arguments("smithy.api#BigInteger", Node.from("1"), "new BigInteger(\"1\")"),
                arguments("smithy.api#BigInteger", Node.nullNode(), "null"),

                arguments("smithy.api#Float", Node.from(1.1), "1.1"),
                arguments("smithy.api#Float", Node.nullNode(), "null"),

                arguments("smithy.api#Double", Node.from(1.1), "1.1"),
                arguments("smithy.api#Double", Node.nullNode(), "null"),

                arguments("smithy.api#BigDecimal", Node.from(1.1), "new BigDecimal(\"1.1\")"),
                arguments("smithy.api#BigDecimal", Node.from("1.1"), "new BigDecimal(\"1.1\")"),
                arguments("smithy.api#BigDecimal", Node.nullNode(), "null"),

                arguments("smithy.api#String", Node.from("foo"), "\"foo\""),
                arguments("smithy.api#String", Node.nullNode(), "null"),

                arguments("smithy.java.codegen#StringEnum", Node.from("FOO"), "StringEnum.FOO"),
                arguments("smithy.java.codegen#StringEnum", Node.from("unknown"), "StringEnum.unknown(\"unknown\")"),
                arguments("smithy.java.codegen#StringEnum", Node.nullNode(), "null"),

                arguments("smithy.api#Timestamp", Node.from(1553372880), "Instant.parse(\"2019-03-23T20:28:00Z\")"),
                arguments("smithy.api#Timestamp",
                        Node.from(1553372880.1),
                        "Instant.parse(\"2019-03-23T20:28:00.100Z\")"),
                arguments("smithy.api#Timestamp",
                        Node.from("2019-03-23T20:28:00Z"),
                        "Instant.parse(\"2019-03-23T20:28:00Z\")"),
                arguments("smithy.api#Timestamp",
                        Node.from("2019-03-23T20:28:00.100Z"),
                        "Instant.parse(\"2019-03-23T20:28:00.100Z\")"),
                arguments("smithy.api#Timestamp",
                        Node.from("Sat, 23 Mar 2019 20:28:00 GMT"),
                        "Instant.parse(\"2019-03-23T20:28:00Z\")"),
                arguments("smithy.api#Timestamp", Node.nullNode(), "null"),

                arguments("smithy.java.codegen#StringList", Node.arrayNode(), "Collections.emptyList()"),
                arguments("smithy.java.codegen#StringList", Node.fromStrings("foo"), "List.of(\"foo\")"),
                arguments("smithy.java.codegen#StringList",
                        Node.fromStrings("foo", "bar", "baz"),
                        """
                                List.of(
                                    "foo",
                                    "bar",
                                    "baz"
                                )"""),
                arguments("smithy.java.codegen#StringList", Node.nullNode(), "null"),

                arguments("smithy.java.codegen#StringMap", ObjectNode.builder().build(), "Collections.emptyMap()"),
                arguments(
                        "smithy.java.codegen#StringMap",
                        ObjectNode.builder()
                                .withMember("foo", "bar")
                                .build(),
                        "Map.of(\"foo\", \"bar\")"),
                arguments(
                        "smithy.java.codegen#StringMap",
                        ObjectNode.builder()
                                .withMember("foo", "bar")
                                .withMember("spam", "eggs")
                                .build(),
                        """
                                Map.of(
                                    "foo", "bar",
                                    "spam", "eggs"
                                )"""),
                arguments(
                        "smithy.java.codegen#StringMap",
                        ObjectNode.builder()
                                .withMember("one", "one")
                                .withMember("two", "two")
                                .withMember("three", "three")
                                .withMember("four", "four")
                                .withMember("five", "five")
                                .withMember("six", "six")
                                .withMember("seven", "seven")
                                .withMember("eight", "eight")
                                .withMember("nine", "nine")
                                .withMember("ten", "ten")
                                .withMember("eleven", "eleven")
                                .build(),
                        """
                                Map.ofEntries(
                                    Map.entry("one", "one"),
                                    Map.entry("two", "two"),
                                    Map.entry("three", "three"),
                                    Map.entry("four", "four"),
                                    Map.entry("five", "five"),
                                    Map.entry("six", "six"),
                                    Map.entry("seven", "seven"),
                                    Map.entry("eight", "eight"),
                                    Map.entry("nine", "nine"),
                                    Map.entry("ten", "ten"),
                                    Map.entry("eleven", "eleven")
                                )"""),
                arguments(
                        "smithy.java.codegen#EnumKeyMap",
                        ObjectNode.builder()
                                .withMember("FOO", "bar")
                                .build(),
                        "Map.of(StringEnum.FOO, \"bar\")"),
                arguments("smithy.java.codegen#StringMap", Node.nullNode(), "null"),

                arguments(
                        "smithy.java.codegen#TestOperationInput",
                        ObjectNode.builder()
                                .withMember("foo", "bar")
                                .build(),
                        """
                                TestOperationInput.builder()
                                    .foo("bar")
                                    .build()"""),
                arguments("smithy.java.codegen#TestOperationInput", Node.nullNode(), "null"),

                arguments(
                        "smithy.java.codegen#BasicUnion",
                        ObjectNode.builder()
                                .withMember("foo", "bar")
                                .build(),
                        "BasicUnion.FooMember(\"bar\")"),
                arguments(
                        "smithy.java.codegen#BasicUnion",
                        ObjectNode.builder()
                                .withMember("spam", "eggs")
                                .build(),
                        "BasicUnion.$Unknown(\"spam\")"),
                arguments("smithy.java.codegen#BasicUnion", Node.nullNode(), "null"),

                arguments("smithy.api#Document", Node.from("foo"), "Document.of(\"foo\")"),
                arguments("smithy.api#Document", Node.from(1), "Document.of(1)"),
                arguments("smithy.api#Document", Node.from(1.1), "Document.of(1.1)"),
                arguments("smithy.api#Document", Node.from(true), "Document.of(true)"),
                arguments("smithy.api#Document", Node.from(false), "Document.of(false)"),
                arguments("smithy.api#Document", Node.arrayNode(), "Document.of(Collections.emptyList())"),
                arguments("smithy.api#Document", Node.fromStrings("foo"), "Document.of(List.of(Document.of(\"foo\")))"),
                arguments("smithy.api#Document",
                        Node.fromStrings("foo", "bar", "baz"),
                        """
                                Document.of(List.of(
                                    Document.of("foo"),
                                    Document.of("bar"),
                                    Document.of("baz")
                                ))"""),
                arguments("smithy.api#Document",
                        Node.arrayNode(Node.from("foo"), Node.from(1), Node.from("bar")),
                        """
                                Document.of(List.of(
                                    Document.of("foo"),
                                    Document.of(1),
                                    Document.of("bar")
                                ))"""),
                arguments("smithy.api#Document", ObjectNode.builder().build(), "Document.of(Collections.emptyMap())"),
                arguments(
                        "smithy.api#Document",
                        ObjectNode.builder()
                                .withMember("foo", "bar")
                                .build(),
                        "Document.of(Map.of(\"foo\", Document.of(\"bar\")))"),
                arguments(
                        "smithy.api#Document",
                        ObjectNode.builder()
                                .withMember("foo", "bar")
                                .withMember("spam", "eggs")
                                .build(),
                        """
                                Document.of(Map.of(
                                    "foo", Document.of("bar"),
                                    "spam", Document.of("eggs")
                                ))"""),
                arguments(
                        "smithy.api#Document",
                        ObjectNode.builder()
                                .withMember("one", "one")
                                .withMember("two", "two")
                                .withMember("three", "three")
                                .withMember("four", "four")
                                .withMember("five", "five")
                                .withMember("six", "six")
                                .withMember("seven", "seven")
                                .withMember("eight", "eight")
                                .withMember("nine", "nine")
                                .withMember("ten", "ten")
                                .withMember("eleven", "eleven")
                                .build(),
                        """
                                Document.of(Map.ofEntries(
                                    Map.entry("one", Document.of("one")),
                                    Map.entry("two", Document.of("two")),
                                    Map.entry("three", Document.of("three")),
                                    Map.entry("four", Document.of("four")),
                                    Map.entry("five", Document.of("five")),
                                    Map.entry("six", Document.of("six")),
                                    Map.entry("seven", Document.of("seven")),
                                    Map.entry("eight", Document.of("eight")),
                                    Map.entry("nine", Document.of("nine")),
                                    Map.entry("ten", Document.of("ten")),
                                    Map.entry("eleven", Document.of("eleven"))
                                ))"""),
                arguments(
                        "smithy.api#Document",
                        ObjectNode.builder()
                                .withMember("foo", "bar")
                                .withMember("spam", 1)
                                .build(),
                        """
                                Document.of(Map.of(
                                    "foo", Document.of("bar"),
                                    "spam", Document.of(1)
                                ))"""),
                arguments("smithy.api#Document", Node.nullNode(), "null"));
    }

    @ParameterizedTest
    @MethodSource("cases")
    public void testGenerateShapeInitializer(String shapeId, Node value, String expected) {
        var shape = context.model().expectShape(ShapeId.from(shapeId));
        var actual = SnippetGenerator.generateShapeInitializer(context, shape, value);
        assertThat(actual, equalTo(expected));
    }
}
