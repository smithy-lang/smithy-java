/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.endpointrules;

import java.util.Arrays;
import java.util.Objects;
import software.amazon.smithy.rulesengine.language.syntax.expressions.Expression;
import software.amazon.smithy.rulesengine.language.syntax.expressions.Template;

final class StringTemplate {

    private final String template;
    private final Object[] parts;
    private final int expressionCount;
    private final Expression templateOnly;

    private StringTemplate(String template, Object[] parts, int expressionCount, Expression templateOnly) {
        this.template = template;
        this.parts = parts;
        this.expressionCount = expressionCount;
        this.templateOnly = templateOnly;
    }

    @Override
    public String toString() {
        return template.substring(1, template.length() - 1);
    }

    int expressionCount() {
        return expressionCount;
    }

    Expression getTemplateOnly() {
        return templateOnly;
    }

    Object[] getParts() {
        return parts;
    }

    String resolve(String... strings) {
        if (strings.length != expressionCount()) {
            String given = String.join(", ", strings);
            throw new RulesEvaluationError("Missing template parameters for a string template "
                    + template + ". Given " + given);
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
        if (templateParts.size() == 1 && !template.isStatic()) {
            // The template is guaranteed to be nothing but a single dynamic value.
            Object[] parts = new Object[1];
            var part = (Template.Dynamic) templateParts.get(0);
            parts[0] = part;
            return new StringTemplate(template.toString(), parts, 1, part.toExpression());
        } else {
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
            return new StringTemplate(template.toString(), parts, expressionCount, null);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StringTemplate that = (StringTemplate) o;
        return expressionCount == that.expressionCount
               && Objects.equals(template, that.template)
               && Objects.deepEquals(parts, that.parts)
               && Objects.equals(templateOnly, that.templateOnly);
    }

    @Override
    public int hashCode() {
        return Objects.hash(template, Arrays.hashCode(parts), expressionCount, templateOnly);
    }
}
