/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.endpointrules;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import software.amazon.smithy.rulesengine.language.evaluation.value.Value;
import software.amazon.smithy.rulesengine.language.syntax.expressions.Expression;
import software.amazon.smithy.rulesengine.language.syntax.expressions.Template;

/**
 * Similar to {@link Template}, but built around Object instead of {@link Value}.
 */
final class StringTemplate {

    private final String template;
    private final Object[] parts;
    private final int expressionCount;
    private final Expression singularExpression;

    private StringTemplate(String template, Object[] parts, int expressionCount, Expression singularExpression) {
        this.template = template;
        this.parts = parts;
        this.expressionCount = expressionCount;
        this.singularExpression = singularExpression;
    }

    @Override
    public String toString() {
        return template.substring(1, template.length() - 1);
    }

    /**
     * Get the number of expressions in the template (telling the VM how much to pop).
     *
     * @return the expression count.
     */
    int expressionCount() {
        return expressionCount;
    }

    /**
     * If the expression contains only an expression and no other text, returns the expression. Otherwise null.
     *
     * @return the expression-only or null if there are other parts.
     */
    Expression getSingularExpression() {
        return singularExpression;
    }

    /**
     * Calls a consumer for every expression in the template.
     *
     * @param consumer consumer that accepts each expression.
     */
    void forEachExpression(Consumer<Expression> consumer) {
        for (int i = parts.length - 1; i >= 0; i--) {
            var part = parts[i];
            if (part instanceof Expression e) {
                consumer.accept(e);
            }
        }
    }

    String resolve(String... strings) {
        if (strings.length != expressionCount()) {
            String given = String.join(", ", strings);
            throw new RulesEvaluationError("Missing template parameters for a string template `"
                    + template + "`. Given: [" + given + ']');
        }
        StringBuilder result = new StringBuilder();
        int paramIndex = 0;
        for (var part : parts) {
            if (part instanceof Expression) {
                result.append(strings[paramIndex++]);
            } else {
                result.append(part);
            }
        }
        return result.toString();
    }

    static StringTemplate from(Template template) {
        var templateParts = template.getParts();
        Object[] parts = new Object[templateParts.size()];
        int expressionCount = 0;
        for (var i = 0; i < templateParts.size(); i++) {
            var part = templateParts.get(i);
            if (part instanceof Template.Dynamic d) {
                expressionCount++;
                parts[i] = d.toExpression();
            } else {
                parts[i] = part;
            }
        }
        var singularExpression = (expressionCount == 1 && parts.length == 1) ? (Expression) parts[0] : null;
        return new StringTemplate(template.toString(), parts, expressionCount, singularExpression);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        } else {
            StringTemplate that = (StringTemplate) o;
            return expressionCount == that.expressionCount
                    && Objects.equals(template, that.template)
                    && Objects.deepEquals(parts, that.parts);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(template, Arrays.hashCode(parts));
    }
}
