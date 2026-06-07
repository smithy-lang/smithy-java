/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.codecs.commons.NumberCodec;
import software.amazon.smithy.java.codecs.commons.TimestampCodec;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.SpecificShapeDeserializer;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

/**
 * High-performance XML deserializer that inlines all byte-level parsing directly.
 *
 * <p>This class combines the ShapeDeserializer contract with a custom byte-level XML parser,
 * avoiding the overhead of javax.xml.stream and intermediate String allocations for numeric types.
 */
final class SmithyXmlDeserializer implements ShapeDeserializer, XmlErrorCodeParser {

    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
    private static final int START_ELEMENT = 1;
    private static final int END_ELEMENT = 2;
    private static final int CHARACTERS = 3;
    private static final int EOF = -1;

    private static final boolean[] NAME_CHAR = new boolean[256];

    static {
        for (int i = 'a'; i <= 'z'; i++)
            NAME_CHAR[i] = true;
        for (int i = 'A'; i <= 'Z'; i++)
            NAME_CHAR[i] = true;
        for (int i = '0'; i <= '9'; i++)
            NAME_CHAR[i] = true;
        NAME_CHAR['-'] = true;
        NAME_CHAR['_'] = true;
        NAME_CHAR['.'] = true;
        NAME_CHAR[':'] = true;
    }

    private final byte[] buf;
    private int pos;
    private final int limit;
    private int nameStart;
    private int nameLen;
    private boolean selfClosing;
    private int attrCount;
    private int[] attrNameStarts;
    private int[] attrNameLens;
    private int[] attrValueStarts;
    private int[] attrValueLens;

    private int endNameStart;
    private int endNameLen;

    private int textSpanStart;
    private int textSpanEnd;
    private String textFallback;

    private final MemberDeserializer memberDeserializer = new MemberDeserializer();

    private final int[] lookupHint = new int[1];

    private int errorWrapperDepth;

    private final XmlInfo xmlInfo;
    private final boolean isTopLevel;
    private final List<String> wrapperElements;

    SmithyXmlDeserializer(
            byte[] buf,
            int offset,
            int length,
            XmlInfo xmlInfo,
            boolean isTopLevel,
            List<String> wrapperElements
    ) {
        this.buf = buf;
        this.pos = offset;
        this.limit = offset + length;
        this.xmlInfo = xmlInfo;
        this.isTopLevel = isTopLevel;
        this.wrapperElements = wrapperElements;
        skipProlog();
    }

    private int next() {
        if (selfClosing) {
            selfClosing = false;
            endNameStart = nameStart;
            endNameLen = nameLen;
            return END_ELEMENT;
        }
        while (pos < limit) {
            byte b = buf[pos];
            if (b == '<') {
                pos++;
                if (pos >= limit) {
                    return EOF;
                }
                b = buf[pos];
                if (b == '/') {
                    pos++;
                    parseEndElement();
                    return END_ELEMENT;
                } else if (b == '!') {
                    pos++;
                    if (pos + 1 < limit && buf[pos] == '-' && buf[pos + 1] == '-') {
                        pos += 2;
                        skipComment();
                        continue;
                    } else if (pos + 6 < limit && buf[pos] == '['
                            && buf[pos + 1] == 'C'
                            && buf[pos + 2] == 'D'
                            && buf[pos + 3] == 'A'
                            && buf[pos + 4] == 'T'
                            && buf[pos + 5] == 'A'
                            && buf[pos + 6] == '[') {
                        pos += 7;
                        while (pos + 2 < limit) {
                            if (buf[pos] == ']' && buf[pos + 1] == ']' && buf[pos + 2] == '>') {
                                pos += 3;
                                break;
                            }
                            pos++;
                        }
                        continue;
                    }
                    throw new SerializationException(
                            "Unsupported markup declaration at byte offset " + (pos - 1));
                } else if (b == '?') {
                    pos++;
                    skipPastString((byte) '?', (byte) '>');
                    continue;
                } else {
                    parseStartElement();
                    return START_ELEMENT;
                }
            } else if (isWhitespace(b)) {
                pos++;
                continue;
            } else {
                while (pos < limit && buf[pos] != '<') {
                    pos++;
                }
                return CHARACTERS;
            }
        }
        return EOF;
    }

    private void skipProlog() {
        while (pos < limit) {
            skipWhitespace();
            if (pos >= limit) {
                return;
            }
            if (buf[pos] != '<') {
                throw new SerializationException("Content is not allowed in prolog");
            }
            int peek = pos + 1;
            if (peek >= limit) {
                return;
            }
            byte b = buf[peek];
            if (b == '?') {
                pos = peek + 1;
                skipPastString((byte) '?', (byte) '>');
            } else if (b == '!') {
                pos = peek + 1;
                if (pos + 1 < limit && buf[pos] == '-' && buf[pos + 1] == '-') {
                    pos += 2;
                    skipComment();
                } else {
                    while (pos < limit && buf[pos] != '>') {
                        pos++;
                    }
                    if (pos < limit) {
                        pos++; // skip '>'
                    }
                }
            } else {
                return;
            }
        }
    }

    private void parseStartElement() {
        int start = pos;
        boolean hasColon = false;
        int colonPos = 0;
        while (pos < limit) {
            byte b = buf[pos];
            if (!NAME_CHAR[b & 0xFF])
                break;
            if (b == ':') {
                hasColon = true;
                colonPos = pos;
            }
            pos++;
        }

        if (hasColon) {
            nameStart = colonPos + 1;
        } else {
            nameStart = start;
        }
        nameLen = pos - nameStart;

        attrCount = 0;
        selfClosing = false;

        while (pos < limit) {
            byte b = buf[pos];
            if (b == '>') {
                pos++;
                return;
            } else if (b == '/') {
                pos++;
                if (pos < limit && buf[pos] == '>') {
                    pos++;
                    selfClosing = true;
                    return;
                }
            } else if (b <= ' ') {
                pos++;
            } else if (NAME_CHAR[b & 0xFF]) {
                parseAttribute();
            } else {
                throw new SerializationException(
                        "Malformed XML: unexpected character '" + (char) b + "' in element tag");
            }
        }
    }

    private void parseEndElement() {
        int localStart = pos;
        while (pos < limit) {
            byte b = buf[pos];
            if (!NAME_CHAR[b & 0xFF])
                break;
            if (b == ':')
                localStart = pos + 1;
            pos++;
        }
        endNameStart = localStart;
        endNameLen = pos - localStart;
        while (pos < limit && buf[pos] != '>') {
            pos++;
        }
        if (pos >= limit) {
            throw new SerializationException("Unexpected end of input in end tag");
        }
        pos++;
    }

