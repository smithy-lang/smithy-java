/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.javadoc;

import java.util.Set;
import java.util.regex.Pattern;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Tag;

/**
 * Converts Markdown or HTML text (from Smithy {@code @documentation} traits) into
 * Javadoc-compatible HTML with proper formatting.
 *
 * <p>The conversion pipeline is:
 * <ol>
 *   <li>Escape Java generics ({@code List<String>}) so they are not parsed as HTML tags</li>
 *   <li>Parse Markdown to HTML via commonmark (already-HTML input passes through unchanged)</li>
 *   <li>Parse the HTML into a jsoup DOM for structural manipulation</li>
 *   <li>Clean up the DOM (unwrap invalid nesting, remove empty elements)</li>
 *   <li>Render the DOM to a Javadoc-formatted string with indentation and line wrapping</li>
 * </ol>
 */
final class MarkdownToJavadoc {

    static final int DEFAULT_MAX_WIDTH = 110;
    private static final int INDENT_WIDTH = 2;
    private static final int MIN_WRAP_WIDTH = 40;

    private static final Parser MD_PARSER = Parser.builder().build();
    private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder().build();
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    // Commonmark treats <Foo> as an HTML tag. Since Java generics like List<String> use
    // uppercase type names, we escape < before uppercase letters so they survive as &lt;
    private static final Pattern JAVA_GENERIC = Pattern.compile("<([A-Z])");

    private static final Set<String> BLOCK_TAGS = Set.of(
            "p",
            "ul",
            "ol",
            "li",
            "blockquote",
            "div",
            "table",
            "tr",
            "th",
            "td",
            "thead",
            "tbody",
            "h1",
            "h2",
            "h3",
            "h4",
            "h5",
            "h6",
            "pre",
            "hr",
            "dl",
            "dt",
            "dd");

    private MarkdownToJavadoc() {}

    /**
     * Converts a Markdown or HTML string to Javadoc-compatible HTML.
     *
     * @param input the documentation trait value (Markdown or HTML)
     * @param maxWidth maximum line width for wrapping (before the " * " prefix)
     * @return formatted Javadoc content, or the input unchanged if null/empty
     */
    static String convert(String input, int maxWidth) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        input = JAVA_GENERIC.matcher(input).replaceAll("&lt;$1");
        String html = HTML_RENDERER.render(MD_PARSER.parse(input));
        Document doc = Jsoup.parseBodyFragment(html);
        cleanDom(doc.body());

