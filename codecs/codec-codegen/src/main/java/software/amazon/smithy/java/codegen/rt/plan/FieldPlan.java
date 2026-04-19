/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.rt.plan;

import software.amazon.smithy.java.core.schema.Schema;

/**
 * Metadata for a single struct member, resolved at plan time for code generation.
 */
public record FieldPlan(
        Schema schema,
        String memberName,
        String getterName,
        int memberIndex,
        FieldCategory category,
        boolean required,
        boolean nullable,
        String timestampFormat,
        boolean sparse,
        Class<?> elementClass,
        Class<?> mapValueClass) {
    public int fixedSizeUpperBound() {
        return category.fixedSizeUpperBound();
    }
}
