/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.config.AwsProfileFileParser.RawProperty;
import software.amazon.smithy.java.aws.config.AwsProfileFileParser.RawSection;

class AwsProfileFileParserTest {

    @Test
    void parsesSimpleSectionsAndProperties() {
        String content = """
                [default]
                aws_access_key_id = AKIA_DEFAULT
                aws_secret_access_key = default_secret

                [profile dev]
                region = us-west-2
                """;

        List<RawSection> sections = AwsProfileFileParser.parse(content);
        assertEquals(2, sections.size());

        RawSection def = sections.get(0);
        assertEquals("default", def.rawHeader);
        assertEquals(List.of("aws_access_key_id", "aws_secret_access_key"), List.copyOf(def.properties.keySet()));
        assertEquals("AKIA_DEFAULT", def.properties.get("aws_access_key_id").value);

        RawSection dev = sections.get(1);
        assertEquals("profile dev", dev.rawHeader);
        assertEquals("us-west-2", dev.properties.get("region").value);
    }

    @Test
    void ignoresCommentsAndBlankLines() {
        String content = """
                # top comment
                ; another one

                [default]
                # inside a section
                ; also a comment
                region = us-east-1
                """;

        List<RawSection> sections = AwsProfileFileParser.parse(content);
        assertEquals(1, sections.size());
        assertEquals(Map.of("region", "us-east-1"),
                mapValues(sections.get(0).properties));
    }

    @Test
    void acceptsSectionHeaderWithTrailingComment() {
        assertEquals("default", AwsProfileFileParser.parse("[default]; hi\n").get(0).rawHeader);
        assertEquals("profile foo", AwsProfileFileParser.parse("[profile foo] # hello\n").get(0).rawHeader);
    }

    @Test
    void duplicateKeysLastWriteWinsAndPreserveOrder() {
        String content = """
                [default]
                region = us-east-1
                region = us-west-2
                """;
        RawSection s = AwsProfileFileParser.parse(content).get(0);
        assertEquals("us-west-2", s.properties.get("region").value);
    }

    @Test
    void stripsInlineSemicolonCommentFromValuesOnly() {
        String content = """
                [default]
                a = hello ; comment
                b = hello;not-a-comment
                c = with#hash
                d = val\twith\ttabs ; cmt
                """;
        RawSection s = AwsProfileFileParser.parse(content).get(0);
        assertEquals("hello", s.properties.get("a").value);
        assertEquals("hello;not-a-comment", s.properties.get("b").value);
        assertEquals("with#hash", s.properties.get("c").value);
        assertEquals("val\twith\ttabs", s.properties.get("d").value);
    }

    @Test
    void propertyContinuationAppendsWithNewline() {
        String content = """
                [default]
                region = us-
                 west-2
                """;
        RawSection s = AwsProfileFileParser.parse(content).get(0);
        assertEquals("us-\nwest-2", s.properties.get("region").value);
    }

    @Test
    void propertyContinuationDoesNotStripInlineComments() {
        String content = """
                [default]
                region = us-
                 west-2 ; comment becomes part of the value
                """;
        RawSection s = AwsProfileFileParser.parse(content).get(0);
        assertEquals("us-\nwest-2 ; comment becomes part of the value",
                s.properties.get("region").value);
    }

    @Test
    void subPropertyUnderEmptyParent() {
        String content = """
                [default]
                s3 =
                  max_concurrent_requests = 30
                  max_retries = 10
                region = us-west-2
                """;
        RawSection s = AwsProfileFileParser.parse(content).get(0);
        assertEquals("", s.properties.get("s3").value);
        assertEquals(Map.of("max_concurrent_requests", "30", "max_retries", "10"),
                s.properties.get("s3").subProperties);
        assertEquals("us-west-2", s.properties.get("region").value);
    }

