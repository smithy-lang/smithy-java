/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.waiters.jmespath;

import java.util.function.Predicate;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.jmespath.JmespathExpression;

/**
 * Tests the input and output of a client call against a JMESPath expression.
 */
public final class JMESPathPredicate implements Predicate<SerializableStruct> {
    private final JmespathExpression expression;
    private final String expected;
    private final Comparator comparator;

    public JMESPathPredicate(String path, String expected, Comparator comparator) {
        this.expression = JmespathExpression.parse(path);
        this.expected = expected;
        this.comparator = comparator;
    }

    @Override
    public boolean test(SerializableStruct output) {
        var value = expression.accept(new InputOutputAwareJMESPathDocumentVisitor(null, Document.of(output)));
        return value != null && comparator.compare(value, expected);
    }
}
