/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.net.URL;
import software.amazon.smithy.codegen.core.ReservedWords;
import software.amazon.smithy.codegen.core.ReservedWordsBuilder;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.CaseUtils;
import software.amazon.smithy.utils.SmithyInternalApi;
import software.amazon.smithy.utils.StringUtils;

@SmithyInternalApi
public final class SymbolUtils {
    public static final URL RESERVED_WORDS_FILE = SymbolUtils.class.getResource("reserved-words.txt");

    public static final ReservedWords SHAPE_ESCAPER = new ReservedWordsBuilder()
        .loadCaseInsensitiveWords(RESERVED_WORDS_FILE, word -> word + "Shape")
        .build();
    public static final ReservedWords MEMBER_ESCAPER = new ReservedWordsBuilder()
        .loadCaseInsensitiveWords(RESERVED_WORDS_FILE, word -> word + "Member")
        .build();

    private SymbolUtils() {
        // Utility class should not be instantiated
    }

    /**
     * Gets a Smithy codegen {@link Symbol} for a Java class.
     *
     * @param clazz class to get symbol for.
     * @return Symbol representing the provided class.
     */
    public static Symbol fromClass(Class<?> clazz) {
        return Symbol.builder()
            .name(clazz.getSimpleName())
            .namespace(clazz.getCanonicalName().replace("." + clazz.getSimpleName(), ""), ".")
            .putProperty(SymbolProperties.PRIMITIVE, clazz.isPrimitive())
            .build();
    }

    /**
     * TODO: update
     * @param boxed
     * @param unboxed
     * @return
     */
    public static Symbol fromBoxedClass(Class<?> boxed, Class<?> unboxed) {
        return fromClass(boxed).toBuilder()
            .putProperty(SymbolProperties.UNBOXED, fromClass(unboxed))
            .build();
    }

    /**
     * Gets the default class name to use for a given Smithy {@link Shape}.
     *
     * @param shape Shape to get name for.
     * @return Default name.
     */
    public static String getDefaultName(Shape shape, ServiceShape service) {
        String baseName = shape.getId().getName(service);

        // If the name contains any problematic delimiters, use PascalCase converter,
        // otherwise, just capitalize first letter to avoid messing with user-defined
        // capitalization.
        String unescaped;
        if (baseName.contains("_")) {
            unescaped = CaseUtils.toPascalCase(shape.getId().getName());
        } else {
            unescaped = StringUtils.capitalize(baseName);
        }

        return SHAPE_ESCAPER.escape(unescaped);
    }


}
