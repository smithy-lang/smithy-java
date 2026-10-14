/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.io.uri.SmithyUri;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.BooleanNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.NumberNode;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.rulesengine.language.evaluation.value.Value;
import software.amazon.smithy.rulesengine.language.syntax.Identifier;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.ParseUrl;

public final class EndpointUtils {

    private EndpointUtils() {}

    // "The type of the value MUST be either a string, boolean or an array of string."
    public static Object convertNode(Node value, boolean allowAllTypes) {
        if (value instanceof StringNode s) {
            return s.getValue();
        } else if (value instanceof BooleanNode b) {
            return b.getValue();
        } else if (value instanceof ArrayNode a) {
            List<Object> result = new ArrayList<>(a.size());
            for (var e : a.getElements()) {
                result.add(convertNode(e, allowAllTypes));
            }
            return result;
        } else if (allowAllTypes) {
            if (value instanceof NumberNode n) {
                return n.getValue();
            } else if (value instanceof ObjectNode o) {
                var result = new HashMap<String, Object>(o.size());
                for (var e : o.getStringMap().entrySet()) {
                    result.put(e.getKey(), convertNode(e.getValue(), allowAllTypes));
                }
                return result;
            } else if (value.isNullNode()) {
                return null;
            }
        }

        throw new RulesEvaluationError("Unsupported endpoint ruleset parameter: " + value);
    }

    public static Object convertNode(Node value) {
        return convertNode(value, false);
    }

    static Value convertToValue(Object o) {
        return switch (o) {
            case null -> Value.emptyValue();
            case String s -> Value.stringValue(s);
            case Number n -> Value.integerValue(n.intValue());
            case Boolean b -> Value.booleanValue(b);
            case List<?> l -> {
                List<Value> valueList = new ArrayList<>(l.size());
                for (var entry : l) {
                    valueList.add(convertToValue(entry));
                }
                yield Value.arrayValue(valueList);
            }
            case Map<?, ?> m -> {
                Map<Identifier, Value> valueMap = new HashMap<>(m.size());
                for (var e : m.entrySet()) {
                    valueMap.put(Identifier.of(e.getKey().toString()), convertToValue(e.getValue()));
                }
                yield Value.recordValue(valueMap);
            }
            default -> throw new RulesEvaluationError("Unsupported value type: " + o);
        };
    }

    // Read big-endian unsigned short (2 bytes)
    static int bytesToShort(byte[] instructions, int offset) {
        return ((instructions[offset] & 0xFF) << 8) | (instructions[offset + 1] & 0xFF);
    }

    /**
     * Get a property from a {@link PropertyGetter}, {@link Map}, or {@link SmithyUri}, returning
     * {@code null} if not found.
     *
     * <p>Used to read endpoint properties without having to know which concrete representation
     * the rules-engine emitter produced — the {@code STRUCTN} fast path returns
     * {@link ArrayPropertyGetter}, the generic {@code MAPN} path returns a {@link Map}, and URL
     * subfields use {@link SmithyUri}.
     */
    public static Object getProperty(Object target, String propertyName) {
        return switch (target) {
            case PropertyGetter pg -> pg.getProperty(propertyName);
            case Map<?, ?> m -> m.get(propertyName);
            case SmithyUri u -> switch (propertyName) {
                case "scheme" -> u.getScheme();
                case "path" -> u.getPath();
                case "normalizedPath" -> ParseUrl.normalizePath(u.getPath());
                case "authority" -> u.getPort() >= 0 ? u.getHost() + ":" + u.getPort() : u.getHost();
                case "isIp" -> ParseUrl.isIpAddr(u.getHost());
                default -> null;
            };
            case null, default -> null;
        };
    }

    // Get a value by index from a list. Returns null if not a list or out of bounds.
    static Object getIndex(Object target, int index) {
        if (target instanceof List<?> l && index >= 0 && index < l.size()) {
            return l.get(index);
        }
        return null;
    }

    // Get a value by negative index from a list. Index is stored as positive (1 means -1, last element).
    static Object getNegativeIndex(Object target, int negIndex) {
        if (target instanceof List<?> l) {
            int actualIndex = l.size() - negIndex;
            if (actualIndex >= 0 && actualIndex < l.size()) {
                return l.get(actualIndex);
            }
        }
        return null;
    }

    // TODO: Remove when Substring.getSubstring in smithy is updated to only check the substring range for ASCII.
    // Fast substring that only validates ASCII within the actual substring range, not the entire string.
    static String getSubstring(String value, int startIndex, int stopIndex, boolean reverse) {
        if (value == null) {
            return null;
        }
        int len = value.length();
        if (startIndex >= stopIndex || len < stopIndex) {
            return null;
        }
        int actualStart, actualEnd;
        if (reverse) {
            actualStart = len - stopIndex;
            actualEnd = len - startIndex;
        } else {
            actualStart = startIndex;
            actualEnd = stopIndex;
        }
        for (int i = actualStart; i < actualEnd; i++) {
            if (value.charAt(i) > 127) {
                return null;
            }
        }
        return value.substring(actualStart, actualEnd);
    }

