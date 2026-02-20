/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.javadoc;

import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import software.amazon.smithy.java.codegen.sections.JavadocSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.StringUtils;

/**
 * This interceptor will format any text written into a javadoc section into the correct multi-line
 * comment-style.
 *
 * <p>This interceptor should be the last javadoc interceptor to run, so it can pick up the text
 * run by other interceptors for formatting.
 */
final class JavadocFormatterInterceptor implements CodeInterceptor<JavadocSection, JavaWriter> {
    private static final int MAX_LINE_LENGTH = 100;
    private static final Pattern PATTERN = Pattern.compile("<([a-z]+)*>.*?</\\1>", Pattern.DOTALL);
    // Matches the start of an {@snippet ...} inline Javadoc tag (JDK 18+, JEP 413).
    private static final Pattern SNIPPET_START_PATTERN = Pattern.compile("\\{@snippet\\b[^:}]*:");
    // HTML tags supported by javadocs for Java17. Note: this list is not directly documented in JavaDocs documentation
    // and is instead found by inspecting the JDK doclint/HtmlTag.java file.
    private static final Set<String> SUPPORTED_TAGS = Set.of(
            "A",
            "ABBR",
            "ACRONYM",
            "ADDRESS",
            "ARTICLE",
            "ASIDE",
            "B",
            "BDI",
            "BIG",
            "BLOCKQUOTE",
            "BODY",
            "BR",
            "CAPTION",
            "CENTER",
            "CITE",
            "CODE",
            "COL",
            "DD",
            "DEL",
            "DFN",
            "DIV",
            "DT",
            "EM",
            "FONT",
            "FIGURE",
            "FIGCAPTION",
            "FRAME",
            "FRAMESET",
            "H1",
            "H2",
            "H3",
            "H4",
            "H5",
            "H6",
            "HEAD",
            "HR",
            "HTML",
            "I",
            "IFRAME",
            "IMG",
            "INS",
            "KBD",
            "LI",
            "LINK",
            "MAIN",
            "MARK",
            "META",
            "NAV",
            "NOFRAMES",
            "NOSCRIPT",
            "P",
            "PRE",
            "Q",
            "S",
            "SAMP",
            "SCRIPT",
            "SECTION",
            "SMALL",
            "SPAN",
            "STRIKE",
            "STRONG",
            "STYLE",
            "SUB",
            "SUP",
            "TD",
            "TEMPLATE",
            "TH",
            "TIME",
            "TITLE",
            "TT",
            "U",
            "UL",
            "WBR",
            "VAR");
    // Convert problematic characters to their HTML escape codes.
    private static final Map<String, String> REPLACEMENTS = Map.of("*", "&#42;");

    @Override
    public Class<JavadocSection> sectionType() {
        return JavadocSection.class;
    }

    @Override
    public void write(JavaWriter writer, String previousText, JavadocSection section) {
        if (!previousText.isEmpty()) {
            writeDocStringContents(writer, previousText);
        }
    }

    private void writeDocStringContents(JavaWriter writer, String contents) {
        writer.writeWithNoFormatting("/**");
        writer.writeInlineWithNoFormatting(" * ");
        writeDocstringBody(writer, contents, 0);
        writer.writeWithNoFormatting("\n */");
    }

    private void writeDocstringBody(JavaWriter writer, String contents, int nestingLevel) {
        // Split out any {@snippet ...} inline tags first so their contents
        // are not line-wrapped or have characters escaped.
        Matcher snippetMatcher = SNIPPET_START_PATTERN.matcher(contents);
        int lastSnippetPos = 0;
        StringBuilder nonSnippetContents = new StringBuilder();
        while (snippetMatcher.find()) {
            nonSnippetContents.append(contents, lastSnippetPos, snippetMatcher.start());
            // Flush any accumulated non-snippet content through HTML tag processing
            if (!nonSnippetContents.isEmpty()) {
                writeDocstringBodyHtml(writer, nonSnippetContents.toString(), nestingLevel);
                nonSnippetContents.setLength(0);
            }
            // Find the matching closing brace using balanced brace counting,
            // since snippet bodies contain Java code with its own braces.
            int snippetEnd = findMatchingBrace(contents, snippetMatcher.start());
            // Write snippet block verbatim - no wrapping, no escaping
            writeSnippetBlock(writer, contents.substring(snippetMatcher.start(), snippetEnd));
            lastSnippetPos = snippetEnd;
        }
        nonSnippetContents.append(contents.substring(lastSnippetPos));
        if (!nonSnippetContents.isEmpty()) {
            writeDocstringBodyHtml(writer, nonSnippetContents.toString(), nestingLevel);
        }
    }

    /**
     * Finds the position after the closing brace that matches the opening brace of
     * an inline Javadoc tag starting at {@code start}. Uses balanced brace counting
     * to skip over braces in the snippet body (e.g. Java code blocks).
     */
    private static int findMatchingBrace(String contents, int start) {
        int depth = 0;
        for (int i = start; i < contents.length(); i++) {
            char c = contents.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        // If no matching brace found, return end of contents
        return contents.length();
    }

    private void writeSnippetBlock(JavaWriter writer, String snippet) {
        for (Scanner it = new Scanner(snippet); it.hasNextLine();) {
            writer.writeInlineWithNoFormatting(it.nextLine());
            if (it.hasNextLine()) {
                writer.writeInlineWithNoFormatting(writer.getNewline() + " * ");
            }
        }
    }

    private void writeDocstringBodyHtml(JavaWriter writer, String contents, int nestingLevel) {
        // Split out any HTML-tag wrapped sections as we do not want to wrap
        // any customer documentation with tags
        Matcher matcher = PATTERN.matcher(contents);
        int lastMatchPos = 0;
        while (matcher.find()) {
            // write all contents up to the match.
            writeDocstringLine(writer, contents.substring(lastMatchPos, matcher.start()), nestingLevel);

            // write match contents if the HTML tag is supported
            var htmlTag = matcher.group(1);
            if (SUPPORTED_TAGS.contains(htmlTag.toUpperCase())) {
                writer.writeInlineWithNoFormatting("<" + htmlTag + ">");
                var offsetForTagStart = 2 + htmlTag.length();
                var tagContents = contents.substring(matcher.start() + offsetForTagStart, matcher.end());
                writeDocstringBodyHtml(writer, tagContents, nestingLevel + 1);
            }

            lastMatchPos = matcher.end();
        }
        // Write out all remaining contents
        writeDocstringLine(writer, contents.substring(lastMatchPos), nestingLevel);
    }

    private void writeDocstringLine(JavaWriter writer, String string, int nestingLevel) {
        for (Scanner it = new Scanner(string); it.hasNextLine();) {
            var s = it.nextLine();

            // Sanitize string
            for (var entry : REPLACEMENTS.entrySet()) {
                s = s.replace(entry.getKey(), entry.getValue());
            }

            // If we are out of an HTML tag, wrap the string. Otherwise, ignore wrapping.
            var str = nestingLevel == 0
                    ? StringUtils.wrap(s, MAX_LINE_LENGTH, writer.getNewline() + " * ", false)
                    : s;

            writer.writeInlineWithNoFormatting(str);

            if (it.hasNextLine()) {
                writer.writeInlineWithNoFormatting(writer.getNewline() + " * ");
            }
        }
    }
}