        var sb = new StringBuilder();
        renderChildren(doc.body(), sb, maxWidth, 0, true);
        return sb.toString().strip();
    }

    static String convert(String input) {
        return convert(input, DEFAULT_MAX_WIDTH);
    }

    private static void cleanDom(Element root) {
        // Strip non-HTML tags (e.g., <note>, <important> from AWS models).
        // Unwrap keeps the text content but removes the tag itself.
        for (Element el : root.getAllElements()) {
            if (!el.tagName().equals("#root") && !Tag.isKnownTag(el.tagName())) {
                el.unwrap();
            }
        }
        // AWS models produce <li><p>text</p></li>; unwrap the <p> for cleaner output
        for (Element p : root.select("li > p")) {
            p.unwrap();
        }
        // Remove empty paragraphs (whitespace-only counts as empty)
        for (Element p : root.select("p")) {
            if (p.text().isBlank() && p.children().isEmpty()) {
                p.remove();
            }
        }
    }

    private static void renderChildren(
            Element parent,
            StringBuilder sb,
            int maxWidth,
            int indent,
            boolean isFirstBlock
    ) {
        boolean first = isFirstBlock;
        for (Node child : parent.childNodes()) {
            first = renderNode(child, sb, maxWidth, indent, first);
        }
    }

    /**
     * Renders a single DOM node to the output buffer.
     *
     * @param isFirstBlock tracks whether the first block-level element has been emitted yet.
     *     The first {@code <p>} tag is suppressed (Javadoc convention: first paragraph has no tag).
     * @return the updated value of isFirstBlock
     */
    private static boolean renderNode(Node node, StringBuilder sb, int maxWidth, int indent, boolean isFirstBlock) {
        if (node instanceof TextNode text) {
            String content = text.getWholeText();
            if (!isInsidePre(node)) {
                content = WHITESPACE_RUN.matcher(content).replaceAll(" ");
                if (content.isBlank()) {
                    return isFirstBlock;
                }
                content = content.replace("@", "{@literal @}");
                content = encodeHtmlText(content);
            }
            appendWrapped(sb, content, maxWidth, indent);
            return false;
        }

        if (node instanceof Element el) {
            renderElement(el, sb, maxWidth, indent, isFirstBlock);
            return false;
        }

        return isFirstBlock;
    }

    private static void renderElement(
            Element el,
            StringBuilder sb,
            int maxWidth,
            int indent,
            boolean isFirstBlock
    ) {
        String tag = el.tagName();

        // <pre> content is preserved verbatim (no wrapping, no escaping)
        if (tag.equals("pre")) {
            ensureNewline(sb);
            if (!isFirstBlock) {
                appendIndent(sb, indent);
                sb.append("\n");
            }
            appendIndent(sb, indent);
            // Commonmark wraps code blocks in <pre><code>; render as <pre>{@code ...}</pre>
            Element codeChild = el.selectFirst("> code");
            if (codeChild != null) {
                sb.append("<pre>{@code\n").append(codeChild.wholeText()).append("\n");
                appendIndent(sb, indent);
                sb.append("}</pre>");
            } else {
                sb.append("<pre>").append(el.wholeText()).append("</pre>");
            }
            return;
        }

        if (tag.equals("hr")) {
            ensureNewline(sb);
            appendIndent(sb, indent);
            sb.append("<hr>");
            return;
        }

        if (tag.equals("br")) {
            sb.append("<br>");
            return;
        }

        if (tag.equals("img")) {
            sb.append(el.outerHtml());
            return;
        }

        // First <p> in the document: emit content without the <p> tag (Javadoc convention)
        if (tag.equals("p") && isFirstBlock) {
            renderChildren(el, sb, maxWidth, indent, false);
            return;
        }

        // Subsequent <p>: blank line separator, then <p> prefix
        if (tag.equals("p")) {
            ensureNewline(sb);
            sb.append("\n");
            appendIndent(sb, indent);
            sb.append("<p>");
            renderChildren(el, sb, maxWidth, indent, false);
            return;
        }

        // Block-level tags: each on its own line, children indented
        if (BLOCK_TAGS.contains(tag)) {
            ensureNewline(sb);
            // Blank line before top-level blocks (e.g., <ul> after text),
            // but not before nested blocks (e.g., <li> inside <ul>)
            if (isTopLevelBlock(el)) {
                sb.append("\n");
            }
            appendIndent(sb, indent);
            sb.append("<").append(tag).append(copyAttributes(el)).append(">\n");
            renderChildren(el, sb, maxWidth, indent + INDENT_WIDTH, false);
            ensureNewline(sb);
            appendIndent(sb, indent);
            sb.append("</").append(tag).append(">");
            return;
        }

        // Inline tags: add indent if starting a new line (e.g., <code> as first child of <li>)
        if (currentLineLength(sb) == 0 && indent > 0) {
            appendIndent(sb, indent);
        }
        sb.append("<").append(tag).append(copyAttributes(el)).append(">");
        renderChildren(el, sb, maxWidth, indent, false);
        sb.append("</").append(tag).append(">");
    }

    /**
     * Appends text to the buffer with word wrapping. Wrapping respects the current indent
     * level (subtracted from maxWidth) and never breaks inside Javadoc inline tags like
     * {@code {@literal @}}.
     */
    private static void appendWrapped(StringBuilder sb, String text, int maxWidth, int indent) {
        int effectiveMax = Math.max(maxWidth - indent, MIN_WRAP_WIDTH);
        int col = currentLineLength(sb);
        if (col == 0 && indent > 0) {
            appendIndent(sb, indent);
            col = indent;
        }
        int i = 0;
        while (i < text.length()) {
            // Javadoc inline tags (e.g., {@literal @}) must not be split
            if (text.charAt(i) == '{' && i + 1 < text.length() && text.charAt(i + 1) == '@') {
                int braceEnd = findMatchingBrace(text, i);
                String token = text.substring(i, braceEnd);
                if (col + token.length() > effectiveMax && col > indent) {
                    wrapLine(sb);
                    appendIndent(sb, indent);
                    col = indent;
                }
                sb.append(token);
                col += token.length();
                i = braceEnd;
                continue;
            }

            // Find next word (including any leading whitespace)
            int wordStart = i;
            int wordEnd = i;
            while (wordEnd < text.length() && text.charAt(wordEnd) == ' ') {
                wordEnd++;
            }
            while (wordEnd < text.length() && text.charAt(wordEnd) != ' '
                    && !(text.charAt(wordEnd) == '{' && wordEnd + 1 < text.length()
                            && text.charAt(wordEnd + 1) == '@')) {
                wordEnd++;
            }
            if (wordEnd == wordStart) {
                break;
            }
            String word = text.substring(wordStart, wordEnd);
            if (col + word.length() > effectiveMax && col > indent) {
                wrapLine(sb);
                appendIndent(sb, indent);
                word = word.stripLeading();
                col = indent;
            }
            sb.append(word);
            col += word.length();
            i = wordEnd;
        }
    }

    /**
     * Encodes HTML special characters in text, preserving Javadoc inline tags like
     * {@code {@literal @}} which use braces and @ that must not be encoded.
     */
    private static String encodeHtmlText(String text) {
        var sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{' && i + 1 < text.length() && text.charAt(i + 1) == '@') {
                int end = findMatchingBrace(text, i);
                sb.append(text, i, end);
                i = end - 1;
            } else if (c == '&') {
                sb.append("&amp;");
            } else if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isInsidePre(Node node) {
        Node parent = node.parentNode();
        while (parent != null) {
            if (parent instanceof Element el && el.tagName().equals("pre")) {
                return true;
            }
            parent = parent.parentNode();
        }
        return false;
    }

    /**
     * A block is "top-level" if it is not nested inside a list or table structure.
     * Top-level blocks get a blank line before them for visual separation.
     */
    private static boolean isTopLevelBlock(Element el) {
        var parent = el.parent();
        if (parent == null) {
            return false;
        }
        return switch (parent.tagName()) {
            case "ul", "ol", "dl", "table", "thead", "tbody", "tr" -> false;
            default -> true;
        };
    }

    private static String copyAttributes(Element el) {
        if (el.attributes().isEmpty()) {
            return "";
        }
        var sb = new StringBuilder();
        el.attributes()
                .forEach(attr -> sb.append(" ")
                        .append(attr.getKey())
                        .append("=\"")
                        .append(attr.getValue())
                        .append("\""));
        return sb.toString();
    }

    private static void appendIndent(StringBuilder sb, int indent) {
        sb.append(" ".repeat(indent));
    }

    private static void ensureNewline(StringBuilder sb) {
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
            sb.append("\n");
        }
    }

    private static void wrapLine(StringBuilder sb) {
        while (!sb.isEmpty() && sb.charAt(sb.length() - 1) == ' ') {
            sb.setLength(sb.length() - 1);
        }
        sb.append("\n");
    }

    private static int currentLineLength(StringBuilder sb) {
        int lastNewline = sb.lastIndexOf("\n");
        return lastNewline < 0 ? sb.length() : sb.length() - lastNewline - 1;
    }

    private static int findMatchingBrace(String s, int start) {
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) == '{') {
                depth++;
            } else if (s.charAt(i) == '}') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return s.length();
    }
}
