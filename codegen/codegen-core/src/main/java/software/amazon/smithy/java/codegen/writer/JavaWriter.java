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
import software.amazon.smithy.utils.SmithyUnstableApi;
import software.amazon.smithy.utils.StringUtils;

/**
 * Writer for java code generation
 *
 * <p>This writer provides the following formatters:
 * <ul>
 *  <li>Use the {@code $T} formatter to refer to {@link Symbol}s.</li>
 *  <li>Use the {@code $B} formatter to use a boxed type (such as {@code Integer}) if applicable.</li>
 *  <li>Use the {@code $N} formatter to render a {@link Symbol} with a non-null annotation added if applicable.</li>
 *  <li>Use the {@code $U} formatter to write a string literal with the first letter capitalized.</li>
 * </ul>
 */
@SmithyUnstableApi
public final class JavaWriter extends DeferredSymbolWriter<JavaWriter, JavaImportContainer> {
    private static final char PLACEHOLDER_FORMAT_CHAR = '£';
    private final String packageNamespace;
    private final JavaCodegenSettings settings;
    private final String filename;

    public JavaWriter(JavaCodegenSettings settings, String packageNamespace, String filename) {
        super(new JavaImportContainer(packageNamespace));

        this.packageNamespace = packageNamespace;
        this.settings = settings;
        this.filename = filename;

        // Ensure extraneous white space is trimmed
        trimBlankLines();
        trimTrailingSpaces();

        // Formatters
        putFormatter('T', new JavaTypeFormatter());
        putFormatter('B', new BoxedTypeFormatter());
        putFormatter('U', new CapitalizingFormatter());
        putFormatter('N', new NonNullAnnotationFormatter());
    }

    // Java does not support aliases, so just import normally
    private void addImport(Symbol symbol) {
        addImport(symbol, symbol.getName());
    }

    @Override
    public String toString() {
        // Do not add headers or attempt symbol resolution for resource files
        if (filename.startsWith("META-INF")) {
            return super.toString();
        }

        putNameContext();
        setExpressionStart(PLACEHOLDER_FORMAT_CHAR);
        return format(
                """
                        £L
                        package £L;

                        £L
                        £L
                        """,
                settings.header(),
                packageNamespace,
                getImportContainer(),
                super.toString());
    }

    public void newLine() {
        writeInlineWithNoFormatting(getNewline());
    }

    private void putNameContext() {
        // Add any implicit usages from classes in the same package
        var packageSymbols = settings.getGeneratedSymbolsPackage(packageNamespace);
        packageSymbols.forEach(this::addToSymbolTable);
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
            return new JavaWriter(settings, namespace, filename);
        }
    }

    /**
     * Implements a formatter for {@code $T} that formats Java types.
     */
    private final class JavaTypeFormatter implements BiFunction<Object, String, String> {
        @Override
        public String apply(Object type, String indent) {
            Symbol typeSymbol = getTypeSymbol(type, 'T');

            if (typeSymbol.getReferences().isEmpty()) {
                return getPlaceholder(typeSymbol);
            }

            // Add type references as type references (ex. `Map<KeyType, ValueType>`)
            putContext("refs", typeSymbol.getReferences());
            String output = format(
                    "$L<${#refs}${value:B}${^key.last}, ${/key.last}${/refs}>",
                    getPlaceholder(typeSymbol));
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
            return format("$L{$L:L}", PLACEHOLDER_FORMAT_CHAR, symbol.getFullName().replace("[]", "Array"));
        }
    }

    /**
     * Implements a formatter for {@code $B} that formats Java types, preferring a boxed version of a type if available.
     */
    private final class BoxedTypeFormatter implements BiFunction<Object, String, String> {
        private final JavaTypeFormatter javaTypeFormatter = new JavaTypeFormatter();

        @Override
        public String apply(Object type, String indent) {
            Symbol typeSymbol = getTypeSymbol(type, 'B');

            if (typeSymbol.getProperty(SymbolProperties.BOXED_TYPE).isPresent()) {
                typeSymbol = typeSymbol.expectProperty(SymbolProperties.BOXED_TYPE);
            }

            return javaTypeFormatter.apply(typeSymbol, indent);
        }
    }

    private static Symbol getTypeSymbol(Object type, char formatChar) {
        return switch (type) {
            case Symbol s -> s;
            case Class<?> c -> CodegenUtils.fromClass(c);
            case SymbolReference r -> r.getSymbol();
            case null, default -> throw new IllegalArgumentException(
                    "Invalid type provided for " + formatChar + ". Expected a Symbol or Class"
                            + " but found: `" + type + "`.");
        };
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
                            + type + "`.");
        }
    }

    /**
     * Implements a formatter for {@code $N} that adds non-null annotation
     */
    private final class NonNullAnnotationFormatter implements BiFunction<Object, String, String> {
        private final JavaTypeFormatter javaTypeFormatter = new JavaTypeFormatter();

        @Override
        public String apply(Object type, String indent) {

            Symbol nonNullAnnotationSymbol = settings.nonNullAnnotationSymbol();

            if (nonNullAnnotationSymbol == null) {
                return javaTypeFormatter.apply(type, indent);
            }

            Symbol typeSymbol = getTypeSymbol(type, 'N');

            if (typeSymbol.expectProperty(SymbolProperties.IS_PRIMITIVE)) {
                return javaTypeFormatter.apply(typeSymbol, indent);
            }

            return format("@$T $T", nonNullAnnotationSymbol, typeSymbol);
        }
    }
}
