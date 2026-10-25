/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.CustomizeDirective;
import software.amazon.smithy.codegen.core.directed.Directive;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.core.schema.EnumValue;
import software.amazon.smithy.java.core.schema.IntEnumValue;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.loader.Prelude;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Generates the package-private {@code $HiddenEnum_<hash>} and {@code $HiddenIntEnum_<hash>} classes.
 *
 * <p>Every generated enum is a sealed interface that permits one class per known value, {@code $Unknown}, and one of
 * these classes. Code outside the package cannot name them, so a switch over the variant types of an enum always
 * needs a default branch and keeps compiling when the model adds a value. One class per generation run implements
 * every enum interface instead of one hidden class per enum.
 *
 * <p>The class name ends in a hash of the sorted interface names it implements. The class has no other content, so
 * two generation runs that target the same package produce the same class name only when they produce the same
 * class, and a collision on the classpath can never be a mismatch.
 */
@SmithyInternalApi
public final class EnumHiddenVariantGenerator
        implements Consumer<CustomizeDirective<CodeGenerationContext, JavaCodegenSettings>> {

    /**
     * Returns the hidden class name that the enum {@code shape} must permit. It is computed from every enum of the
     * same kind in the generation run, so it matches the class this generator emits.
     */
    static String classNameFor(Directive<?> directive, SymbolProvider symbolProvider, Shape shape) {
        boolean string = shape.getType() == ShapeType.ENUM;
        return className(string, collect(directive, symbolProvider, string));
    }

    @Override
    public void accept(CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        var enums = collect(directive, directive.symbolProvider(), true);
        var intEnums = collect(directive, directive.symbolProvider(), false);
        generate(directive, className(true, enums), EnumValue.class, "null", enums);
        generate(directive, className(false, intEnums), IntEnumValue.class, "0", intEnums);
    }

    private static String className(boolean string, List<Symbol> interfaces) {
        var names = interfaces.stream().map(Symbol::getFullName).collect(Collectors.joining("\n"));
        return (string ? "$HiddenEnum_" : "$HiddenIntEnum_") + sha256(names).substring(0, 8);
    }

    private static List<Symbol> collect(Directive<?> directive, SymbolProvider symbolProvider, boolean string) {
        var wanted = string ? ShapeType.ENUM : ShapeType.INT_ENUM;
        List<Symbol> symbols = new ArrayList<>();
        for (var shape : directive.connectedShapes().values()) {
            if (shape.getType() != wanted || Prelude.isPreludeShape(shape)) {
                continue;
            }
            var symbol = symbolProvider.toSymbol(shape);
            if (!symbol.getProperty(SymbolProperties.EXTERNAL_TYPE).orElse(false)) {
                symbols.add(symbol);
            }
        }
        symbols.sort(Comparator.comparing(Symbol::getFullName));
        return symbols;
    }

    private static String sha256(String value) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void generate(
            CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive,
            String className,
            Class<?> base,
            String initialValue,
            List<Symbol> interfaces
    ) {
        if (interfaces.isEmpty()) {
            return;
        }
        var fileName = CodegenUtils.getJavaFilePath(directive.settings(), "model", className);
        directive.context()
                .writerDelegator()
                .useFileWriter(fileName, CodegenUtils.getModelNamespace(directive.settings()), writer -> {
                    writer.pushState();
                    writer.putContext("className", className);
                    writer.putContext("base", base);
                    writer.putContext("initialValue", initialValue);
                    writer.putContext("interfaces", interfaces);
                    writer.putContext("shapeSerializer", ShapeSerializer.class);
                    writer.putContext("unsupported", UnsupportedOperationException.class);
                    writer.write(
                            """
                                    /**
                                     * A permitted but unnameable variant of every enum generated with this package: a switch over
                                     * the variant types of an enum always needs a default branch, so it keeps compiling when the
                                     * model adds a value. Never instantiated.
                                     */
                                    final class ${className:L} extends ${base:T} implements ${#interfaces}${value:T}${^key.last}, ${/key.last}${/interfaces} {
                                        private ${className:L}() {
                                            super(${initialValue:L});
                                        }

                                        @Override
                                        public void serialize(${shapeSerializer:T} serializer) {
                                            throw new ${unsupported:T}();
                                        }
                                    }
                                    """);
                    writer.popState();
                });
    }
}