    private void parseAttribute() {
        int aNameStart = pos;
        while (pos < limit && isNameChar(buf[pos])) {
            pos++;
        }
        int aNameLen = pos - aNameStart;

        skipWhitespace();
        if (pos >= limit || buf[pos] != '=') {
            throw new SerializationException("Malformed attribute: expected '=' after attribute name");
        }
        pos++;
        skipWhitespace();

        int aValueStart = 0;
        int aValueLen = 0;
        if (pos >= limit || (buf[pos] != '"' && buf[pos] != '\'')) {
            throw new SerializationException("Malformed attribute: expected quoted value");
        }
        if (pos < limit && (buf[pos] == '"' || buf[pos] == '\'')) {
            byte quote = buf[pos];
            pos++;
            aValueStart = pos;
            while (pos < limit && buf[pos] != quote) {
                pos++;
            }
            aValueLen = pos - aValueStart;
            if (pos < limit) {
                pos++; // skip closing quote
            }
        }

        if (aNameLen >= 5 && buf[aNameStart] == 'x'
                && buf[aNameStart + 1] == 'm'
                && buf[aNameStart + 2] == 'l'
                && buf[aNameStart + 3] == 'n'
                && buf[aNameStart + 4] == 's') {
            return;
        }

        int localNameStart = aNameStart;
        for (int i = aNameStart; i < aNameStart + aNameLen; i++) {
            if (buf[i] == ':') {
                localNameStart = i + 1;
                break;
            }
        }
        int localNameLen = (aNameStart + aNameLen) - localNameStart;

        ensureAttrCapacity();
        attrNameStarts[attrCount] = localNameStart;
        attrNameLens[attrCount] = localNameLen;
        attrValueStarts[attrCount] = aValueStart;
        attrValueLens[attrCount] = aValueLen;
        attrCount++;
    }

    private void ensureAttrCapacity() {
        if (attrNameStarts == null) {
            attrNameStarts = new int[4];
            attrNameLens = new int[4];
            attrValueStarts = new int[4];
            attrValueLens = new int[4];
        } else if (attrCount >= attrNameStarts.length) {
            int newCap = attrNameStarts.length * 2;
            attrNameStarts = Arrays.copyOf(attrNameStarts, newCap);
            attrNameLens = Arrays.copyOf(attrNameLens, newCap);
            attrValueStarts = Arrays.copyOf(attrValueStarts, newCap);
            attrValueLens = Arrays.copyOf(attrValueLens, newCap);
        }
    }

    private void skipElement() {
        skipElement(nameStart, nameLen);
    }

    private void skipElement(int expectedNameStart, int expectedNameLen) {
        if (selfClosing) {
            selfClosing = false;
            return;
        }
        int depth = 1;
        while (depth > 0 && pos < limit) {
            int event = next();
            if (event == START_ELEMENT) {
                depth++;
            } else if (event == END_ELEMENT) {
                depth--;
                if (depth == 0) {
                    if (endNameLen != expectedNameLen
                            || !Arrays.equals(buf,
                                    endNameStart,
                                    endNameStart + endNameLen,
                                    buf,
                                    expectedNameStart,
                                    expectedNameStart + expectedNameLen)) {
                        throw new SerializationException(
                                "Mismatched end tag: expected '</"
                                        + new String(buf, expectedNameStart, expectedNameLen, StandardCharsets.UTF_8)
                                        + ">' but found '</"
                                        + new String(buf, endNameStart, endNameLen, StandardCharsets.UTF_8) + ">'");
                    }
                }
            } else if (event == EOF) {
                throw new SerializationException(
                        "Unexpected end of input while looking for end tag '</"
                                + new String(buf, expectedNameStart, expectedNameLen, StandardCharsets.UTF_8) + ">'");
            }
        }
    }

    private void consumeEndElement() {
        if (pos + 1 < limit && buf[pos] == '<' && buf[pos + 1] == '/') {
            pos += 2;
            int expectedStart = nameStart;
            int expectedLen = nameLen;
            if (pos + expectedLen <= limit
                    && Arrays.equals(buf, pos, pos + expectedLen, buf, expectedStart, expectedStart + expectedLen)) {
                pos += expectedLen;
                while (pos < limit && buf[pos] != '>') {
                    pos++;
                }
                if (pos < limit) {
                    pos++;
                }
                return;
            }
            pos -= 2;
        }
        consumeEndElementSlow();
    }

    private void consumeEndElementSlow() {
        int expectedStart = nameStart;
        int expectedLen = nameLen;
        while (true) {
            int event = next();
            if (event == END_ELEMENT) {
                if (endNameLen != expectedLen
                        || !Arrays.equals(buf,
                                endNameStart,
                                endNameStart + endNameLen,
                                buf,
                                expectedStart,
                                expectedStart + expectedLen)) {
                    throw new SerializationException(
                            "Mismatched end tag: expected '</"
                                    + new String(buf, expectedStart, expectedLen, StandardCharsets.UTF_8)
                                    + ">' but found '</"
                                    + new String(buf, endNameStart, endNameLen, StandardCharsets.UTF_8) + ">'");
                }
                return;
            } else if (event == START_ELEMENT) {
                skipElement();
            } else if (event == CHARACTERS) {
                continue;
            } else {
                throw new SerializationException(
                        "Unexpected end of input while looking for end tag '</"
                                + new String(buf, expectedStart, expectedLen, StandardCharsets.UTF_8) + ">'");
            }
        }
    }

    private void consumeEndElement(byte[] expectedName) {
        while (true) {
            int event = next();
            if (event == END_ELEMENT) {
                if (endNameLen != expectedName.length
                        || !Arrays.equals(buf,
                                endNameStart,
                                endNameStart + endNameLen,
                                expectedName,
                                0,
                                expectedName.length)) {
                    throw new SerializationException(
                            "Mismatched end tag: expected '</"
                                    + new String(expectedName, StandardCharsets.UTF_8)
                                    + ">' but found '</"
                                    + new String(buf, endNameStart, endNameLen, StandardCharsets.UTF_8) + ">'");
                }
                return;
            } else if (event == CHARACTERS) {
                continue;
            } else if (event == EOF) {
                throw new SerializationException(
                        "Unexpected end of input while looking for end tag '</"
                                + new String(expectedName, StandardCharsets.UTF_8) + ">'");
            } else {
                throw new SerializationException(
                        "Expected end element for '"
                                + new String(expectedName, StandardCharsets.UTF_8)
                                + "' but found other content");
            }
        }
    }

    private void validateContainerEndTag(int expectedStart, int expectedLen) {
        if (endNameLen != expectedLen
                || !Arrays.equals(buf,
                        endNameStart,
                        endNameStart + endNameLen,
                        buf,
                        expectedStart,
                        expectedStart + expectedLen)) {
            throw new SerializationException(
                    "Mismatched end tag: expected '</"
                            + new String(buf, expectedStart, expectedLen, StandardCharsets.UTF_8)
                            + ">' but found '</"
                            + new String(buf, endNameStart, endNameLen, StandardCharsets.UTF_8) + ">'");
        }
    }

    private void skipComment() {
        while (pos + 2 < limit) {
            if (buf[pos] == '-' && buf[pos + 1] == '-' && buf[pos + 2] == '>') {
                pos += 3;
                return;
            }
            pos++;
        }
        pos = limit;
    }

    private void skipWhitespace() {
        while (pos < limit && isWhitespace(buf[pos])) {
            pos++;
        }
    }

    private void skipPastString(byte b1, byte b2) {
        while (pos + 1 < limit) {
            if (buf[pos] == b1 && buf[pos + 1] == b2) {
                pos += 2;
                return;
            }
            pos++;
        }
        pos = limit;
    }

