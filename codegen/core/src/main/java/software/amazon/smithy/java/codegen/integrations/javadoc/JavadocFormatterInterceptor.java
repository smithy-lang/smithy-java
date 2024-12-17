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
        "VAR"
    );
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
                writeDocstringBody(writer, tagContents, nestingLevel + 1);
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
