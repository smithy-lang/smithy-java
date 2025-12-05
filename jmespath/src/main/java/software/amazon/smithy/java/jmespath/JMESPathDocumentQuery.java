/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.jmespath;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.jmespath.ExpressionVisitor;
import software.amazon.smithy.jmespath.JmespathExpression;
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
import software.amazon.smithy.jmespath.evaluation.Evaluator;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Performs a query on a document given a JMESPath expression.
 */
public final class JMESPathDocumentQuery {

    private JMESPathDocumentQuery() {}

    /**
     * Queries a document using a JMESPath expression.
     *
     * @param expression JMESPath expression to execute against the document
     * @param document Document to query for data
     * @return result of query
     */
    public static Document query(String expression, Document document) {
        return query(JmespathExpression.parse(expression), document);
    }

    /**
     * Queries a document using a JMESPath expression.
     *
     * @param expression JMESPath expression to execute against the document
     * @param document Document to query for data
     * @return result of query
     */
    public static Document query(JmespathExpression expression, Document document) {
        return new Evaluator<>(document, DocumentJmespathRuntime.INSTANCE).visit(expression);
    }
}
