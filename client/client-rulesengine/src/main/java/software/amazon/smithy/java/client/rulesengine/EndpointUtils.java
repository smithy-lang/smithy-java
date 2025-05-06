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
import software.amazon.smithy.rulesengine.language.evaluation.value.ArrayValue;
import software.amazon.smithy.rulesengine.language.evaluation.value.BooleanValue;
import software.amazon.smithy.rulesengine.language.evaluation.value.EmptyValue;
import software.amazon.smithy.rulesengine.language.evaluation.value.IntegerValue;
import software.amazon.smithy.rulesengine.language.evaluation.value.RecordValue;
import software.amazon.smithy.rulesengine.language.evaluation.value.StringValue;
import software.amazon.smithy.rulesengine.language.evaluation.value.Value;

final class EndpointUtils {

    private EndpointUtils() {}

    static Object convertInputParamValue(Value value) {
        if (value instanceof StringValue s) {
            return s.getValue();
        } else if (value instanceof IntegerValue i) {
            return i.getValue();
        } else if (value instanceof ArrayValue a) {
            var result = new ArrayList<>();
            for (var v : a.getValues()) {
                result.add(convertInputParamValue(v));
            }
            return result;
        } else if (value instanceof EmptyValue) {
            return null;
        } else if (value instanceof BooleanValue b) {
            return b.getValue();
        } else if (value instanceof RecordValue r) {
            var result = new HashMap<>();
            for (var e : r.getValue().entrySet()) {
                result.put(e.getKey().getName().getValue(), convertInputParamValue(e.getValue()));
            }
            return result;
        } else {
            throw new IllegalArgumentException("Unsupported value type: " + value);
        }
    }

    static Object verifyObject(Object value) {
        if (value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof StringTemplate
                || value instanceof URI) {
            return value;
        }

        if (value instanceof List<?> l) {
            for (var v : l) {
                verifyObject(v);
            }
            return value;
        }

        if (value instanceof Map<?, ?> m) {
            for (var e : m.entrySet()) {
                if (!(e.getKey() instanceof String)) {
                    throw new UnsupportedOperationException("Endpoint parameter maps must use string keys. Found " + e);
                }
                verifyObject(e.getKey());
                verifyObject(e.getValue());
            }
            return m;
        }

        throw new UnsupportedOperationException("Unsupported endpoint rules value given: " + value);
    }

    // Read little-endian unsigned short (2 bytes)
    static int bytesToShort(byte[] instructions, int offset) {
        int low = instructions[offset] & 0xFF;
        int high = instructions[offset + 1] & 0xFF;
        return (high << 8) | low;
    }

    // Write little-endian unsigned short (2 bytes)
    static void shortToTwoBytes(int value, byte[] instructions, int offset) {
        instructions[offset] = (byte) (value & 0xFF);
        instructions[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    static Object getUriProperty(URI uri, String key) {
        return switch (key) {
            case "scheme" -> uri.getScheme();
            case "path" -> uri.getRawPath();
            case "normalizedPath" -> getUriNormalizedPath(uri);
            case "authority" -> uri.getAuthority();
            case "isIp" -> getUriIsIp(uri);
            default -> null;
        };
    }

    private static boolean getUriIsIp(URI uri) {
        String host = uri.getHost();
        if (host == null || host.length() < 2) {
            return false;
        }

        // Simple check for IPv6 (enclosed in square brackets)
        if (host.charAt(0) == '[' && host.charAt(host.length() - 1) == ']') {
            return true;
        }

        // Simple IPv4 check: four dot-separated parts that are valid numbers
        String[] dottedParts = host.split("\\.");
        if (dottedParts.length != 4) {
            return false;
        }

        try {
            for (String dottedPart : dottedParts) {
                int value = Integer.parseInt(dottedPart);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String getUriNormalizedPath(URI uri) {
        var path = uri.getRawPath();
        // The path segment of the URL. This value is guaranteed to start and end with a / character.
        if (!path.startsWith("/")) {
            path = '/' + path;
        }
        if (!path.endsWith("/")) {
            path = path + '/';
        }
        return path;
    }

    static <T> T castFnArgument(Object value, Class<T> type, String method, int position) {
        try {
            return type.cast(value);
        } catch (ClassCastException e) {
            throw new RulesEvaluationError(String.format("Expected %s argument %d to be %s, but given %s",
                    method,
                    position,
                    type.getName(),
                    value.getClass().getName()));
        }
    }
}
