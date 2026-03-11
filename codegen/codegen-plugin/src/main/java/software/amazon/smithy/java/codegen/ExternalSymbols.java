/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import software.amazon.smithy.codegen.core.Symbol;

/**
 * Pre-built Symbol objects for types from modules that are NOT on the compile classpath
 * of codegen-plugin (e.g., framework-errors generated types).
 */
public final class ExternalSymbols {
    private ExternalSymbols() {}

    /**
     * Symbol for {@code software.amazon.smithy.java.framework.model.UnknownOperationException}.
     * This class lives in the framework-errors module which is NOT on the compile classpath.
     */
    public static final Symbol UNKNOWN_OPERATION_EXCEPTION = Symbol.builder()
            .name("UnknownOperationException")
            .namespace("software.amazon.smithy.java.framework.model", ".")
            .putProperty(SymbolProperties.IS_PRIMITIVE, false)
            .build();
}
