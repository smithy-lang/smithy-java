/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.jmespath;

import java.util.EnumSet;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeType;

final class JMESPathDocumentUtils {
    private static final EnumSet<ShapeType> NUMERIC_TYPES = EnumSet.of(
            ShapeType.BYTE,
            ShapeType.SHORT,
            ShapeType.INTEGER,
            ShapeType.LONG,
            ShapeType.FLOAT,
            ShapeType.DOUBLE,
            ShapeType.BIG_DECIMAL,
            ShapeType.BIG_INTEGER,
            ShapeType.INT_ENUM);

    static boolean isTruthy(Document document) {
        if (document == null) {
            return false;
        }
        return switch (document.type()) {
            // TODO: Should structures be included here?
            case LIST, MAP -> document.size() != 0;
            case STRING -> !document.asString().isEmpty();
            case BOOLEAN -> document.asBoolean();
            // All other values are considered "truthy" if they exist.
            default -> true;
        };
    }

    static boolean isNumericComparison(Document left, Document right) {
        return isNumeric(left) && isNumeric(right);
    }

    static boolean isNumeric(Document document) {
        return document != null && NUMERIC_TYPES.contains(document.type());
    }

    private JMESPathDocumentUtils() {}
}
