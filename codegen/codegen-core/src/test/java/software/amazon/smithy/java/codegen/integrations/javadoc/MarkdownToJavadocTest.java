/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.javadoc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MarkdownToJavadocTest {

    @Nested
    class EdgeCases {
        @Test
        void nullReturnsNull() {
            assertThat(MarkdownToJavadoc.convert(null)).isNull();
        }

        @Test
        void emptyReturnsEmpty() {
            assertThat(MarkdownToJavadoc.convert("")).isEmpty();
        }
    }

    @Nested
    class MarkdownInput {
        @Test
        void singleParagraphHasNoPTag() {
            assertThat(MarkdownToJavadoc.convert("This is a simple paragraph."))
                    .isEqualTo("This is a simple paragraph.");
        }

        @Test
        void multipleParagraphsGetBlankLineSeparation() {
            var result = MarkdownToJavadoc.convert("First paragraph.\n\nSecond paragraph.");
            // First paragraph has no <p> tag, second gets <p> with blank line before it
            assertThat(result).startsWith("First paragraph.");
            assertThat(result).contains("\n\n<p>Second paragraph.");
        }

        @Test
        void inlineFormatting() {
            var result = MarkdownToJavadoc.convert("This is **bold** and *italic*.");
            assertThat(result).contains("<strong>bold</strong>");
            assertThat(result).contains("<em>italic</em>");
        }

        @Test
        void inlineCode() {
            assertThat(MarkdownToJavadoc.convert("Use `foo()` method."))
                    .isEqualTo("Use <code>foo()</code> method.");
        }

        @Test
        void atSignIsEscapedInText() {
            var result = MarkdownToJavadoc.convert("Send email to user@example.com");
            assertThat(result).contains("{@literal @}example.com");
            assertThat(result).doesNotContain("@example");
        }

        @Test
        void atSignIsEscapedInsideCode() {
            var result = MarkdownToJavadoc.convert("Use `@Override` annotation.");
            assertThat(result).contains("<code>{@literal @}Override</code>");
        }

        @Test
        void linksPreserveHrefAndText() {
            var result = MarkdownToJavadoc.convert("See [AWS docs](https://aws.amazon.com).");
            assertThat(result).contains("<a href=\"https://aws.amazon.com\">AWS docs</a>");
        }

        @Test
        void headingsRenderedAsHtmlTags() {
            var result = MarkdownToJavadoc.convert("# Title\n\nSome text.");
            assertThat(result).contains("<h1>");
            assertThat(result).contains("Title");
            assertThat(result).contains("</h1>");
            assertThat(result).contains("Some text.");
        }

        @Test
        void fencedCodeBlockUsesJavadocCodeTag() {
            var result = MarkdownToJavadoc.convert("Text.\n\n```\ncode here\n```");
            assertThat(result).contains("<pre>{@code");
            assertThat(result).contains("code here");
            assertThat(result).contains("}</pre>");
            // Code block content should not be wrapped or escaped
            assertThat(result).doesNotContain("&lt;");
        }

        @Test
        void unorderedListPrettyPrinted() {
            var result = MarkdownToJavadoc.convert("Items:\n\n- one\n- two");
            // Block structure: <ul> on its own line, <li> indented
            assertThat(result).contains("<ul>\n");
            assertThat(result).contains("  <li>\n");
            assertThat(result).contains("    one\n");
            assertThat(result).contains("  </li>");
            assertThat(result).contains("</ul>");
        }

        @Test
        void blockquoteRendered() {
            var result = MarkdownToJavadoc.convert("> quoted text");
            assertThat(result).contains("<blockquote>\n");
            assertThat(result).contains("quoted text");
            assertThat(result).contains("</blockquote>");
        }
    }

    @Nested
    class HtmlInput {
        @Test
        void firstParagraphTagStripped() {
            assertThat(MarkdownToJavadoc.convert("<p>Simple text.</p>"))
                    .isEqualTo("Simple text.");
        }

        @Test
        void subsequentParagraphsGetBlankLine() {
            var result = MarkdownToJavadoc.convert(
                    "<p>First paragraph.</p><p>Second paragraph.</p>");
            assertThat(result).startsWith("First paragraph.");
            assertThat(result).contains("\n\n<p>Second paragraph.");
        }

        @Test
        void whitespaceCollapsedAcrossNewlines() {
            var html = "<p>Audio streams are encoded as   \n      HTTP/2 data frames.</p>";
            var result = MarkdownToJavadoc.convert(html);
            // Multiple spaces and newlines collapsed to single space
            assertThat(result).isEqualTo("Audio streams are encoded as HTTP/2 data frames.");
        }

        @Test
        void unwrapsParagraphInsideListItem() {
            var result = MarkdownToJavadoc.convert(
                    "<ul><li><p>Item one</p></li><li><p>Item two</p></li></ul>");
            // The <p> inside <li> should be removed, content preserved with spacing
            assertThat(result).contains("Item one");
            assertThat(result).contains("Item two");
            assertThat(result).doesNotContain("<p>");
        }

        @Test
        void removesEmptyParagraphs() {
            var result = MarkdownToJavadoc.convert("<p>Text.</p><p>  </p><p>More.</p>");
            // The whitespace-only <p> should be removed
            var paragraphCount = result.split("<p>").length - 1;
            assertThat(paragraphCount).isEqualTo(1); // only "More." gets a <p>
        }

        @Test
        void preservesInlineCodeAndLinks() {
            var html = "<p>Use <code>getFoo()</code> or see <a href=\"https://example.com\">docs</a>.</p>";
            var result = MarkdownToJavadoc.convert(html);
            assertThat(result).contains("<code>getFoo()</code>");
            assertThat(result).contains("<a href=\"https://example.com\">docs</a>");
        }

        @Test
        void escapesAtSignInHtmlText() {
            var result = MarkdownToJavadoc.convert("<p>Use @deprecated annotation.</p>");
            assertThat(result).contains("{@literal @}deprecated");
            assertThat(result).doesNotContain(" @deprecated");
        }

        @Test
        void prettyPrintsNestedBlockTags() {
            var result = MarkdownToJavadoc.convert("<ul><li>Foo</li><li>Bar</li></ul>");
            // Verify indentation structure
            assertThat(result).contains("<ul>\n");
            assertThat(result).contains("  <li>\n");
            assertThat(result).contains("    Foo\n");
            assertThat(result).contains("  </li>\n");
            assertThat(result).endsWith("</ul>");
        }

        @Test
        void blankLineBeforeTopLevelBlockTags() {
            var result = MarkdownToJavadoc.convert("<p>Text.</p><ul><li>item</li></ul>");
            // <ul> should be preceded by a blank line since it follows a <p>
            assertThat(result).contains("\n\n<ul>");
        }

        @Test
        void stripsUnknownHtmlTags() {
            var result = MarkdownToJavadoc.convert(
                    "<p>Text.</p><note>This is not a real tag.</note><important>Neither is this.</important>");
            // Unknown tags removed, but their text content preserved
            assertThat(result).doesNotContain("<note>");
            assertThat(result).doesNotContain("<important>");
            assertThat(result).contains("This is not a real tag.");
            assertThat(result).contains("Neither is this.");
        }
    }

    @Nested
    class HtmlEntityEscaping {
        @Test
        void angleBracketsInTextEscaped() {
            var result = MarkdownToJavadoc.convert("foo < bar > baz");
            assertThat(result).contains("&lt;");
            assertThat(result).contains("&gt;");
            assertThat(result).doesNotContain(" < ");
        }

        @Test
        void javaGenericsEscaped() {
            var result = MarkdownToJavadoc.convert("Returns a List<String> value.");
            assertThat(result).contains("List&lt;String&gt;");
            // Should not be parsed as an HTML <String> tag
            assertThat(result).doesNotContain("<string>");
        }

        @Test
        void ampersandEscaped() {
            var result = MarkdownToJavadoc.convert("A & B");
            assertThat(result).contains("&amp;");
        }
    }

    @Nested
    class LineWrapping {
        @Test
        void wrapsAtConfiguredWidth() {
            var longText = "This is a very long line that should be wrapped because it exceeds the maximum width "
                    + "that we have configured for the output formatting.";
            var result = MarkdownToJavadoc.convert(longText, 60);
            var lines = result.split("\n");
            assertThat(lines.length).isGreaterThan(1);
            for (var line : lines) {
                assertThat(line.length()).isLessThanOrEqualTo(60);
            }
        }

        @Test
        void htmlTagsNeverSplitAcrossLines() {
            var text = "See <a href=\"https://docs.aws.amazon.com/very/long/path\">AWS docs</a> for info.";
            var result = MarkdownToJavadoc.convert(text, 40);
            // The full opening tag must appear on a single line
            for (var line : result.split("\n")) {
                if (line.contains("<a ")) {
                    assertThat(line).contains("\">");
                }
            }
        }

        @Test
        void javadocInlineTagsNeverSplit() {
            var result = MarkdownToJavadoc.convert("Use the @Override annotation in your code.", 30);
            // {@literal @} must not be broken across lines
            for (var line : result.split("\n")) {
                if (line.contains("{@literal")) {
                    assertThat(line).contains("{@literal @}");
                }
            }
        }

        @Test
        void shortLinesNotWrapped() {
            assertThat(MarkdownToJavadoc.convert("Short.", 80)).isEqualTo("Short.");
        }

        @Test
        void indentationReducesEffectiveWrapWidth() {
            // A list item's content is indented 4 spaces (2 for <ul> children, 2 for <li> children).
            // With maxWidth=50, effective width inside <li> is 50-4=46.
            var result = MarkdownToJavadoc.convert(
                    "<ul><li>This text is long enough to wrap at a narrow width setting</li></ul>",
                    50);
            var lines = result.split("\n");
            for (var line : lines) {
                assertThat(line.length()).isLessThanOrEqualTo(50);
            }
        }
    }
}
