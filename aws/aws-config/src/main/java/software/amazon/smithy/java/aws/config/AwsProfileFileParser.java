/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parser for the INI-style AWS shared configuration / credentials file format.
 *
 * <p>This parser produces a flat list of {@link RawSection raw sections} preserving the order of the
 * original file. It is responsible for the grammar (which lines are valid and how values are constructed) but not
 * for file-type specific validation (whether a section is allowed in this file, identifier validation, handling of
 * {@code [default]} vs {@code [profile default]}, etc.); that belongs in {@link ProfileStandardizer}.
 *
 * <p>Grammar, matching the AWS SDK shared-configuration SEP:
 * <ul>
 *   <li><b>Blank line:</b> only whitespace. Ignored.</li>
 *   <li><b>Comment line:</b> first character is {@code #} or {@code ;}. Ignored. (Leading whitespace
 *       before the marker is NOT a comment line; such a line is classified as continuation or as an
 *       unknown line depending on parser state.)</li>
 *   <li><b>Section header:</b> {@code [ name ]} optionally followed by a comment
 *       ({@code ; ...} or {@code # ...}). A missing closing {@code ]} is a parse error.</li>
 *   <li><b>Property:</b> {@code key = value} where {@code key} is at column 0 (no leading
 *       whitespace). The value is trimmed. If the value contains an unescaped {@code ;} preceded by
 *       whitespace, that {@code ;} and everything after it is a comment. Missing {@code =} or empty
 *       key is a parse error.</li>
 *   <li><b>Property continuation:</b> a non-blank, non-comment line that starts with whitespace.
 *       Appended to the previous property's value with a leading newline. If the previous property
 *       had an empty value, the continuation is instead parsed as a <i>sub-property</i>
 *       ({@code key = value}), and subsequent indented lines under the same parent are also
 *       sub-properties.</li>
 * </ul>
 *
 * <p>This parser preserves keys exactly as written; lower-casing is performed by
 * {@link ProfileStandardizer}.
 */
final class AwsProfileFileParser {

    private AwsProfileFileParser() {}

    /** Matches just the bracketed portion of a section header, with groups for content and trailer. */
    private static final Pattern SECTION_PATTERN = Pattern.compile("^\\[(?<content>[^]]*)](?<trailer>.*)$");

    /**
     * Parse an AWS-style INI document.
     *
     * @param content the full text of the file.
     * @return an ordered list of raw sections.
     * @throws ConfigFileParseException on any critical syntax error.
     */
    static List<RawSection> parse(String content) {
        try (Reader reader = new StringReader(content)) {
            return parse(reader);
        } catch (IOException e) {
            // StringReader.close() doesn't throw; this can't happen in practice.
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Parse an AWS-style INI document from a reader. The caller is responsible for closing the reader.
     */
    static List<RawSection> parse(Reader reader) throws IOException {
        BufferedReader br = (reader instanceof BufferedReader) ? (BufferedReader) reader : new BufferedReader(reader);
        List<RawSection> sections = new ArrayList<>();
        RawSection currentSection = null;
        RawProperty currentProperty = null;
        boolean inSubProperties = false;

        String line;
        int lineNumber = 0;
        while ((line = br.readLine()) != null) {
            lineNumber++;

            // Strictly blank line.
            if (line.isBlank()) {
                continue;
            }

            // Strict comment line: first char is '#' or ';' (no leading whitespace).
            char firstChar = line.charAt(0);
            if (firstChar == '#' || firstChar == ';') {
                continue;
            }

            if (firstChar == '[') {
                // Section header: ends the previous section's sub-property / continuation state.
                RawSection section = parseSectionHeader(line, lineNumber);
                sections.add(section);
                currentSection = section;
                currentProperty = null;
                inSubProperties = false;
                continue;
            }

            boolean startsWithWhitespace = firstChar == ' ' || firstChar == '\t';
            if (startsWithWhitespace) {
                if (currentSection == null) {
                    throw new ConfigFileParseException(lineNumber, "Expected a section definition");
                }
                if (currentProperty == null) {
                    throw new ConfigFileParseException(lineNumber,
                            "Expected a property definition, found continuation");
                }
                if (inSubProperties || currentProperty.value.isEmpty()) {
                    // Sub-property line: key = value.
                    parseSubProperty(currentProperty, line, lineNumber);
                    inSubProperties = true;
                } else {
                    // Plain continuation: append trimmed content with a leading newline.
                    // Inline comments are NOT stripped from continuations per the SEP.
                    currentProperty.value = currentProperty.value + "\n" + line.strip();
                }
                continue;
            }

            // Otherwise: must be a property definition at column 0.
            if (currentSection == null) {
                throw new ConfigFileParseException(lineNumber, "Expected a section definition");
            }
            RawProperty prop = parseProperty(line, lineNumber);
            // Duplicates within the same file and section: last write wins. Merge preserves
            // insertion order of the first occurrence.
            currentSection.properties.put(prop.key, prop);
            currentProperty = prop;
            inSubProperties = false;
        }

        return sections;
    }

    private static RawSection parseSectionHeader(String line, int lineNumber) {
        var matcher = SECTION_PATTERN.matcher(line);
        if (!matcher.matches()) {
            throw new ConfigFileParseException(lineNumber, "Section definition must end with ']'");
        }

        String content = matcher.group("content").strip();
        String trailer = matcher.group("trailer");

        // Trailer after ']' must be whitespace and/or a comment (# or ; based).
        if (!trailer.isEmpty()) {
            String t = trailer.stripLeading();
            if (!t.isEmpty() && t.charAt(0) != '#' && t.charAt(0) != ';') {
                throw new ConfigFileParseException(lineNumber, "unexpected characters after ']' in section header");
            }
        }
        return new RawSection(lineNumber, content);
    }

    private static RawProperty parseProperty(String line, int lineNumber) {
        int eq = line.indexOf('=');
        if (eq < 0) {
            throw new ConfigFileParseException(lineNumber, "Expected an '=' sign defining a property");
        }

        String key = line.substring(0, eq).strip();
        if (key.isEmpty()) {
            throw new ConfigFileParseException(lineNumber, "Property did not have a name");
        }

        String rawValue = line.substring(eq + 1);
        String value = stripInlineComment(rawValue).strip();
        return new RawProperty(lineNumber, key, value);
    }

    private static void parseSubProperty(RawProperty parent, String line, int lineNumber) {
        int eq = line.indexOf('=');
        if (eq < 0) {
            throw new ConfigFileParseException(lineNumber, "Expected an '=' sign defining a property in sub-property");
        }

        String key = line.substring(0, eq).strip();
        if (key.isEmpty()) {
            throw new ConfigFileParseException(lineNumber, "Property did not have a name in sub-property");
        }

        // Per the SEP, comments are NOT stripped from sub-property values.
        String value = line.substring(eq + 1).strip();
        parent.subProperties.put(key, value);
    }

    /**
     * Strip an inline comment from a property value. Both {@code ;} and {@code #} count as inline
     * comment markers when preceded by whitespace (space or tab).
     */
    private static String stripInlineComment(String value) {
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ';' || c == '#') {
                char prev = value.charAt(i - 1);
                if (prev == ' ' || prev == '\t') {
                    return value.substring(0, i);
                }
            }
        }

        return value;
    }

    /** An ordered, intermediate representation of one section of a parsed config/credentials file. */
    static final class RawSection {
        final int lineNumber;
        /** Raw content inside the brackets, already whitespace-trimmed. May contain a space (e.g. "profile foo"). */
        final String rawHeader;
        final Map<String, RawProperty> properties = new LinkedHashMap<>();

        RawSection(int lineNumber, String rawHeader) {
            this.lineNumber = lineNumber;
            this.rawHeader = rawHeader;
        }
    }

    /** Intermediate representation of a single property within a section. */
    static final class RawProperty {
        final int lineNumber;
        final String key;
        String value;
        final Map<String, String> subProperties = new LinkedHashMap<>();

        RawProperty(int lineNumber, String key, String value) {
            this.lineNumber = lineNumber;
            this.key = key;
            this.value = value;
        }
    }
}
