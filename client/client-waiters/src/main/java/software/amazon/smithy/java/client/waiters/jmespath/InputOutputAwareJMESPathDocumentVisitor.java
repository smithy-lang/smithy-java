/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.waiters.jmespath;

import java.util.Objects;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.jmespath.JMESPathDocumentQuery;
import software.amazon.smithy.jmespath.ExpressionVisitor;
import software.amazon.smithy.jmespath.ast.AndExpression;
import software.amazon.smithy.jmespath.ast.ComparatorExpression;
import software.amazon.smithy.jmespath.ast.CurrentExpression;
import software.amazon.smithy.jmespath.ast.ExpressionTypeExpression;
import software.amazon.smithy.jmespath.ast.FieldExpression;
import software.amazon.smithy.jmespath.ast.FilterProjectionExpression;
import software.amazon.smithy.jmespath.ast.FlattenExpression;
import software.amazon.smithy.jmespath.ast.FunctionExpression;
import software.amazon.smithy.jmespath.ast.IndexExpression;
import software.amazon.smithy.jmespath.ast.LiteralExpression;
import software.amazon.smithy.jmespath.ast.MultiSelectHashExpression;
import software.amazon.smithy.jmespath.ast.MultiSelectListExpression;
import software.amazon.smithy.jmespath.ast.NotExpression;
import software.amazon.smithy.jmespath.ast.ObjectProjectionExpression;
import software.amazon.smithy.jmespath.ast.OrExpression;
import software.amazon.smithy.jmespath.ast.ProjectionExpression;
import software.amazon.smithy.jmespath.ast.SliceExpression;
import software.amazon.smithy.jmespath.ast.Subexpression;

/**
 * Provides custom handling JMESPath expression that may have input/output evaluated in Waiters.
 *
 * <p>Waiters special-case the `input` and `output` keywords, allowing users to target those
 * shapes in their JMESPath expressions. This decorator handles such JMESPath expressions.
 */
record InputOutputAwareJMESPathDocumentVisitor(Document input, Document output) implements ExpressionVisitor<Document> {
    private static final String INPUT_NAME = "input";
    private static final String OUTPUT_NAME = "output";

    InputOutputAwareJMESPathDocumentVisitor {
        Objects.requireNonNull(output, "output cannot be null");
    }

    @Override
    public Document visitComparator(ComparatorExpression comparatorExpression) {
        return JMESPathDocumentQuery.query(comparatorExpression, output);
    }

    @Override
    public Document visitCurrentNode(CurrentExpression currentExpression) {
        return JMESPathDocumentQuery.query(currentExpression, output);
    }

    @Override
    public Document visitExpressionType(ExpressionTypeExpression expressionTypeExpression) {
        return JMESPathDocumentQuery.query(expressionTypeExpression, output);
    }

    @Override
    public Document visitFlatten(FlattenExpression flattenExpression) {
        return JMESPathDocumentQuery.query(flattenExpression, output);
    }

    @Override
    public Document visitFunction(FunctionExpression functionExpression) {
        return JMESPathDocumentQuery.query(functionExpression, output);
    }

    @Override
    public Document visitField(FieldExpression fieldExpression) {
        if (input != null && INPUT_NAME.equals(fieldExpression.getName())) {
            return input;
        } else if (OUTPUT_NAME.equals(fieldExpression.getName())) {
            return output;
        }
        return JMESPathDocumentQuery.query(fieldExpression, output);
    }

    @Override
    public Document visitIndex(IndexExpression indexExpression) {
        return JMESPathDocumentQuery.query(indexExpression, output);
    }

    @Override
    public Document visitLiteral(LiteralExpression literalExpression) {
        return JMESPathDocumentQuery.query(literalExpression, output);
    }

    @Override
    public Document visitMultiSelectList(MultiSelectListExpression multiSelectListExpression) {
        return JMESPathDocumentQuery.query(multiSelectListExpression, output);
    }

    @Override
    public Document visitMultiSelectHash(MultiSelectHashExpression multiSelectHashExpression) {
        return JMESPathDocumentQuery.query(multiSelectHashExpression, output);
    }

    @Override
    public Document visitAnd(AndExpression andExpression) {
        return JMESPathDocumentQuery.query(andExpression, output);
    }

    @Override
    public Document visitOr(OrExpression orExpression) {
        return JMESPathDocumentQuery.query(orExpression, output);
    }

    @Override
    public Document visitNot(NotExpression notExpression) {
        return JMESPathDocumentQuery.query(notExpression, output);
    }

    @Override
    public Document visitProjection(ProjectionExpression projectionExpression) {
        return JMESPathDocumentQuery.query(projectionExpression, output);
    }

    @Override
    public Document visitFilterProjection(FilterProjectionExpression filterProjectionExpression) {
        return JMESPathDocumentQuery.query(filterProjectionExpression, output);
    }

    @Override
    public Document visitObjectProjection(ObjectProjectionExpression objectProjectionExpression) {
        return JMESPathDocumentQuery.query(objectProjectionExpression, output);
    }

    @Override
    public Document visitSlice(SliceExpression sliceExpression) {
        return JMESPathDocumentQuery.query(sliceExpression, output);
    }

    @Override
    public Document visitSubexpression(Subexpression subexpression) {
        var left = subexpression.getLeft().accept(this);
        return JMESPathDocumentQuery.query(subexpression.getRight(), left);
    }
}
