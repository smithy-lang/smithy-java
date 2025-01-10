/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.waiters.jmespath;

import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Used to compare the result of a JMESPath expression with the expected value.
 */
public enum Comparator {
    STRING_EQUALS {
        @Override
        public boolean compare(Document value, String expected) {
            if (value == null) {
                return false;
            }
            return switch (value.type()) {
                case STRING -> value.asString().equals(expected);
                case BYTE, SHORT, INT_ENUM, INTEGER, FLOAT, DOUBLE, BIG_DECIMAL, BIG_INTEGER ->
                    expected.equals(value.asNumber().toString());
                default -> false;
            };
        }
    },
    BOOLEAN_EQUALS {
        @Override
        public boolean compare(Document value, String expected) {
            if (value != null && value.type().equals(ShapeType.BOOLEAN)) {
                return Boolean.parseBoolean(expected) == value.asBoolean();
            }
            return false;
        }
    },
    ALL_STRING_EQUALS {
        @Override
        public boolean compare(Document value, String expected) {
            if (!value.type().equals(ShapeType.LIST)) {
                return false;
            }
            var documentList = value.asList();

            if (documentList.isEmpty()) {
                return false;
            }

            for (var document : documentList) {
                if (Comparator.STRING_EQUALS.compare(document, expected)) {
                    return false;
                }
            }
            return true;
        }
    },
    ANY_STRING_EQUALS {
        @Override
        public boolean compare(Document value, String expected) {
            if (!value.type().equals(ShapeType.LIST)) {
                return false;
            }
            var documentList = value.asList();
            for (var document : documentList) {
                if (Comparator.STRING_EQUALS.compare(document, expected)) {
                    return true;
                }
            }
            return false;
        }
    };

    abstract boolean compare(Document value, String expected);
}
