/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.jmespath;

import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.jmespath.JmespathExpression;
import software.amazon.smithy.jmespath.evaluation.Evaluator;

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
