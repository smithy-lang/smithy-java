/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    // Get a property from a map or URI, or return null.
    static Object getProperty(Object target, String propertyName) {
        return switch (target) {
            case Map<?, ?> m -> m.get(propertyName);
            case URI u -> switch (propertyName) {
                case "scheme" -> u.getScheme();
                case "path" -> u.getRawPath();
                case "normalizedPath" -> ParseUrl.normalizePath(u.getRawPath());
                case "authority" -> u.getAuthority();
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
