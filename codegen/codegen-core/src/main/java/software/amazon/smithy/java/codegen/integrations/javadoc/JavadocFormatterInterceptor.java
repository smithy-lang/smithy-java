/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.javadoc;

import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import software.amazon.smithy.java.codegen.sections.JavadocSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.utils.CodeInterceptor;

/**
 * Formats text written into a javadoc section into the correct multi-line comment style.
 *
 * <p>This interceptor should be the last javadoc interceptor to run. It wraps the content
 * in {@code /** ... * /} delimiters and prefixes each line with {@code *}.
 *
 * <p>Line wrapping and HTML formatting are handled upstream by {@link MarkdownToJavadoc}.
 */
final class JavadocFormatterInterceptor implements CodeInterceptor<JavadocSection, JavaWriter> {
    // Matches the start of an {@snippet ...} inline Javadoc tag (JDK 18+, JEP 413).
    private static final Pattern SNIPPET_START_PATTERN = Pattern.compile("\\{@snippet\\b[^:}]*:");

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
        writeBody(writer, contents);
        writer.writeWithNoFormatting("\n */");
    }

    private void writeBody(JavaWriter writer, String contents) {
        // Split out {@snippet ...} inline tags so their contents are not escaped.
        Matcher snippetMatcher = SNIPPET_START_PATTERN.matcher(contents);
        int lastPos = 0;
        StringBuilder pending = new StringBuilder();
        while (snippetMatcher.find()) {
            pending.append(contents, lastPos, snippetMatcher.start());
            if (!pending.isEmpty()) {
                writeLines(writer, pending.toString());
                pending.setLength(0);
            }
            int snippetEnd = findMatchingBrace(contents, snippetMatcher.start());
            writeVerbatimLines(writer, contents.substring(snippetMatcher.start(), snippetEnd));
            lastPos = snippetEnd;
        }
        pending.append(contents.substring(lastPos));
        if (!pending.isEmpty()) {
            writeLines(writer, pending.toString());
        }
    }

    /**
     * Writes pre-formatted content, prefixing each line with {@code *} and escaping
     * literal {@code *} characters in the content.
     */
    private void writeLines(JavaWriter writer, String text) {
        for (Scanner it = new Scanner(text); it.hasNextLine();) {
            var line = it.nextLine();
            // Escape literal * so it doesn't close the javadoc comment
            line = line.replace("*", "&#42;");
            writer.writeInlineWithNoFormatting(line);
            if (it.hasNextLine()) {
                writer.writeInlineWithNoFormatting(writer.getNewline() + " * ");
            }
        }
    }

    /**
     * Writes snippet content verbatim (no escaping), prefixing each line with {@code *}.
     */
    private void writeVerbatimLines(JavaWriter writer, String text) {
        for (Scanner it = new Scanner(text); it.hasNextLine();) {
            writer.writeInlineWithNoFormatting(it.nextLine());
            if (it.hasNextLine()) {
                writer.writeInlineWithNoFormatting(writer.getNewline() + " * ");
            }
        }
    }

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
        return contents.length();
    }
}
