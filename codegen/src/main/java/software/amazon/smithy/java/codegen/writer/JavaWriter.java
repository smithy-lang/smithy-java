/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.writer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolReference;
import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.SymbolUtils;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyUnstableApi;
import software.amazon.smithy.utils.StringUtils;

/**
 * Writer for java code generation
 * TODO: update docs
 */
@SmithyUnstableApi
public class JavaWriter extends DeferredSymbolWriter<JavaWriter, JavaImportContainer> {
    private static final int MAX_LINE_LENGTH = 120;
    private static final Pattern PATTERN = Pattern.compile("<([a-z]+)*>.*?</\\1>", Pattern.DOTALL);
    private final Map<String, Set<Symbol>> symbolTable = new HashMap<>();
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
            getHeader(),
            packageNamespace,
            getImportContainer(),
            super.toString()
        );
    }

    // TODO: make this take a runnable
    public void openDocstring() {
        pushState().writeWithNoFormatting("/**");
    }

    public void newLine() {
        writeInlineWithNoFormatting(getNewline());
    }

    public void writeDocStringContents(String contents) {
        // Split out any HTML-tag wrapped sections as we do not want to wrap
        // any customer documentation with tags
        Matcher matcher = PATTERN.matcher(contents);
        int lastMatchPos = 0;
        writeInlineWithNoFormatting(" * ");
        while (matcher.find()) {
            // write all contents up to the match.
            writeInlineWithNoFormatting(
                StringUtils.wrap(
                    contents.substring(lastMatchPos, matcher.start())
                        .replace("\n", "\n * "),
                    MAX_LINE_LENGTH - 8,
                    getNewline() + " * ",
                    false
                )
            );
            // write match contents
            writeInlineWithNoFormatting(contents.substring(matcher.start(), matcher.end()).replace("\n", "\n * "));
            lastMatchPos = matcher.end();
        }
        // Write out all remaining contents
        writeWithNoFormatting(
            StringUtils.wrap(
                contents.substring(lastMatchPos).replace("\n", "\n * "),
                MAX_LINE_LENGTH - 8,
                getNewline() + " * ",
                false
            )
        );
    }

    public void writeDocStringContents(String contents, Object... args) {
        writeInlineWithNoFormatting(" * ");
        write(
            StringUtils.wrap(
                contents.replace("\n", "\n * "),
                MAX_LINE_LENGTH - 8,
                getNewline() + " * ",
                false
            ),
            args
        );
    }

    public void closeDocstring() {
        writeWithNoFormatting(" */").popState();
    }

    public String getHeader() {
        StringBuilder builder = new StringBuilder().append("/**").append(getNewline());
        for (String line : settings.headerLines()) {
            builder.append(" * ").append(line).append(getNewline());
        }
        builder.append(" */");
        return builder.toString();
    }

    private void putNameContext() {
        for (Set<Symbol> duplicates : symbolTable.values()) {
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
            return new JavaWriter(settings, namespace);
        }
    }


    /**
     * Implements a formatter for {@code $T} that formats Java types.
     */
    private final class JavaTypeFormatter implements BiFunction<Object, String, String> {
        @Override
        public String apply(Object type, String indent) {
            Symbol typeSymbol = switch (type) {
                case Symbol s -> s;
                case Class<?> c -> SymbolUtils.fromClass(c);
                case SymbolReference r -> r.getSymbol();
                default -> throw new IllegalArgumentException(
                    "Invalid type provided for $T. Expected a Symbol or Class"
                        + " but found: `" + type + "`."
                );
            };

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
            // Add symbol to import container
            addImport(symbol);

            // Add symbol to symbol map, so we can handle potential type name conflicts
            Set<Symbol> nameSet = symbolTable.computeIfAbsent(symbol.getName(), n -> new HashSet<>());
            nameSet.add(symbol);

            // Return a placeholder value that will be filled when toString is called
            // [] is replaced with "Array" to ensure array types dont break formatter.
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
            Symbol typeSymbol = switch (type) {
                case Symbol s -> s;
                case Class<?> c -> SymbolUtils.fromClass(c);
                case SymbolReference r -> r.getSymbol();
                default -> throw new IllegalArgumentException(
                    "Invalid type provided for $B. Expected a Symbol or Class"
                        + " but found: `" + type + "`."
                );
            };

            if (typeSymbol.getProperty(SymbolProperties.BOXED_TYPE).isPresent()) {
                typeSymbol = typeSymbol.expectProperty(SymbolProperties.BOXED_TYPE, Symbol.class);
            }

            return javaTypeFormatter.apply(typeSymbol, indent);
        }
    }

    /**
     * Writes the ID string constant for a shape class.
    
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
}
