/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.writer;

import java.util.Set;
import java.util.function.BiFunction;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolReference;
import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyUnstableApi;
import software.amazon.smithy.utils.StringUtils;

/**
 * Writer for java code generation
 *
 * TODO: update docs
 */
@SmithyUnstableApi
public class JavaWriter extends DeferredSymbolWriter<JavaWriter, JavaImportContainer> {
    private final String packageNamespace;
    private final JavaCodegenSettings settings;

    public JavaWriter(JavaCodegenSettings settings, String packageNamespace) {
        super(new JavaImportContainer(packageNamespace));

        this.packageNamespace = packageNamespace;
        this.settings = settings;

        // Ensure extraneous white space is trimmed
        trimBlankLines();
        trimTrailingSpaces();

        // Formatters
        putFormatter('T', new JavaTypeFormatter());
        putFormatter('B', new BoxedTypeFormatter());
        putFormatter('U', new CapitalizingFormatter());
    }

    // Java does not support aliases, so just import normally
    private void addImport(Symbol symbol) {
        addImport(symbol, symbol.getName());
    }

    @Override
    public String toString() {
        putNameContext();
        return format(
            """
                $L

                package $L;

                $L
                $L
                """,
            settings.header(),
            packageNamespace,
            getImportContainer(),
            super.toString()
        );
    }

    public void newLine() {
        writeInlineWithNoFormatting(getNewline());
    }

    /**
     * Writes the ID string constant for a shape class.
     *
     * @param shape Shape to write ID for
     */
    public void writeIdString(Shape shape) {
        write("public static final $1T ID = $1T.from($2S);", ShapeId.class, shape.getId());
    }

    /**
     * Writes the toString method for a serializable Class.
     */
    public void writeToString() {
        write("""
            @Override
            public $T toString() {
                return $T.serialize(this);
            }
            """, String.class, ToStringSerializer.class);
    }

    private void putNameContext() {
        for (final Set<Symbol> duplicates : symbolTable.values()) {
            // If the duplicates list has more than one entry
            // then duplicates are present, and we need to de-duplicate the names
            if (duplicates.size() > 1) {
                duplicates.forEach(dupe -> putContext(dupe.getFullName(), deduplicate(dupe)));
            } else {
                Symbol symbol = duplicates.iterator().next();
                putContext(symbol.getFullName().replace("[]", "Array"), symbol.getName());
            }
        }
    }

    private String deduplicate(Symbol dupe) {
        // If we are in the namespace of a Symbol, use its
        // short name, otherwise use the full name
        if (dupe.getNamespace().equals(packageNamespace)) {
            return dupe.getName();
        }
        return dupe.getFullName();
    }

    /**
     * TODO: Docs
     * @param writer
     */
    public void writeSchemaGetter() {
        pushState();
        putContext("sdkSchema", SdkSchema.class);
        write("""
            @Override
            public ${sdkSchema:T} schema() {
                return SCHEMA;
            }
            """);
        popState();
    }

    public void writeBuilderGetter() {
        writeWithNoFormatting("""
            public static Builder builder() {
                return new Builder();
            }
            """);
    }

    /**
     * A factory class to create {@link JavaWriter}s.
     */
    public static final class Factory implements SymbolWriter.Factory<JavaWriter> {

        private final JavaCodegenSettings settings;

        /**
         * @param settings The python plugin settings.
         */
        public Factory(JavaCodegenSettings settings) {
            this.settings = settings;
        }

        @Override
        public JavaWriter apply(String filename, String namespace) {
            return new JavaWriter(settings, namespace);
        }
    }


    /**
     * Implements a formatter for {@code $T} that formats Java types.
     */
    private final class JavaTypeFormatter implements BiFunction<Object, String, String> {
        @Override
        public String apply(Object type, String indent) {
            Symbol typeSymbol;
            if (type instanceof Symbol s) {
                typeSymbol = s;
            } else if (type instanceof Class<?> c) {
                typeSymbol = CodegenUtils.fromClass(c);
            } else if (type instanceof SymbolReference r) {
                typeSymbol = r.getSymbol();
            } else {
                throw new IllegalArgumentException(
                    "Invalid type provided for $T. Expected a Symbol or Class"
                        + " but found: `" + type + "`."
                );
            }

            if (typeSymbol.getReferences().isEmpty()) {
                return getPlaceholder(typeSymbol);
            }

            // Add type references as type references (ex. `Map<KeyType, ValueType>`)
            putContext("refs", typeSymbol.getReferences());
            String output = format(
                "$L<${#refs}${value:B}${^key.last}, ${/key.last}${/refs}>",
                getPlaceholder(typeSymbol)
            );
            removeContext("refs");
            return output;
        }

        private String getPlaceholder(Symbol symbol) {
            // Add symbol to import container and symbol table
            var normalizedSymbol = normalizeSymbol(symbol);
            addImport(normalizedSymbol);
            addToSymbolTable(normalizedSymbol);

            // Return a placeholder value that will be filled when toString is called
            // [] is replaced with "Array" to ensure array types don't break formatter.
            return format("$${$L:L}", symbol.getFullName().replace("[]", "Array"));
        }
    }

    /**
     * Implements a formatter for {@code $B} that formats Java types, preferring a boxed version of a type if available.
     */
    private final class BoxedTypeFormatter implements BiFunction<Object, String, String> {
        private final JavaTypeFormatter javaTypeFormatter = new JavaTypeFormatter();

        @Override
        public String apply(Object type, String indent) {
            Symbol typeSymbol;
            if (type instanceof Symbol s) {
                typeSymbol = s;
            } else if (type instanceof Class<?> c) {
                typeSymbol = CodegenUtils.fromClass(c);
            } else if (type instanceof SymbolReference r) {
                typeSymbol = r.getSymbol();
            } else {
                throw new IllegalArgumentException(
                    "Invalid type provided for $B. Expected a Symbol or Class"
                        + " but found: `" + type + "`."
                );
            }

            if (typeSymbol.getProperty(SymbolProperties.BOXED_TYPE).isPresent()) {
                typeSymbol = typeSymbol.expectProperty(SymbolProperties.BOXED_TYPE);
            }

            return javaTypeFormatter.apply(typeSymbol, indent);
        }
    }

    /**
     * Implements a formatter for {@code $U} that capitalizes the first letter of a string literal.
     */
    private static final class CapitalizingFormatter implements BiFunction<Object, String, String> {
        @Override
        public String apply(Object type, String indent) {
            if (type instanceof String s) {
                return StringUtils.capitalize(s);
            }
            throw new IllegalArgumentException(
                "Invalid type provided for $U. Expected a String but found: `"
                    + type + "`."
            );
        }
    }
}
