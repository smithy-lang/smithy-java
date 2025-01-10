/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.waiters.jmespath;

import java.util.function.BiPredicate;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.jmespath.JmespathExpression;

/**
 * Tests the input/output of a client call against a JMESPath expression.
 *
 * <p><strong>Note:</strong>The input shape is optional, but the tested output must be nonnull.
 */
public final class JMESPathBiPredicate implements BiPredicate<SerializableStruct, SerializableStruct> {
    private final JmespathExpression expression;
    private final String expected;
    private final Comparator comparator;

    public JMESPathBiPredicate(String path, String expected, Comparator comparator) {
        this.expression = JmespathExpression.parse(path);
        this.expected = expected;
        this.comparator = comparator;
    }

    @Override
    public boolean test(SerializableStruct input, SerializableStruct output) {
        var value =
                expression.accept(new InputOutputAwareJMESPathDocumentVisitor(Document.of(input), Document.of(output)));
        return value != null && comparator.compare(value, expected);
    }
}
