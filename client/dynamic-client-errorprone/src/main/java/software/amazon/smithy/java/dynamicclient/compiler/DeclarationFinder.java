/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient.compiler;

import com.google.errorprone.VisitorState;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.code.Symbol;

/**
 * Scans a compilation unit for the {@link VariableTree} that declares a given {@link Symbol}. Used as a fallback when
 * the declaration is not on the path from the current call site to the top level (e.g. a field, or a local declared in
 * a sibling method within the same file).
 */
final class DeclarationFinder {

    private DeclarationFinder() {}

    static VariableTree find(Symbol target, VisitorState state) {
        var unit = state.getPath().getCompilationUnit();
        if (unit == null) {
            return null;
        }
        var scanner = new TreePathScanner<Void, Void>() {
            VariableTree found;

            @Override
            public Void visitVariable(VariableTree node, Void unused) {
                if (found == null && ASTHelpers.getSymbol(node) == target) {
                    found = node;
                }
                return super.visitVariable(node, unused);
            }
        };
        scanner.scan(unit, null);
        return scanner.found;
    }
}