    private boolean nextStartElement() {
        if (selfClosing) {
            selfClosing = false;
            endNameStart = nameStart;
            endNameLen = nameLen;
            return false;
        }
        while (pos < limit) {
            byte b = buf[pos];
            if (b <= ' ') {
                pos++;
                continue;
            }
            if (b != '<') {
                while (pos < limit && buf[pos] != '<')
                    pos++;
                continue;
            }
            pos++;
            if (pos >= limit)
                return false;
            b = buf[pos];
            if (b == '/') {
                pos++;
                parseEndElement();
                return false;
            } else if (b == '!' || b == '?') {
                skipMarkup(b);
                continue;
            } else {
                parseStartElement();
                return true;
            }
        }
        return false;
    }

    private void skipMarkup(byte type) {
        if (type == '!') {
            pos++;
            if (pos + 1 < limit && buf[pos] == '-' && buf[pos + 1] == '-') {
                pos += 2;
                skipComment();
            } else if (pos + 6 < limit && buf[pos] == '['
                    && buf[pos + 1] == 'C'
                    && buf[pos + 2] == 'D'
                    && buf[pos + 3] == 'A'
                    && buf[pos + 4] == 'T'
                    && buf[pos + 5] == 'A'
                    && buf[pos + 6] == '[') {
                pos += 7;
                while (pos + 2 < limit) {
                    if (buf[pos] == ']' && buf[pos + 1] == ']' && buf[pos + 2] == '>') {
                        pos += 3;
                        return;
                    }
                    pos++;
                }
            } else {
                throw new SerializationException(
                        "Unsupported markup declaration at byte offset " + (pos - 1));
            }
        } else {
            pos++;
            skipPastString((byte) '?', (byte) '>');
        }
    }

    private static boolean isWhitespace(byte b) {
        return b == ' ' || b == '\n' || b == '\r' || b == '\t';
    }

    private static boolean isNameChar(byte b) {
        return NAME_CHAR[b & 0xFF];
    }

    /**
     * Scans text content from current pos until the end-tag '</', sets textSpanStart/textSpanEnd.
     * Strips leading and trailing whitespace from the span.
     * When CDATA is present, coalesces all text+CDATA via readTextContent() and sets textSpanStart=-1
     * with the result in textFallback.
     */
    private void readTextSpan() {
        if (selfClosing) {
            textSpanStart = pos;
            textSpanEnd = pos;
            return;
        }
        int start = pos;
        while (pos < limit && buf[pos] != '<') {
            pos++;
        }
        if (pos + 8 < limit && buf[pos + 1] == '!'
                && buf[pos + 2] == '['
                && buf[pos + 3] == 'C'
                && buf[pos + 4] == 'D'
                && buf[pos + 5] == 'A'
                && buf[pos + 6] == 'T'
                && buf[pos + 7] == 'A'
                && buf[pos + 8] == '[') {
            pos = start;
            textFallback = readTextContent();
            textSpanStart = -1;
            textSpanEnd = -1;
            return;
        }
        int end = pos;

        textSpanStart = start;
        textSpanEnd = end;
    }

    /**
     * Returns true if the last readTextSpan() hit CDATA and fell back to textFallback.
     */
    private boolean spanHasCdataFallback() {
        return textSpanStart == -1;
    }

    /**
     * Reads text content as a String, handling XML entity unescaping and CDATA sections.
     * Used only for String-valued fields where we must produce a String anyway.
     */
    private String readTextContent() {
        if (selfClosing) {
            return "";
        }
        int start = pos;
        while (pos < limit && buf[pos] != '<') {
            pos++;
        }

        if (pos >= limit || buf[pos + 1] != '!') {
            int len = pos - start;
            if (len == 0) {
                return "";
            }
            boolean clean = true;
            for (int i = start; i < pos; i++) {
                int b = buf[i] & 0xFF;
                if (b < 0x20 || b > 0x7E || b == '&' || b == ']') {
                    clean = false;
                    break;
                }
            }
            if (clean) {
                @SuppressWarnings("deprecation")
                String s = new String(buf, 0, start, len);
                return s;
            }
            return readTextContentSlow(start, len);
        }

        StringBuilder sb = new StringBuilder();
        appendUnescaped(sb, start, pos - start);

        while (pos < limit && buf[pos] == '<' && pos + 1 < limit && buf[pos + 1] == '!') {
            if (pos + 8 < limit && buf[pos + 2] == '['
                    && buf[pos + 3] == 'C'
                    && buf[pos + 4] == 'D'
                    && buf[pos + 5] == 'A'
                    && buf[pos + 6] == 'T'
                    && buf[pos + 7] == 'A'
                    && buf[pos + 8] == '[') {
                pos += 9; // skip <![CDATA[
                int cdataStart = pos;
                while (pos + 2 < limit) {
                    if (buf[pos] == ']' && buf[pos + 1] == ']' && buf[pos + 2] == '>') {
                        sb.append(new String(buf, cdataStart, pos - cdataStart, StandardCharsets.UTF_8));
                        pos += 3; // skip ]]>
                        break;
                    }
                    pos++;
                }
            } else {
                break;
            }
            start = pos;
            while (pos < limit && buf[pos] != '<') {
                pos++;
            }
            if (pos > start) {
                appendUnescaped(sb, start, pos - start);
            }
        }

        return sb.toString();
    }

    private String readTextContentSlow(int start, int len) {
        int end = start + len;
        boolean needsUnescape = false;
        boolean needsCrNormalization = false;
        for (int i = start; i < end; i++) {
            byte b = buf[i];
            if (b == '&') {
                needsUnescape = true;
                break;
            }
            if (b == '\r') {
                needsCrNormalization = true;
            } else if ((b & 0xFF) < 0x20 && b != '\t' && b != '\n') {
                throw new SerializationException(
                        "Invalid XML character U+" + String.format("%04X", b & 0xFF)
                                + " at byte offset " + (i - start));
            } else if ((b & 0xC0) == 0x80) {
                if (i == start || (buf[i - 1] & 0x80) == 0) {
                    throw new SerializationException("Invalid UTF-8 encoding at byte offset " + (i - start));
                }
            }
            if (b == ']' && i + 2 < end && buf[i + 1] == ']' && buf[i + 2] == '>') {
                throw new SerializationException("The sequence ']]>' is not allowed in text content");
            }
        }
        if (needsUnescape) {
            return unescapeXml(start, len);
        }
        if (needsCrNormalization) {
            return normalizeCr(start, len);
        }
        return new String(buf, start, len, StandardCharsets.UTF_8);
    }

