/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.shapes.Shape;

/**
 * TODO: DOcs
 * @param writer
 * @param symbolProvider
 * @param shape
 */
record TypeEnumGenerator(JavaWriter writer, Shape shape, SymbolProvider symbolProvider) implements
    Runnable {
    @Override
    public void run() {
        List<String> enumList = new ArrayList<>();
        enumList.add("$$UNKNOWN");
        shape.members()
            .stream()
            .map(member -> CodegenUtils.getEnumVariantName(symbolProvider, member))
            .forEach(enumList::add);
        writer.pushState();
        writer.putContext("variants", enumList);
        writer.write("""
            public enum Type {
                ${#variants}${value:L}${^key.last},
                ${/key.last}${/variants}
            }
            """);
        writer.popState();
    }
}