    /**
     * Host-label validation equivalent to {@code IsValidHostLabel.isValidHostLabel}, written as a flat
     * loop.
     *
     * <p>The library version routes every character through {@code String.charAt} into a separate
     * {@code isAlphanumeric} helper, which leaves C2 nothing to unroll; the decomposition benchmark puts
     * the scan at ~24ns for a 14-character label, which is far more than the comparison work implies.
     * This inlines the character classes and keeps the whole scan in one method so the loop can be
     * unrolled, and tests the digit and letter ranges as single unsigned comparisons instead of four
     * signed ones.
     *
     * <p>Behavior is identical and pinned by a differential test against the library implementation.
     *
     * @param value the label, which may be null.
     * @param allowDots true to accept a dot-separated sequence of labels.
     * @return true if valid.
     */
    static boolean isValidHostLabel(String value, boolean allowDots) {
        if (value == null) {
            return false;
        }
        int len = value.length();
        if (len == 0) {
            return false;
        }
        if (!allowDots) {
            return isValidLabel(value, 0, len);
        }
        // A trailing dot yields a final zero-length label, which isValidLabel rejects, matching the
        // library's behavior of running its segment loop through i == len.
        int start = 0;
        for (int i = 0; i <= len; i++) {
            if (i == len || value.charAt(i) == '.') {
                if (!isValidLabel(value, start, i)) {
                    return false;
                }
                start = i + 1;
            }
        }
        return true;
    }

    private static boolean isValidLabel(String value, int start, int end) {
        int len = end - start;
        if (len < 1 || len > 63) {
            return false;
        }
        byte[] bytes = AsciiBytes.latin1(value);
        if (bytes == null) {
            // Not Latin1 coded, so it holds a character no label may contain — but fall back rather
            // than assume, since a UTF16-coded string can still be all ASCII under -XX:-CompactStrings.
            return isValidLabelChars(value, start, end);
        }
        // The first character may not be a hyphen; the rest may.
        return AsciiBytes.isAlnum(bytes[start] & 0xFF)
                && AsciiBytes.allAlnumOrHyphen(bytes, start + 1, end);
    }

    /** Fallback scan for a string whose bytes are not directly reachable. */
    private static boolean isValidLabelChars(String value, int start, int end) {
        if (!AsciiBytes.isAlnum(value.charAt(start))) {
            return false;
        }
        for (int i = start + 1; i < end; i++) {
            char c = value.charAt(i);
            if (c != '-' && !AsciiBytes.isAlnum(c)) {
                return false;
            }
        }
        return true;
    }

    // Check if substring equals expected, returning false for null/short strings
    static boolean substringEquals(String value, int start, int end, boolean reverse, String expected) {
        if (value == null || expected == null) {
            return false;
        }
        int len = value.length();
        int actualStart, actualEnd;
        if (reverse) {
            actualStart = len - end;
            actualEnd = len - start;
        } else {
            actualStart = start;
            actualEnd = end;
        }
        if (actualStart < 0 || actualEnd > len || actualStart >= actualEnd) {
            return false;
        }
        return value.regionMatches(actualStart, expected, 0, actualEnd - actualStart);
    }

    // True when substring extraction would yield null and coalescing it with "" would yield "".
    static boolean substringIsEmpty(String value, int start, int end, boolean reverse) {
        if (value == null) {
            return true;
        }
        int length = value.length();
        if (start >= end || length < end) {
            return true;
        }
        int actualStart = reverse ? length - end : start;
        int actualEnd = reverse ? length - start : end;
        for (int i = actualStart; i < actualEnd; i++) {
            if (value.charAt(i) > 127) {
                return true;
            }
        }
        return false;
    }

    // Split and get element at index without allocating array
    static Object splitGet(String value, String delimiter, int index) {
        if (value == null || delimiter == null || delimiter.isEmpty()) {
            return null;
        }
        int delimLen = delimiter.length();

        if (index >= 0) {
            int start = 0;
            for (int i = 0; i < index; i++) {
                int next = value.indexOf(delimiter, start);
                if (next < 0) {
                    return null;
                }
                start = next + delimLen;
            }
            int end = value.indexOf(delimiter, start);
            return end < 0 ? value.substring(start) : value.substring(start, end);
        } else {
            int segmentsFromEnd = -index;
            int end = value.length();
            for (int i = 0; i < segmentsFromEnd - 1; i++) {
                int prev = value.lastIndexOf(delimiter, end - delimLen - 1);
                if (prev < 0) {
                    return null;
                }
                end = prev;
            }
            int start = value.lastIndexOf(delimiter, end - delimLen - 1);
            return start < 0 ? value.substring(0, end) : value.substring(start + delimLen, end);
        }
    }
}
