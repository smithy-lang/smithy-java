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
import java.util.stream.Collectors;
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
        // Do not import primitive types, java.lang standard library imports,
        // or any symbols already in the same namespace.
        if (symbol.expectProperty(SymbolProperties.IS_PRIMITIVE)
            || symbol.getNamespace().startsWith("java.lang")
            || symbol.getNamespace().equals(namespace)
        ) {
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
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

    /**
     * Sort imports then filter out any instances of duplicates.
     *
     * @return sorted list of imports
     */
    private Set<String> getSortedAndFilteredImports() {
        return imports.values()
            .stream()
            .filter(s -> s.size() == 1)
            .map(s -> s.iterator().next())
            .map(Symbol::getFullName)
            .collect(Collectors.toCollection(TreeSet::new));
    }
}
