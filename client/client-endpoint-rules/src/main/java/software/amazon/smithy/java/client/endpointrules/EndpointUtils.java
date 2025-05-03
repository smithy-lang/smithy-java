/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.endpointrules;

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

    static Object convertValue(Value value) {
        if (value instanceof StringValue s) {
            return s.getValue();
        } else if (value instanceof IntegerValue i) {
            return i.getValue();
        } else if (value instanceof ArrayValue a) {
            var result = new ArrayList<>();
            for (var v : a.getValues()) {
                result.add(convertValue(v));
            }
            return result;
        } else if (value instanceof EmptyValue) {
            return null;
        } else if (value instanceof BooleanValue b) {
            return b.getValue();
        } else if (value instanceof RecordValue r) {
            var result = new HashMap<>();
            for (var e : r.getValue().entrySet()) {
                result.put(e.getKey().getName().getValue(), convertValue(e.getValue()));
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
                verifyObject(e.getKey());
                verifyObject(e.getValue());
            }
        }

        throw new UnsupportedOperationException("Unsupported endpoint rules value type given: " + value);
    }

    static void serializeObject(Object value, StringBuilder sink) {
        if (value instanceof String s) {
            sink.append('"').append(s.replace("\"", "\\\"")).append('"');
        } else if (value instanceof Boolean || value instanceof Number) {
            sink.append(value);
        } else if (value instanceof StringTemplate || value instanceof URI) {
            // Add quotes to the value and ensure inner quotes are escaped.
            serializeObject(value.toString(), sink);
        } else if (value instanceof List<?> l) {
            sink.append('[');
            var first = true;
            for (var v : l) {
                if (!first) {
                    sink.append(",");
                }
                serializeObject(v, sink);
                first = false;
            }
            sink.append(']');
        } else if (value instanceof Map<?, ?> m) {
            sink.append('{');
            var first = true;
            for (var e : m.entrySet()) {
                if (!first) {
                    sink.append(',');
                }
                serializeObject(e.getKey(), sink);
                sink.append(':');
                serializeObject(e.getValue(), sink);
                first = false;
            }
            sink.append('}');
        } else {
            sink.append(value);
        }
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
}
