/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.writer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.codegen.core.ImportContainer;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.utils.ListUtils;

public abstract class DeferredSymbolWriter<W extends SymbolWriter<W, I>, I extends ImportContainer> extends
    SymbolWriter<W, I> {

    protected final Map<String, Set<Symbol>> symbolTable = new HashMap<>();

    public DeferredSymbolWriter(I importContainer) {
        super(importContainer);
    }

    @Override
    public String toString() {
        return format(super.toString());
    }

    /**
     * Add symbol to symbol table, so potential type name conflicts can be detected and
     * handled.
     *
     * @param symbol Symbol to add to symbol table.
     */
    protected void addToSymbolTable(Symbol symbol) {
        Set<Symbol> nameSet = symbolTable.computeIfAbsent(symbol.getName(), n -> new HashSet<>());
        nameSet.add(symbol);
    }

    /**
     * Replace Symbol with version with no type references to avoid false positive duplicates.
     *
     * @param symbol Symbol to normalize
     * @return normalized symbol
     */
    protected static Symbol normalizeSymbol(Symbol symbol) {
        return symbol.toBuilder().references(ListUtils.of()).build();
    }
}