    /**
     * Fused read: scans text content, constructs String, and verifies/consumes the end tag
     * in a single pass. Avoids separate readTextContent + consumeEndElement method calls.
     */
    private String readStringAndConsumeEndTag() {
        if (selfClosing) {
            selfClosing = false;
            endNameStart = nameStart;
            endNameLen = nameLen;
            return "";
        }
        int start = pos;
        while (pos < limit && buf[pos] != '<') {
            pos++;
        }
        int textEnd = pos;
        int textLen = textEnd - start;

        int expectedStart = nameStart;
        int expectedLen = nameLen;
        if (pos + 1 < limit && buf[pos] == '<' && buf[pos + 1] == '/') {
            int tagNamePos = pos + 2;
            if (tagNamePos + expectedLen < limit
                    && Arrays.equals(buf,
                            tagNamePos,
                            tagNamePos + expectedLen,
                            buf,
                            expectedStart,
                            expectedStart + expectedLen)) {
                int p = tagNamePos + expectedLen;
                while (p < limit && buf[p] != '>')
                    p++;
                if (p < limit) {
                    pos = p + 1;
                    if (textLen == 0)
                        return "";
                    boolean clean = true;
                    for (int i = start; i < textEnd; i++) {
                        int b = buf[i] & 0xFF;
                        if (b < 0x20 || b > 0x7E || b == '&' || b == ']') {
                            clean = false;
                            break;
                        }
                    }
                    if (clean) {
                        @SuppressWarnings("deprecation")
                        String s = new String(buf, 0, start, textLen);
                        return s;
                    }
                    return readTextContentSlow(start, textLen);
                }
            }
        }
        pos = start;
        String result = readTextContent();
        consumeEndElement();
        return result;
    }

    private void appendUnescaped(StringBuilder sb, int start, int len) {
        int end = start + len;
        int i = start;
        while (i < end) {
            byte b = buf[i];
            if (b == '&') {
                sb.append(unescapeXml(start, len));
                return;
            }
            i++;
        }
        sb.append(new String(buf, start, len, StandardCharsets.UTF_8));
    }

    private String unescapeXml(int start, int len) {
        int end = start + len;
        StringBuilder sb = new StringBuilder(len);
        int i = start;
        while (i < end) {
            byte b = buf[i];
            if (b == '&') {
                i++;
                int entityStart = i;
                while (i < end && buf[i] != ';') {
                    i++;
                }
                if (i >= end) {
                    throw new SerializationException("Unterminated entity reference");
                }
                int entityLen = i - entityStart;
                i++; // skip ';'

                if (entityLen == 2 && buf[entityStart] == 'l' && buf[entityStart + 1] == 't') {
                    sb.append('<');
                } else if (entityLen == 2 && buf[entityStart] == 'g' && buf[entityStart + 1] == 't') {
                    sb.append('>');
                } else if (entityLen == 3 && buf[entityStart] == 'a'
                        && buf[entityStart + 1] == 'm'
                        && buf[entityStart + 2] == 'p') {
                    sb.append('&');
                } else if (entityLen == 4 && buf[entityStart] == 'a'
                        && buf[entityStart + 1] == 'p'
                        && buf[entityStart + 2] == 'o'
                        && buf[entityStart + 3] == 's') {
                    sb.append('\'');
                } else if (entityLen == 4 && buf[entityStart] == 'q'
                        && buf[entityStart + 1] == 'u'
                        && buf[entityStart + 2] == 'o'
                        && buf[entityStart + 3] == 't') {
                    sb.append('"');
                } else if (buf[entityStart] == '#') {
                    int codePoint;
                    if (entityLen > 1 && buf[entityStart + 1] == 'x') {
                        codePoint = 0;
                        for (int j = entityStart + 2; j < entityStart + entityLen; j++) {
                            codePoint = codePoint * 16 + hexDigit(buf[j]);
                        }
                    } else {
                        codePoint = 0;
                        for (int j = entityStart + 1; j < entityStart + entityLen; j++) {
                            codePoint = codePoint * 10 + (buf[j] - '0');
                        }
                    }
                    if (!isValidXmlChar(codePoint)) {
                        throw new SerializationException(
                                "Invalid XML character reference &#"
                                        + (entityLen > 1 && buf[entityStart + 1] == 'x' ? "x" : "")
                                        + Integer.toString(codePoint,
                                                entityLen > 1 && buf[entityStart + 1] == 'x' ? 16 : 10)
                                        + ";");
                    }
                    sb.appendCodePoint(codePoint);
                } else {
                    throw new SerializationException(
                            "Undeclared entity reference '&"
                                    + new String(buf, entityStart, entityLen, StandardCharsets.UTF_8) + ";'");
                }
            } else {
                if (b == '\r') {
                    sb.append('\n');
                    i++;
                    if (i < end && buf[i] == '\n') {
                        i++; // \r\n -> \n
                    }
                } else if ((b & 0x80) == 0) {
                    sb.append((char) b);
                    i++;
                } else {
                    int remaining = end - i;
                    int seqLen;
                    if ((b & 0xE0) == 0xC0) {
                        seqLen = 2;
                    } else if ((b & 0xF0) == 0xE0) {
                        seqLen = 3;
                    } else {
                        seqLen = 4;
                    }
                    seqLen = Math.min(seqLen, remaining);
                    sb.append(new String(buf, i, seqLen, StandardCharsets.UTF_8));
                    i += seqLen;
                }
            }
        }
        return sb.toString();
    }

    private String normalizeCr(int start, int len) {
        int end = start + len;
        StringBuilder sb = new StringBuilder(len);
        int i = start;
        while (i < end) {
            byte b = buf[i];
            if (b == '\r') {
                sb.append('\n');
                i++;
                if (i < end && buf[i] == '\n') {
                    i++; // \r\n -> \n (skip the \n)
                }
            } else if ((b & 0x80) == 0) {
                sb.append((char) b);
                i++;
            } else {
                int seqLen;
                if ((b & 0xE0) == 0xC0) {
                    seqLen = 2;
                } else if ((b & 0xF0) == 0xE0) {
                    seqLen = 3;
                } else {
                    seqLen = 4;
                }
                seqLen = Math.min(seqLen, end - i);
                sb.append(new String(buf, i, seqLen, StandardCharsets.UTF_8));
                i += seqLen;
            }
        }
        return sb.toString();
    }

    private static boolean isValidXmlChar(int cp) {
        return cp == 0x9 || cp == 0xA
                || cp == 0xD
                || (cp >= 0x20 && cp <= 0xD7FF)
                || (cp >= 0xE000 && cp <= 0xFFFD)
                || (cp >= 0x10000 && cp <= 0x10FFFF);
    }

    private static int hexDigit(byte b) {
        if (b >= '0' && b <= '9') {
            return b - '0';
        }
        if (b >= 'a' && b <= 'f') {
            return b - 'a' + 10;
        }
        if (b >= 'A' && b <= 'F') {
            return b - 'A' + 10;
        }
        return 0;
    }

    private boolean nameEquals(byte[] expected) {
        if (nameLen != expected.length) {
            return false;
        }
        return Arrays.equals(buf, nameStart, nameStart + nameLen, expected, 0, expected.length);
    }

    private String elementName() {
        return new String(buf, nameStart, nameLen, StandardCharsets.UTF_8);
    }

    private String getAttributeValue(String localName) {
        byte[] nameBytes = localName.getBytes(StandardCharsets.UTF_8);
        return getAttributeValueByBytes(nameBytes);
    }

