/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.writer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import software.amazon.smithy.codegen.core.ImportContainer;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.java.codegen.SymbolProperties;

final class JavaImportContainer implements ImportContainer {

    private final Map<String, Set<Symbol>> imports = new HashMap<>();
    private final String namespace;

    JavaImportContainer(String namespace) {
        this.namespace = namespace;
    }

    @Override
    public void importSymbol(Symbol symbol, String s) {
        // Do not import primitive types, java.lang standard library imports or inner classes.
        if (symbol.expectProperty(SymbolProperties.IS_PRIMITIVE)
                || symbol.getNamespace().startsWith("java.lang")
                || symbol.getProperty(SymbolProperties.IS_LOCALLY_DEFINED).isPresent()) {
            return;
        }
        Set<Symbol> duplicates = imports.computeIfAbsent(symbol.getName(), sn -> new HashSet<>());
        duplicates.add(symbol);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (String importName : getSortedAndFilteredImports()) {
            builder.append("import ").append(importName).append(";");
            // Match the writer's "\n" newlines; lineSeparator() yields mixed line endings on Windows.
            builder.append('\n');
        }
        return builder.toString();
    }

    /**
     * Sort imports then filter out any instances of duplicates.
     *
     * @return sorted list of imports
     */
    private Set<String> getSortedAndFilteredImports() {
        TreeSet<String> sortedImports = new TreeSet<>();
        for (Set<Symbol> s : imports.values()) {
            if (s.size() == 1) {
                Symbol next = s.iterator().next();
                if (!next.getNamespace().equals(namespace)) {
                    String fullName = next.getFullName();
                    sortedImports.add(fullName);
                }
            }
        }
        return sortedImports;
    }
}
