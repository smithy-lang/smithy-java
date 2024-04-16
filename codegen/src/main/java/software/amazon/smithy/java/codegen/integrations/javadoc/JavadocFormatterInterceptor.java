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

        // Split out any HTML-tag wrapped sections as we do not want to wrap
        // any customer documentation with tags
        Matcher matcher = PATTERN.matcher(contents);
        int lastMatchPos = 0;
        writer.writeInlineWithNoFormatting(" * ");
        while (matcher.find()) {
            // write all contents up to the match.
            writeDocstringLine(writer, contents.substring(lastMatchPos, matcher.start()));

            // write match contents
            writer.writeInlineWithNoFormatting(
                contents.substring(matcher.start(), matcher.end()).replace("\n", "\n * ")
            );
            lastMatchPos = matcher.end();
        }

        // Write out all remaining contents
        writeDocstringLine(writer, contents.substring(lastMatchPos));
        writer.writeWithNoFormatting("\n */");
    }

    private void writeDocstringLine(JavaWriter writer, String string) {
        for (Scanner it = new Scanner(string); it.hasNextLine();) {
            var s = it.nextLine();
            writer.writeInlineWithNoFormatting(
                StringUtils.wrap(
                    s,
                    MAX_LINE_LENGTH,
                    writer.getNewline() + " * ",
                    false
                )
            );
            if (it.hasNextLine()) {
                writer.writeInlineWithNoFormatting(writer.getNewline() + " * ");
            }
        }
    }
}