    private String getAttributeValueByBytes(byte[] nameBytes) {
        for (int i = 0; i < attrCount; i++) {
            if (attrNameLens[i] == nameBytes.length
                    && Arrays.equals(buf,
                            attrNameStarts[i],
                            attrNameStarts[i] + attrNameLens[i],
                            nameBytes,
                            0,
                            nameBytes.length)) {
                int vStart = attrValueStarts[i];
                int vLen = attrValueLens[i];
                for (int j = vStart; j < vStart + vLen; j++) {
                    if (buf[j] == '&') {
                        return unescapeXml(vStart, vLen);
                    }
                }
                return new String(buf, vStart, vLen, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static final byte[] ERROR_RESPONSE_BYTES = "ErrorResponse".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ERROR_BYTES = "Error".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RESPONSE_BYTES = "Response".getBytes(StandardCharsets.UTF_8);

    private void enter(Schema schema) {
        if (!isTopLevel) {
            return;
        }

        if (!wrapperElements.isEmpty()) {
            skipWrapperElements();
            return;
        }

        if (!nextStartElement()) {
            throw new SerializationException("Expected start element but found end of document");
        }

        String expected;
        var trait = schema.getTrait(TraitKey.XML_NAME_TRAIT);
        if (trait != null) {
            expected = trait.getValue();
        } else if (schema.isMember()) {
            expected = schema.memberTarget().id().getName();
        } else {
            expected = schema.id().getName();
        }

        if (nameEquals(ERROR_RESPONSE_BYTES)) {
            if (!nextStartElement()) {
                throw new SerializationException("Expected <Error> inside <ErrorResponse>");
            }
            errorWrapperDepth = 1;
            return;
        } else if (nameEquals(RESPONSE_BYTES)) {
            nextStartElement();
            if (!nextStartElement()) {
                throw new SerializationException("Expected <Error> inside <Errors>");
            }
            errorWrapperDepth = 2;
            return;
        }

        if (!nameEqualsString(expected)) {
            if (!nameEquals(ERROR_BYTES)) {
                throw new SerializationException(
                        "Expected XML element named '" + expected + "', found '" + elementName() + "'");
            }
        }
    }

    private boolean nameEqualsString(String expected) {
        if (nameLen != expected.length()) {
            return false;
        }
        for (int i = 0; i < nameLen; i++) {
            if ((buf[nameStart + i] & 0xFF) != expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private void skipWrapperElements() {
        for (String wrapperName : wrapperElements) {
            if (!nextStartElement()) {
                return;
            }
            String name = elementName();
            if (!name.equals(wrapperName)) {
                throw new SerializationException(
                        "Expected wrapper element '" + wrapperName + "', found '" + name + "'");
            }
        }
    }

    public String parseErrorCodeName() {
        if (!nextStartElement()) {
            throw new SerializationException(
                    "Expected element <ErrorResponse>, <Response>, or <Error> for XML error response");
        }
        String name = elementName();
        if (!name.equals("ErrorResponse") && !name.equals("Error") && !name.equals("Response")) {
            throw new SerializationException(
                    "Expected element <ErrorResponse>, <Response>, or <Error> for XML error response");
        }

        if (name.equals("ErrorResponse")) {
            if (!nextStartElement()) {
                throw new SerializationException("Expected <Error> element inside <ErrorResponse>");
            }
            if (!elementName().equals("Error")) {
                throw new SerializationException("Expected <Error> element inside <ErrorResponse>");
            }
        } else if (name.equals("Response")) {
            if (!nextStartElement()) {
                throw new SerializationException("Expected <Errors> element inside <Response>");
            }
            if (!elementName().equals("Errors")) {
                throw new SerializationException("Expected <Errors> element inside <Response>");
            }
            if (!nextStartElement()) {
                throw new SerializationException("Expected <Error> element inside <Errors>");
            }
            if (!elementName().equals("Error")) {
                throw new SerializationException("Expected <Error> element inside <Errors>");
            }
        }

        while (nextStartElement()) {
            if (elementName().equals("Code")) {
                String code = readTextContent();
                consumeEndElement();
                return code;
            }
            skipElement();
        }
        throw new SerializationException("Expected <Code> element inside <Error>");
    }

    private void validateNoTrailingContent() {
        if (!isTopLevel) {
            return;
        }
        int depth = wrapperElements.size() + errorWrapperDepth;
        if (depth > 0) {
            while (depth > 0 && pos < limit) {
                int event = next();
                if (event == END_ELEMENT) {
                    depth--;
                } else if (event == START_ELEMENT) {
                    skipElement();
                } else if (event == EOF) {
                    break;
                }
            }
        }
        skipWhitespace();
        if (pos < limit) {
            throw new SerializationException("Content is not allowed after root element closes");
        }
    }

    @Override
    public boolean readBoolean(Schema schema) {
        enter(schema);
        readTextSpan();
        boolean result = parseBooleanFromSpan();
        consumeEndElement();
        validateNoTrailingContent();
        return result;
    }

    @Override
    public byte readByte(Schema schema) {
        enter(schema);
        readTextSpan();
        int value = parseIntFromSpan();
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
            throw new SerializationException("Value out of range for byte: " + value);
        }
        consumeEndElement();
        validateNoTrailingContent();
        return (byte) value;
    }

    @Override
    public short readShort(Schema schema) {
        enter(schema);
        readTextSpan();
        int value = parseIntFromSpan();
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new SerializationException("Value out of range for short: " + value);
        }
        consumeEndElement();
        validateNoTrailingContent();
        return (short) value;
    }

    @Override
    public int readInteger(Schema schema) {
        enter(schema);
        readTextSpan();
        int result = parseIntFromSpan();
        consumeEndElement();
        validateNoTrailingContent();
        return result;
    }

    @Override
    public long readLong(Schema schema) {
        enter(schema);
        readTextSpan();
        long result = parseLongFromSpan();
        consumeEndElement();
        validateNoTrailingContent();
        return result;
    }

    @Override
    public float readFloat(Schema schema) {
        enter(schema);
        readTextSpan();
        float result = parseFloatFromSpan();
        consumeEndElement();
        validateNoTrailingContent();
        return result;
    }

    @Override
    public double readDouble(Schema schema) {
        enter(schema);
        readTextSpan();
        double result = parseDoubleFromSpan();
        consumeEndElement();
        validateNoTrailingContent();
        return result;
    }

    @Override
    public BigInteger readBigInteger(Schema schema) {
        enter(schema);
        readTextSpan();
        BigInteger result = parseBigIntegerFromSpan();
        consumeEndElement();
        validateNoTrailingContent();
        return result;
    }

    @Override
    public BigDecimal readBigDecimal(Schema schema) {
        enter(schema);
        readTextSpan();
        BigDecimal result = parseBigDecimalFromSpan();
        consumeEndElement();
        validateNoTrailingContent();
        return result;
    }

    @Override
    public String readString(Schema schema) {
        enter(schema);
        String result = readTextContent();
        consumeEndElement();
        validateNoTrailingContent();
        return result;
    }

    @Override
    public ByteBuffer readBlob(Schema schema) {
        enter(schema);
        readTextSpan();
        ByteBuffer result = parseBlobFromSpan();
        consumeEndElement();
        validateNoTrailingContent();
        return result;
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        enter(schema);
        readTextSpan();
        Instant result = parseTimestampFromSpan(schema);
        consumeEndElement();
        validateNoTrailingContent();
        return result;
    }

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
        enter(schema);
        readStructContent(schema, state, consumer);
        validateNoTrailingContent();
    }

    @Override
    public <T> void readList(Schema schema, T state, ListMemberConsumer<T> consumer) {
        enter(schema);
        readListContent(schema, state, consumer);
        validateNoTrailingContent();
    }

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer) {
        enter(schema);
        readMapContent(schema, state, consumer);
        validateNoTrailingContent();
    }

    @Override
    public boolean isNull() {
        int saved = pos;
        while (saved < limit && buf[saved] != '<' && isWhitespace(buf[saved])) {
            saved++;
        }
        return saved < limit && buf[saved] == '<';
    }

    @Override
    public <T> T readNull() {
        return null;
    }

    @Override
    public Document readDocument() {
        return null;
    }

    private <T> void readStructContent(Schema schema, T state, StructMemberConsumer<T> consumer) {
        int containerNameStart = nameStart;
        int containerNameLen = nameLen;
        var decoder = xmlInfo.getStructInfo(schema);

        if (!decoder.attributes.isEmpty() && attrCount > 0) {
            var structExt = decoder.schema.getExtension(XmlSchemaExtensions.KEY);
            byte[][] nameTable = (structExt instanceof XmlSchemaExtensions.StructExtension se)
                    ? se.nameTable()
                    : null;

            for (var entry : decoder.attributes.entrySet()) {
                Schema attributeSchema = entry.getValue();
                int idx = attributeSchema.memberIndex();
                String attrValue;
                if (nameTable != null && idx >= 0 && idx < nameTable.length && nameTable[idx] != null) {
                    attrValue = getAttributeValueByBytes(nameTable[idx]);
                } else {
                    String attributeName = entry.getKey();
                    int colonIdx = attributeName.indexOf(':');
                    String lookupName = colonIdx >= 0 ? attributeName.substring(colonIdx + 1) : attributeName;
                    attrValue = getAttributeValue(lookupName);
                }
                if (attrValue != null) {
                    consumer.accept(state, attributeSchema, new AttributeDeserializer(attrValue));
                }
            }
        }

        XmlMemberLookup lookup = decoder.memberLookup;
        lookupHint[0] = 0;

        if (decoder.hasFlattened) {
            readStructContentWithFlattened(state, consumer, lookup, containerNameStart, containerNameLen);
        } else if (lookup != null) {
            while (nextStartElement()) {
                Schema memberSchema = lookup.findMember(buf, nameStart, nameLen, lookupHint);
                if (memberSchema != null) {
                    memberDeserializer.consumed = false;
                    consumer.accept(state, memberSchema, memberDeserializer);
                    if (!memberDeserializer.consumed) {
                        skipElement();
                    }
                } else {
                    consumer.unknownMember(state, elementName());
                    skipElement();
                }
            }
            validateContainerEndTag(containerNameStart, containerNameLen);
        } else {
            while (nextStartElement()) {
                skipElement();
            }
            validateContainerEndTag(containerNameStart, containerNameLen);
        }
    }

    private <T> void readStructContentWithFlattened(
            T state,
            StructMemberConsumer<T> consumer,
            XmlMemberLookup lookup,
            int containerNameStart,
            int containerNameLen
    ) {
        Map<Schema, List<int[]>> flattenedSpans = new LinkedHashMap<>();
        while (nextStartElement()) {
            Schema memberSchema = lookup.findMember(buf, nameStart, nameLen, lookupHint);
            if (memberSchema != null) {
                if (memberSchema.hasTrait(TraitKey.XML_FLATTENED_TRAIT)) {
                    int spanStart = nameStart - 1;
                    skipElement();
                    int spanEnd = pos;
                    flattenedSpans.computeIfAbsent(memberSchema, k -> new ArrayList<>())
                            .add(new int[] {spanStart, spanEnd});
                } else {
                    memberDeserializer.consumed = false;
                    consumer.accept(state, memberSchema, memberDeserializer);
                    if (!memberDeserializer.consumed) {
                        skipElement();
                    }
                }
            } else {
                consumer.unknownMember(state, elementName());
                skipElement();
            }
        }
        validateContainerEndTag(containerNameStart, containerNameLen);
        for (var entry : flattenedSpans.entrySet()) {
            consumer.accept(state, entry.getKey(), new FlattenedReplayDeserializer(entry.getValue()));
        }
    }

    private <T> void readListContent(Schema schema, T state, ListMemberConsumer<T> consumer) {
        int containerNameStart = nameStart;
        int containerNameLen = nameLen;
        var ext = schema.getExtension(XmlSchemaExtensions.KEY);
        byte[] expectedMemberName;
        if (ext instanceof XmlSchemaExtensions.ListExtension le) {
            expectedMemberName = le.memberNameBytes();
        } else {
            var info = xmlInfo.getListInfo(schema);
            expectedMemberName = info.memberName.getBytes(StandardCharsets.UTF_8);
        }

        MemberDeserializer itemDeser = new MemberDeserializer();
        while (nextStartElement()) {
            if (!nameEquals(expectedMemberName)) {
                throw new SerializationException(
                        "Expected list item '" + new String(expectedMemberName, StandardCharsets.UTF_8)
                                + "' but found '" + elementName() + "'");
            }
            itemDeser.consumed = false;
            consumer.accept(state, itemDeser);
            if (!itemDeser.consumed) {
                skipElement();
            }
        }

        validateContainerEndTag(containerNameStart, containerNameLen);
    }

    private <T> void readMapContent(Schema schema, T state, MapMemberConsumer<String, T> consumer) {
        int containerNameStart = nameStart;
        int containerNameLen = nameLen;
        byte[] entryNameBytes;
        byte[] keyNameBytes;
        byte[] valueNameBytes;
        boolean flattened;
        var ext = schema.getExtension(XmlSchemaExtensions.KEY);
        if (ext instanceof XmlSchemaExtensions.MapExtension me) {
            entryNameBytes = me.entryNameBytes();
            keyNameBytes = me.keyNameBytes();
            valueNameBytes = me.valueNameBytes();
            var decoder = xmlInfo.getMapInfo(schema);
            flattened = decoder.flattened;
        } else {
            var decoder = xmlInfo.getMapInfo(schema);
            entryNameBytes = decoder.entryName.getBytes(StandardCharsets.UTF_8);
            keyNameBytes = decoder.keyName.getBytes(StandardCharsets.UTF_8);
            valueNameBytes = decoder.valueName.getBytes(StandardCharsets.UTF_8);
            flattened = decoder.flattened;
        }

        while (nextStartElement()) {
            if (!nameEquals(entryNameBytes)) {
                if (!flattened) {
                    break;
                } else {
                    throw new SerializationException("Unexpected element in map: " + elementName());
                }
            }

            if (!nextStartElement()) {
                throw new SerializationException("Expected map key, but map unexpectedly closed");
            }
            if (!nameEquals(keyNameBytes)) {
                throw new SerializationException("Expected map key but found '" + elementName() + "'");
            }
            String key = readTextContent();
            consumeEndElement(); // consume </key>

            if (!nextStartElement()) {
                throw new SerializationException("Expected map value, but map unexpectedly closed");
            }
            if (!nameEquals(valueNameBytes)) {
                throw new SerializationException("Expected map value but found '" + elementName() + "'");
            }
            MemberDeserializer valueDeser = new MemberDeserializer();
            consumer.accept(state, key, valueDeser);
            if (!valueDeser.consumed) {
                skipElement();
            }
            consumeEndElement(entryNameBytes);
        }

        validateContainerEndTag(containerNameStart, containerNameLen);
    }

    private boolean parseBooleanFromSpan() {
        if (spanHasCdataFallback()) {
            return "true".equals(textFallback);
        }
        int len = textSpanEnd - textSpanStart;
        if (len == 4 && buf[textSpanStart] == 't'
                && buf[textSpanStart + 1] == 'r'
                && buf[textSpanStart + 2] == 'u'
                && buf[textSpanStart + 3] == 'e') {
            return true;
        }
        if (len == 5 && buf[textSpanStart] == 'f'
                && buf[textSpanStart + 1] == 'a'
                && buf[textSpanStart + 2] == 'l'
                && buf[textSpanStart + 3] == 's'
                && buf[textSpanStart + 4] == 'e') {
            return false;
        }
        throw new SerializationException(
                "Expected boolean 'true' or 'false', found '"
                        + new String(buf, textSpanStart, len, StandardCharsets.UTF_8) + "'");
    }

    private static final byte[] INFINITY_BYTES = "Infinity".getBytes(StandardCharsets.UTF_8);

    private float parseFloatFromSpan() {
        if (spanHasCdataFallback()) {
            return Float.parseFloat(textFallback);
        }
        int len = textSpanEnd - textSpanStart;
        if (len == 0) {
            throw new SerializationException("Empty float value");
        }
        if (len == 3 && buf[textSpanStart] == 'N' && buf[textSpanStart + 1] == 'a' && buf[textSpanStart + 2] == 'N') {
            return Float.NaN;
        } else if (len == 8 && Arrays.equals(buf, textSpanStart, textSpanEnd, INFINITY_BYTES, 0, 8)) {
            return Float.POSITIVE_INFINITY;
        } else if (len == 9 && buf[textSpanStart] == '-'
                && Arrays.equals(buf, textSpanStart + 1, textSpanEnd, INFINITY_BYTES, 0, 8)) {
            return Float.NEGATIVE_INFINITY;
        }
        return NumberCodec.parseFloat(buf, textSpanStart, len);
    }

    private double parseDoubleFromSpan() {
        if (spanHasCdataFallback()) {
            return Double.parseDouble(textFallback);
        }
        int len = textSpanEnd - textSpanStart;
        if (len == 0) {
            throw new SerializationException("Empty double value");
        }
        if (len == 3 && buf[textSpanStart] == 'N' && buf[textSpanStart + 1] == 'a' && buf[textSpanStart + 2] == 'N') {
            return Double.NaN;
        } else if (len == 8 && Arrays.equals(buf, textSpanStart, textSpanEnd, INFINITY_BYTES, 0, 8)) {
            return Double.POSITIVE_INFINITY;
        } else if (len == 9 && buf[textSpanStart] == '-'
                && Arrays.equals(buf, textSpanStart + 1, textSpanEnd, INFINITY_BYTES, 0, 8)) {
            return Double.NEGATIVE_INFINITY;
        }
        return NumberCodec.parseDouble(buf, textSpanStart, len);
    }

    private Instant parseTimestampFromSpan(Schema schema) {
        if (spanHasCdataFallback()) {
            try {
                return TimestampFormatter.of(schema, TimestampFormatTrait.Format.DATE_TIME)
                        .readFromString(textFallback, false);
            } catch (TimestampFormatter.TimestampSyntaxError | DateTimeParseException e) {
                throw new SerializationException("Failed to read timestamp: " + e.getMessage(), e);
            }
        }
        try {
            Instant result = TimestampCodec.parseIso8601(buf, textSpanStart, textSpanEnd);
            if (result != null) {
                return result;
            }
            String value = new String(buf, textSpanStart, textSpanEnd - textSpanStart, StandardCharsets.UTF_8);
            return TimestampFormatter.of(schema, TimestampFormatTrait.Format.DATE_TIME).readFromString(value, false);
        } catch (TimestampFormatter.TimestampSyntaxError | DateTimeParseException e) {
            throw new SerializationException("Failed to read timestamp: " + e.getMessage(), e);
        }
    }

    private int parseIntFromSpan() {
        if (spanHasCdataFallback()) {
            return Integer.parseInt(textFallback.trim());
        }
        return NumberCodec.parseInt(buf, textSpanStart, textSpanEnd - textSpanStart);
    }

    private long parseLongFromSpan() {
        if (spanHasCdataFallback()) {
            return Long.parseLong(textFallback.trim());
        }
        return NumberCodec.parseLong(buf, textSpanStart, textSpanEnd - textSpanStart);
    }

    private BigInteger parseBigIntegerFromSpan() {
        if (spanHasCdataFallback()) {
            return new BigInteger(textFallback.trim());
        }
        return new BigInteger(new String(buf, textSpanStart, textSpanEnd - textSpanStart, StandardCharsets.US_ASCII));
    }

    private BigDecimal parseBigDecimalFromSpan() {
        if (spanHasCdataFallback()) {
            return new BigDecimal(textFallback.trim());
        }
        return new BigDecimal(new String(buf, textSpanStart, textSpanEnd - textSpanStart, StandardCharsets.US_ASCII));
    }

    private ByteBuffer parseBlobFromSpan() {
        if (spanHasCdataFallback()) {
            return ByteBuffer.wrap(BASE64_DECODER.decode(textFallback.trim()));
        }
        return BASE64_DECODER.decode(ByteBuffer.wrap(buf, textSpanStart, textSpanEnd - textSpanStart));
    }

    private final class MemberDeserializer implements ShapeDeserializer {
        boolean consumed;

        @Override
        public boolean readBoolean(Schema schema) {
            consumed = true;
            readTextSpan();
            boolean result = parseBooleanFromSpan();
            consumeEndElement();
            return result;
        }

        @Override
        public byte readByte(Schema schema) {
            consumed = true;
            readTextSpan();
            int value = parseIntFromSpan();
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                throw new SerializationException("Value out of range for byte: " + value);
            }
            consumeEndElement();
            return (byte) value;
        }

        @Override
        public short readShort(Schema schema) {
            consumed = true;
            readTextSpan();
            int value = parseIntFromSpan();
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                throw new SerializationException("Value out of range for short: " + value);
            }
            consumeEndElement();
            return (short) value;
        }

        @Override
        public int readInteger(Schema schema) {
            consumed = true;
            readTextSpan();
            int result = parseIntFromSpan();
            consumeEndElement();
            return result;
        }

        @Override
        public long readLong(Schema schema) {
            consumed = true;
            readTextSpan();
            long result = parseLongFromSpan();
            consumeEndElement();
            return result;
        }

        @Override
        public float readFloat(Schema schema) {
            consumed = true;
            readTextSpan();
            float result = parseFloatFromSpan();
            consumeEndElement();
            return result;
        }

        @Override
        public double readDouble(Schema schema) {
            consumed = true;
            readTextSpan();
            double result = parseDoubleFromSpan();
            consumeEndElement();
            return result;
        }

        @Override
        public BigInteger readBigInteger(Schema schema) {
            consumed = true;
            readTextSpan();
            BigInteger result = parseBigIntegerFromSpan();
            consumeEndElement();
            return result;
        }

        @Override
        public BigDecimal readBigDecimal(Schema schema) {
            consumed = true;
            readTextSpan();
            BigDecimal result = parseBigDecimalFromSpan();
            consumeEndElement();
            return result;
        }

        @Override
        public String readString(Schema schema) {
            consumed = true;
            return readStringAndConsumeEndTag();
        }

        @Override
        public ByteBuffer readBlob(Schema schema) {
            consumed = true;
            readTextSpan();
            ByteBuffer result = parseBlobFromSpan();
            consumeEndElement();
            return result;
        }

        @Override
        public Instant readTimestamp(Schema schema) {
            consumed = true;
            readTextSpan();
            Instant result = parseTimestampFromSpan(schema);
            consumeEndElement();
            return result;
        }

        @Override
        public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
            consumed = true;
            readStructContent(schema, state, consumer);
        }

        @Override
        public <T> void readList(Schema schema, T state, ListMemberConsumer<T> consumer) {
            consumed = true;
            readListContent(schema, state, consumer);
        }

        @Override
        public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer) {
            consumed = true;
            readMapContent(schema, state, consumer);
        }

