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
        return new Visitor(document).visit(expression);
    }

    private record Visitor(Document document) implements ExpressionVisitor<Document> {
        private static final EnumSet<ShapeType> OBJECT_TYPES = EnumSet.of(
                ShapeType.MAP,
                ShapeType.STRUCTURE,
                ShapeType.UNION);

        private Document visit(JmespathExpression expression) {
            if (document == null) {
                return null;
            }
            return expression.accept(this);
        }

        @Override
        public Document visitComparator(ComparatorExpression comparatorExpression) {
            var left = visit(comparatorExpression.getLeft());
            var right = visit(comparatorExpression.getRight());
            Boolean value = switch (comparatorExpression.getComparator()) {
                case EQUAL -> Objects.equals(left, right);
                case NOT_EQUAL -> !Objects.equals(left, right);
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
            return visit(expressionTypeExpression.getExpression());
        }

        @Override
        public Document visitFlatten(FlattenExpression flattenExpression) {
            var value = visit(flattenExpression.getExpression());

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
            ExpressionTypeExpression functionReference = null;
            for (var expr : functionExpression.getArguments()) {
                // Store up to one function reference for passing to jmespath functions
                if (expr instanceof ExpressionTypeExpression exprType) {
                    if (functionReference != null) {
                        throw new IllegalArgumentException(
                                "JMESPath functions only support a single function reference");
                    }
                    functionReference = exprType;
                    continue;
                }
                arguments.add(visit(expr));
            }
            return function.apply(arguments, functionReference);
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
            if (!document.type().equals(ShapeType.LIST)) {
                return null;
            }
            // Negative indices indicate reverse indexing in JMESPath
            if (index < 0) {
                index = document.size() + index;
            }
            if (document.size() <= index || index < 0) {
                return null;
            }
            return document.asList().get(index);
        }

        @Override
        public Document visitLiteral(LiteralExpression literalExpression) {
            if (literalExpression.isNumberValue()) {
                // TODO: Remove this check by correcting behavior in smithy-jmespath to correctly
                //       handle int vs double
                var value = literalExpression.expectNumberValue();
                if (value.doubleValue() == Math.floor(value.doubleValue())) {
                    return Document.ofNumber(value.longValue());
                }
            } else if (literalExpression.isArrayValue()) {
                List<Document> result = new ArrayList<>();
                for (var item : literalExpression.expectArrayValue()) {
                    result.add(visit(LiteralExpression.from(item)));
                }
                return Document.of(result);
            } else if (literalExpression.isObjectValue()) {
                var value = literalExpression.expectObjectValue();
                Map<String, Document> result = new HashMap<>();
                for (var entry : value.entrySet()) {
                    result.put(entry.getKey(), visit(LiteralExpression.from(entry.getValue())));
                }
                return Document.of(result);
            }
            return literalExpression.isNullValue() ? null : Document.ofObject(literalExpression.getValue());
        }

        @Override
        public Document visitMultiSelectList(MultiSelectListExpression multiSelectListExpression) {
            List<Document> output = new ArrayList<>();
            for (var exp : multiSelectListExpression.getExpressions()) {
                output.add(visit(exp));
            }
            return output.isEmpty() ? null : Document.of(output);
        }

        @Override
        public Document visitMultiSelectHash(MultiSelectHashExpression multiSelectHashExpression) {
            Map<String, Document> output = new HashMap<>();
            for (var expEntry : multiSelectHashExpression.getExpressions().entrySet()) {
                output.put(expEntry.getKey(), visit(expEntry.getValue()));
            }
            return output.isEmpty() ? null : Document.of(output);
        }

        @Override
        public Document visitAnd(AndExpression andExpression) {
            var left = visit(andExpression.getLeft());
            return JMESPathDocumentUtils.isTruthy(left) ? visit(andExpression.getRight()) : left;
        }

        @Override
        public Document visitOr(OrExpression orExpression) {
            var left = visit(orExpression.getLeft());
            if (JMESPathDocumentUtils.isTruthy(left)) {
                return left;
            }
            return orExpression.getRight().accept(this);
        }

        @Override
        public Document visitNot(NotExpression notExpression) {
            var output = visit(notExpression.getExpression());
            return Document.of(!JMESPathDocumentUtils.isTruthy(output));
        }

        @Override
        public Document visitProjection(ProjectionExpression projectionExpression) {
            var resultList = visit(projectionExpression.getLeft());
            if (resultList == null || !resultList.type().equals(ShapeType.LIST)) {
                return null;
            }
            List<Document> projectedResults = new ArrayList<>();
            for (var result : resultList.asList()) {
                var projected = new Visitor(result).visit(projectionExpression.getRight());
                if (projected != null) {
                    projectedResults.add(projected);
                }
            }
            return Document.of(projectedResults);
        }

        @Override
        public Document visitFilterProjection(FilterProjectionExpression filterProjectionExpression) {
            var left = visit(filterProjectionExpression.getLeft());
            if (left == null || !left.type().equals(ShapeType.LIST)) {
                return null;
            }
            List<Document> results = new ArrayList<>();
            for (var val : left.asList()) {
                var output = new Visitor(val).visit(filterProjectionExpression.getComparison());
                if (JMESPathDocumentUtils.isTruthy(output)) {
                    var result = new Visitor(val).visit(filterProjectionExpression.getRight());
                    if (result != null) {
                        results.add(result);
                    }
                }
            }
            return Document.of(results);
        }

        @Override
        public Document visitObjectProjection(ObjectProjectionExpression objectProjectionExpression) {
            var resultObject = visit(objectProjectionExpression.getLeft());
            if (resultObject == null || !OBJECT_TYPES.contains(resultObject.type())) {
                return null;
            }
            List<Document> projectedResults = new ArrayList<>();
            for (var member : resultObject.getMemberNames()) {
                var memberValue = resultObject.getMember(member);
                if (memberValue != null) {
                    var projectedResult = new Visitor(memberValue).visit(objectProjectionExpression.getRight());
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
            var left = visit(subexpression.getLeft());
            return new Visitor(left).visit(subexpression.getRight());
        }
    }
}
