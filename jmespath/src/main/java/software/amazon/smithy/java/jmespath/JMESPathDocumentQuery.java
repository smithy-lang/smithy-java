/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.jmespath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        return expression.accept(new Visitor(document));
    }

    private record Visitor(Document document) implements ExpressionVisitor<Document> {
        @Override
        public Document visitComparator(ComparatorExpression comparatorExpression) {
            var left = comparatorExpression.getLeft().accept(this);
            if (left == null) {
                return null;
            }
            var right = comparatorExpression.getRight().accept(this);
            if (right == null) {
                return null;
            }
            Boolean value = switch (comparatorExpression.getComparator()) {
                case EQUAL -> left.equals(right);
                case NOT_EQUAL -> !left.equals(right);
                // NOTE: Ordering operators >, >=, <, <= are only valid for numbers. All invalid
                // comparisons return null.
                case LESS_THAN ->
                    JMESPathDocumentUtils.isNumericComparison(left, right) ? Document.compare(left, right) < 0 : null;
                case LESS_THAN_EQUAL ->
                    JMESPathDocumentUtils.isNumericComparison(left, right) ? Document.compare(left, right) <= 0 : null;
                case GREATER_THAN ->
                    JMESPathDocumentUtils.isNumericComparison(left, right) ? Document.compare(left, right) > 0 : null;
                case GREATER_THAN_EQUAL ->
                    JMESPathDocumentUtils.isNumericComparison(left, right) ? Document.compare(left, right) >= 0 : null;
            };
            return value == null ? null : Document.of(value);
        }

        @Override
        public Document visitCurrentNode(CurrentExpression currentExpression) {
            return document;
        }

        @Override
        public Document visitExpressionType(ExpressionTypeExpression expressionTypeExpression) {
            throw new UnsupportedOperationException("Expression type not supported: " + expressionTypeExpression);
        }

        @Override
        public Document visitFlatten(FlattenExpression flattenExpression) {
            var value = flattenExpression.getExpression().accept(this);

            // Only lists can be flattened.
            if (value == null || !value.type().equals(ShapeType.LIST)) {
                return null;
            }
            List<Document> flattened = new ArrayList<>();
            for (var val : value.asList()) {
                if (val.type().equals(ShapeType.LIST)) {
                    flattened.addAll(val.asList());
                    continue;
                }
                flattened.add(val);
            }
            return Document.of(flattened);
        }

        @Override
        public Document visitFunction(FunctionExpression functionExpression) {
            var function = JMESPathFunction.from(functionExpression);
            List<Document> arguments = new ArrayList<>();
            for (var expr : functionExpression.getArguments()) {
                arguments.add(expr.accept(this));
            }
            return function.apply(arguments);
        }

        @Override
        public Document visitField(FieldExpression fieldExpression) {
            return switch (document.type()) {
                case MAP, STRUCTURE, UNION -> document.getMember(fieldExpression.getName());
                default -> null;
            };
        }

        @Override
        public Document visitIndex(IndexExpression indexExpression) {
            var index = indexExpression.getIndex();
            if (document.size() < index) {
                return null;
            }
            // Negative indices indicate reverse indexing in JMESPath
            if (index < 0) {
                index = document.size() + index;
            }
            return document.asList().get(index);
        }

        @Override
        public Document visitLiteral(LiteralExpression literalExpression) {
            return Document.ofObject(literalExpression.getValue());
        }

        @Override
        public Document visitMultiSelectList(MultiSelectListExpression multiSelectListExpression) {
            List<Document> output = new ArrayList<>();
            for (var exp : multiSelectListExpression.getExpressions()) {
                output.add(exp.accept(this));
            }
            return Document.of(output);
        }

        @Override
        public Document visitMultiSelectHash(MultiSelectHashExpression multiSelectHashExpression) {
            Map<String, Document> output = new HashMap<>();
            for (var expEntry : multiSelectHashExpression.getExpressions().entrySet()) {
                output.put(expEntry.getKey(), expEntry.getValue().accept(this));
            }
            return Document.of(output);
        }

        @Override
        public Document visitAnd(AndExpression andExpression) {
            var left = andExpression.getLeft().accept(this);
            return JMESPathDocumentUtils.isTruthy(left) ? andExpression.getRight().accept(this) : left;
        }

        @Override
        public Document visitOr(OrExpression orExpression) {
            var left = orExpression.getLeft().accept(this);
            if (JMESPathDocumentUtils.isTruthy(left)) {
                return left;
            }
            var right = orExpression.getRight().accept(this);
            if (JMESPathDocumentUtils.isTruthy(right)) {
                return right;
            }
            return null;
        }

        @Override
        public Document visitNot(NotExpression notExpression) {
            var output = notExpression.getExpression().accept(this);
            return Document.of(!JMESPathDocumentUtils.isTruthy(output));
        }

        @Override
        public Document visitProjection(ProjectionExpression projectionExpression) {
            var resultList = projectionExpression.getLeft().accept(this);
            if (resultList == null || !resultList.type().equals(ShapeType.LIST)) {
                return null;
            }
            List<Document> projectedResults = new ArrayList<>();
            for (var result : resultList.asList()) {
                var projected = projectionExpression.getRight().accept(new Visitor(result));
                if (projected != null) {
                    projectedResults.add(projected);
                }
            }
            return Document.of(projectedResults);
        }

        @Override
        public Document visitFilterProjection(FilterProjectionExpression filterProjectionExpression) {
            var target = filterProjectionExpression.getRight().accept(this);
            var left = filterProjectionExpression.getLeft().accept(new Visitor(target));
            if (left == null || !left.type().equals(ShapeType.LIST)) {
                return null;
            }
            List<Document> results = new ArrayList<>();
            for (var val : left.asList()) {
                var output = filterProjectionExpression.getComparison().accept(new Visitor(val));
                if (JMESPathDocumentUtils.isTruthy(output)) {
                    results.add(val);
                }
            }
            return Document.of(results);
        }

        @Override
        public Document visitObjectProjection(ObjectProjectionExpression objectProjectionExpression) {
            var resultObject = objectProjectionExpression.getLeft().accept(this);
            List<Document> projectedResults = new ArrayList<>();
            for (var member : resultObject.getMemberNames()) {
                var memberValue = resultObject.getMember(member);
                if (memberValue != null) {
                    var projectedResult =
                            objectProjectionExpression.getRight().accept(new Visitor(memberValue));
                    if (projectedResult != null) {
                        projectedResults.add(projectedResult);
                    }
                }
            }
            return Document.of(projectedResults);
        }

        @Override
        public Document visitSlice(SliceExpression sliceExpression) {
            List<Document> output = new ArrayList<>();
            int step = sliceExpression.getStep();
            int start = sliceExpression.getStart().orElseGet(() -> step > 0 ? 0 : document.size());
            if (start < 0) {
                start = document.size() + start;
            }
            int stop = sliceExpression.getStop().orElseGet(() -> step > 0 ? document.size() : 0);
            if (stop < 0) {
                stop = document.size() + stop;
            }

            var docList = document.asList();
            if (start < stop) {
                for (int idx = start; idx < stop; idx += step) {
                    output.add(docList.get(idx));
                }
            } else {
                // List is iterating in reverse
                for (int idx = start; idx > stop; idx += step) {
                    output.add(docList.get(idx - 1));
                }
            }
            return Document.of(output);
        }

        @Override
        public Document visitSubexpression(Subexpression subexpression) {
            var left = subexpression.getLeft().accept(this);
            return subexpression.getRight().accept(new Visitor(left));
        }
    }
}