        @Override
        public boolean isNull() {
            if (selfClosing) {
                return true;
            }
            int saved = pos;
            while (saved < limit && isWhitespace(buf[saved])) {
                saved++;
            }
            return saved + 1 < limit && buf[saved] == '<' && buf[saved + 1] == '/';
        }

        @Override
        public <T> T readNull() {
            consumed = true;
            skipElement();
            return null;
        }

        @Override
        public Document readDocument() {
            consumed = true;
            skipElement();
            return null;
        }
    }

    private static final class AttributeDeserializer extends SpecificShapeDeserializer {

        private final String value;

        AttributeDeserializer(String value) {
            this.value = value;
        }

        @Override
        public boolean readBoolean(Schema schema) {
            return switch (value) {
                case "true" -> true;
                case "false" -> false;
                default -> throw new SerializationException(
                        "Expected boolean 'true' or 'false', found '" + value + "'");
            };
        }

        @Override
        public String readString(Schema schema) {
            return value;
        }

        @Override
        public Instant readTimestamp(Schema schema) {
            try {
                return TimestampFormatter.of(schema, TimestampFormatTrait.Format.DATE_TIME)
                        .readFromString(value, false);
            } catch (TimestampFormatter.TimestampSyntaxError e) {
                throw new SerializationException("Failed to read timestamp: " + e.getMessage(), e);
            }
        }

        @Override
        public byte readByte(Schema schema) {
            return Byte.parseByte(value);
        }

        @Override
        public short readShort(Schema schema) {
            return Short.parseShort(value);
        }

        @Override
        public int readInteger(Schema schema) {
            return Integer.parseInt(value);
        }

        @Override
        public long readLong(Schema schema) {
            return Long.parseLong(value);
        }

        @Override
        public float readFloat(Schema schema) {
            return Float.parseFloat(value);
        }

        @Override
        public double readDouble(Schema schema) {
            return Double.parseDouble(value);
        }

        @Override
        public BigDecimal readBigDecimal(Schema schema) {
            return new BigDecimal(value);
        }

        @Override
        public BigInteger readBigInteger(Schema schema) {
            return new BigInteger(value);
        }
    }

    private final class FlattenedReplayDeserializer implements ShapeDeserializer {

        private final List<int[]> spans;

        FlattenedReplayDeserializer(List<int[]> spans) {
            this.spans = spans;
        }

        @Override
        public boolean readBoolean(Schema schema) {
            throw new SerializationException("Flattened replay does not support scalar reads");
        }

        @Override
        public ByteBuffer readBlob(Schema schema) {
            throw new SerializationException("Flattened replay does not support scalar reads");
        }

        @Override
        public byte readByte(Schema schema) {
            throw new SerializationException("Flattened replay does not support scalar reads");
        }

        @Override
        public short readShort(Schema schema) {
            throw new SerializationException("Flattened replay does not support scalar reads");
        }

        @Override
        public int readInteger(Schema schema) {
            throw new SerializationException("Flattened replay does not support scalar reads");
        }

        @Override
        public long readLong(Schema schema) {
            throw new SerializationException("Flattened replay does not support scalar reads");
        }

        @Override
        public float readFloat(Schema schema) {
            throw new SerializationException("Flattened replay does not support scalar reads");
        }

        @Override
        public double readDouble(Schema schema) {
            throw new SerializationException("Flattened replay does not support scalar reads");
        }

        @Override
        public BigInteger readBigInteger(Schema schema) {
            throw new SerializationException("Flattened replay does not support scalar reads");
        }

        @Override
        public BigDecimal readBigDecimal(Schema schema) {
            throw new SerializationException("Flattened replay does not support scalar reads");
        }

        @Override
        public String readString(Schema schema) {
            throw new SerializationException("Flattened replay does not support scalar reads");
        }

        @Override
        public Instant readTimestamp(Schema schema) {
            throw new SerializationException("Flattened replay does not support scalar reads");
        }

        @Override
        public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
            throw new SerializationException("Flattened replay does not support struct reads");
        }

        @Override
        public <T> void readList(Schema schema, T state, ListMemberConsumer<T> consumer) {
            for (int[] span : spans) {
                int spanStart = span[0];
                int spanLen = span[1] - span[0];
                SmithyXmlDeserializer sub = new SmithyXmlDeserializer(
                        buf,
                        spanStart,
                        spanLen,
                        xmlInfo,
                        false,
                        List.of());
                if (sub.nextStartElement()) {
                    MemberDeserializer memberDeser = sub.new MemberDeserializer();
                    consumer.accept(state, memberDeser);
                }
            }
        }

        @Override
        public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer) {
            var decoder = xmlInfo.getMapInfo(schema);
            byte[] keyNameBytes = decoder.keyName.getBytes(StandardCharsets.UTF_8);
            byte[] valueNameBytes = decoder.valueName.getBytes(StandardCharsets.UTF_8);

            for (int[] span : spans) {
                int spanStart = span[0];
                int spanLen = span[1] - span[0];
                SmithyXmlDeserializer sub = new SmithyXmlDeserializer(
                        buf,
                        spanStart,
                        spanLen,
                        xmlInfo,
                        false,
                        List.of());
                if (sub.nextStartElement()) {
                    if (!sub.nextStartElement()) {
                        throw new SerializationException("Expected map key in flattened entry");
                    }
                    if (!sub.nameEquals(keyNameBytes)) {
                        throw new SerializationException("Expected map key '" + decoder.keyName + "'");
                    }
                    String key = sub.readTextContent();
                    sub.skipElement();

                    if (!sub.nextStartElement()) {
                        throw new SerializationException("Expected map value in flattened entry");
                    }
                    if (!sub.nameEquals(valueNameBytes)) {
                        throw new SerializationException("Expected map value '" + decoder.valueName + "'");
                    }
                    MemberDeserializer valueDeser = sub.new MemberDeserializer();
                    consumer.accept(state, key, valueDeser);
                }
            }
        }

        @Override
        public boolean isNull() {
            return spans.isEmpty();
        }

        @Override
        public Document readDocument() {
            return null;
        }
    }
}
