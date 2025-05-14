/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

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

    private static final ThreadLocal<StringBuilder> STRING_BUILDER = ThreadLocal.withInitial(
            () -> new StringBuilder(64));

    private final String template;
    private final Object[] parts;
    private final int expressionCount;
    private final Expression singularExpression;

    StringTemplate(String template, Object[] parts, int expressionCount, Expression singularExpression) {
        this.template = template;
        this.parts = parts;
        this.expressionCount = expressionCount;
        this.singularExpression = singularExpression;
    }

    int expressionCount() {
        return expressionCount;
    }

    Expression singularExpression() {
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

    String resolve(int arraySize, Object[] strings) {
        if (arraySize != expressionCount) {
            throw new RulesEvaluationError("Missing template parameters for a string template `"
                    + template + "`. Given: [" + Arrays.asList(strings) + ']');
        }

        var result = STRING_BUILDER.get();
        result.setLength(0);
        int paramIndex = 0;
        for (var part : parts) {
            if (part == null) {
                throw new RulesEvaluationError("Missing part of template " + template + " at part " + paramIndex);
            } else if (part.getClass() == String.class) {
                result.append((String) part); // we know parts are either strings or Expressions.
            } else {
                result.append(strings[paramIndex++]);
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
                parts[i] = part.toString();
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

    @Override
    public String toString() {
        return "StringTemplate[template=\"" + template + "\"]";
    }
}