    @Test
    void multipleSubPropertyBlocksInSameProfile() {
        String content = """
                [default]
                s3 =
                  max_concurrent_requests = 30
                dynamodb =
                  endpoint_url = https://localhost:1234
                """;
        RawSection s = AwsProfileFileParser.parse(content).get(0);
        assertEquals(Map.of("max_concurrent_requests", "30"), s.properties.get("s3").subProperties);
        assertEquals(Map.of("endpoint_url", "https://localhost:1234"),
                s.properties.get("dynamodb").subProperties);
    }

    @Test
    void windowsLineEndingsAreHandled() {
        String content = "[default]\r\nregion = us-east-1\r\n";
        RawSection s = AwsProfileFileParser.parse(content).get(0);
        assertEquals("us-east-1", s.properties.get("region").value);
    }

    // --- Fail-fast cases ------------------------------------------------------------------------

    @Test
    void sectionWithoutClosingBracketFails() {
        ConfigFileParseException e = assertThrows(
                ConfigFileParseException.class,
                () -> AwsProfileFileParser.parse("[default\nregion = us-east-1\n"));
        assertEquals(1, e.lineNumber());
    }

    @Test
    void propertyBeforeAnySectionFails() {
        ConfigFileParseException e = assertThrows(
                ConfigFileParseException.class,
                () -> AwsProfileFileParser.parse("region = us-east-1\n[default]\n"));
        assertEquals(1, e.lineNumber());
    }

    @Test
    void propertyWithoutEqualsFails() {
        ConfigFileParseException e = assertThrows(
                ConfigFileParseException.class,
                () -> AwsProfileFileParser.parse("[default]\nregion\n"));
        assertEquals(2, e.lineNumber());
    }

    @Test
    void propertyWithoutKeyBeforeEqualsFails() {
        ConfigFileParseException e = assertThrows(
                ConfigFileParseException.class,
                () -> AwsProfileFileParser.parse("[default]\n= us-east-1\n"));
        assertEquals(2, e.lineNumber());
    }

    @Test
    void continuationBeforeAnyPropertyFails() {
        ConfigFileParseException e = assertThrows(
                ConfigFileParseException.class,
                () -> AwsProfileFileParser.parse("[default]\n  continued\n"));
        assertEquals(2, e.lineNumber());
    }

    @Test
    void continuationOfEmptyValueWithoutEqualsFails() {
        ConfigFileParseException e = assertThrows(
                ConfigFileParseException.class,
                () -> AwsProfileFileParser.parse("[default]\ns3 =\n  notanassignment\n"));
        assertEquals(3, e.lineNumber());
    }

    @Test
    void continuationOfEmptyValueWithoutKeyFails() {
        ConfigFileParseException e = assertThrows(
                ConfigFileParseException.class,
                () -> AwsProfileFileParser.parse("[default]\ns3 =\n  = 30\n"));
        assertEquals(3, e.lineNumber());
    }

    @Test
    void textAfterSectionHeaderFails() {
        // e.g. "[default] extra" — anything after ']' other than whitespace+comment is invalid.
        ConfigFileParseException e = assertThrows(
                ConfigFileParseException.class,
                () -> AwsProfileFileParser.parse("[default] extra\n"));
        assertEquals(1, e.lineNumber());
    }

    @Test
    void whitespaceOnlyLineWithinSubPropertiesIsBlank() {
        // Blank lines between sub-properties are permitted per the SEP.
        String content = """
                [default]
                s3 =
                  max_concurrent_requests = 30

                  max_retries = 10
                """;
        RawSection s = AwsProfileFileParser.parse(content).get(0);
        Map<String, String> subs = s.properties.get("s3").subProperties;
        assertTrue(subs.containsKey("max_concurrent_requests"));
        assertTrue(subs.containsKey("max_retries"));
    }

    private static Map<String, String> mapValues(Map<String, RawProperty> props) {
        Map<String, String> out = new LinkedHashMap<>();
        for (var e : props.entrySet()) {
            out.put(e.getKey(), e.getValue().value);
        }
        return out;
    }
}
