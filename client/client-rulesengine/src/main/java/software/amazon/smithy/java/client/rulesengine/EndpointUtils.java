/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

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
        if (o == null) {
            return Value.emptyValue();
        } else if (o instanceof String s) {
            return Value.stringValue(s);
        } else if (o instanceof Number n) {
            return Value.integerValue(n.intValue());
        } else if (o instanceof Boolean b) {
            return Value.booleanValue(b);
        } else if (o instanceof List<?> l) {
            List<Value> valueList = new ArrayList<>(l.size());
            for (var entry : l) {
                valueList.add(convertToValue(entry));
            }
            return Value.arrayValue(valueList);
        } else if (o instanceof Map<?, ?> m) {
            Map<Identifier, Value> valueMap = new HashMap<>(m.size());
            for (var e : m.entrySet()) {
                valueMap.put(Identifier.of(e.getKey().toString()), convertToValue(e.getValue()));
            }
            return Value.recordValue(valueMap);
        } else {
            throw new RulesEvaluationError("Unsupported value type: " + o);
        }
    }

    // Read big-endian unsigned short (2 bytes)
    static int bytesToShort(byte[] instructions, int offset) {
        return ((instructions[offset] & 0xFF) << 8) | (instructions[offset + 1] & 0xFF);
    }
}
