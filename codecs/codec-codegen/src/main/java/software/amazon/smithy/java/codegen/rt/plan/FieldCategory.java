/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.rt.plan;

/**
 * Classifies struct member types for codec code generation.
 * Each category carries a fixed-size upper bound (in JSON bytes) used for batched capacity checks.
 * A value of -1 means the size is variable and cannot be pre-computed.
 */
public enum FieldCategory {
    BOOLEAN(5),
    BYTE(4),
    SHORT(6),
    INTEGER(11),
    LONG(20),
    FLOAT(24),
    DOUBLE(24),
    STRING(-1),
    BLOB(-1),
    TIMESTAMP(-1),
    BIG_INTEGER(-1),
    BIG_DECIMAL(-1),
    ENUM_STRING(-1),
    INT_ENUM(11),
    LIST(-1),
    MAP(-1),
    STRUCT(-1),
    UNION(-1),
    DOCUMENT(-1);

    private final int fixedSizeUpperBound;

    FieldCategory(int fixedSizeUpperBound) {
        this.fixedSizeUpperBound = fixedSizeUpperBound;
    }

    public int fixedSizeUpperBound() {
        return fixedSizeUpperBound;
    }

    public boolean isPrimitive() {
        return switch (this) {
            case BOOLEAN, BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, INT_ENUM -> true;
            default -> false;
        };
    }

    public boolean isFixedSize() {
        return fixedSizeUpperBound >= 0;
    }
}
