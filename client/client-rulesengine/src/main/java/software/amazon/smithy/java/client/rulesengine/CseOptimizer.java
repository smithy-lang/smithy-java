/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.rulesengine.language.syntax.expressions.Expression;
import software.amazon.smithy.rulesengine.language.syntax.rule.Rule;
import software.amazon.smithy.rulesengine.language.syntax.rule.TreeRule;

/**
 * Eliminates common subexpressions so they're stored in a single registry.
 *
 * <p>This currently only eliminates top-level condition functions that are duplicates across any rule or tree-rule
 * and at any depth. It doesn't eliminate nested expressions of functions.
 */
final class CseOptimizer {

    // Counts how many times an expression is duplicated.
    private final Map<Expression, Double> conditions = new HashMap<>();

    static Map<Expression, Byte> apply(List<Rule> rules, int offset) {
        var cse = new CseOptimizer();
        for (var rule : rules) {
            cse.apply(1, rule);
        }
        return cse.getCse(offset);
    }

    private void apply(int depth, Rule rule) {
        if (rule instanceof TreeRule t) {
            for (var c : t.getConditions()) {
                findCse(depth, c.getFunction());
            }
            for (var r : t.getRules()) {
                apply(depth + 1, r);
            }
        } else {
            for (var c : rule.getConditions()) {
                findCse(depth, c.getFunction());
            }
        }
    }

    private void findCse(int depth, Expression f) {
        // Add to the score for each expression, discounting occurrences the deeper they appear.
        conditions.put(f, conditions.getOrDefault(f, 0.0) + (1 / (depth * 0.5)));
    }

    private Map<Expression, Byte> getCse(int offset) {
        int index = offset;

        // Only keep duplicated expressions.
        Map<Expression, Byte> result = new HashMap<>();
        for (var e : conditions.entrySet()) {
            if (e.getValue() > 2) {
                result.put(e.getKey(), (byte) index++);
            }
        }

        return result;
    }
}
