/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.jmespath;

import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.jmespath.JmespathExpression;

/**
 * Smithy-Java JMESPath query implementations.
 */
public final class JmesPathQueries {

    private JmesPathQueries() {}

    /**
     * Queries a document using a JMESPath expression.
     *
     * @param expression JMESPath expression to execute against the document
     * @param document Document to query for data
     * @return result of query
     */
    public static Document query(JmespathExpression expression, Document document) {
        return expression.evaluate(document, DocumentJmespathRuntime.INSTANCE);
    }

    /**
     * Queries a structure/union using a JMESPath expression.
     *
     * @param expression JMESPath expression to execute against the value
     * @param value Structure/union to query
     * @return result of query
     */
    public static Object query(JmespathExpression expression, SerializableStruct value) {
        return expression.evaluate(value, GeneratedTypeJmespathRuntime.INSTANCE);
    }
}
