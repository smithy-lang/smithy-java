/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.writer;

import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.java.codegen.SymbolProperties;

/**
 * Symbols for JSpecify nullness annotations.
 */
final class JSpecifyAnnotations {
    static final Symbol NULL_MARKED = Symbol.builder()
            .name("NullMarked")
            .namespace("org.jspecify.annotations", ".")
            .putProperty(SymbolProperties.IS_PRIMITIVE, false)
            .build();

    static final Symbol NULLABLE = Symbol.builder()
            .name("Nullable")
            .namespace("org.jspecify.annotations", ".")
            .putProperty(SymbolProperties.IS_PRIMITIVE, false)
            .build();

    private JSpecifyAnnotations() {}
}
