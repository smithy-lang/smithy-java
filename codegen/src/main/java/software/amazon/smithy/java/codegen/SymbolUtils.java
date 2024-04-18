/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.net.URL;
import software.amazon.smithy.codegen.core.ReservedWords;
import software.amazon.smithy.codegen.core.ReservedWordsBuilder;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.StreamingTrait;
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
     * Gets a Symbol for a class with both a boxed and unboxed variant.
     *
     * @param boxed Boxed variant of class
     * @param unboxed Unboxed variant of class
     * @return Symbol representing java class
     */
    public static Symbol fromBoxedClass(Class<?> unboxed, Class<?> boxed) {
        return fromClass(unboxed).toBuilder()
            .putProperty(SymbolProperties.PRIMITIVE, true)
            .putProperty(SymbolProperties.BOXED_TYPE, fromClass(boxed))
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

    /**
     * Determines if a shape is a streaming blob.
     *
     * @param shape shape to check
     * @return returns true if the shape is a streaming blob
     */
    public static boolean isStreamingBlob(Shape shape) {
        return shape.isBlobShape() && shape.hasTrait(StreamingTrait.class);
    }

    /**
     * Checks if a symbol resolves to a Java Array type.
     *
     * @param symbol symbol to check
     * @return true if symbol resolves to a Java Array
     */
    public static boolean isJavaArray(Symbol symbol) {
        return symbol.getProperty(SymbolProperties.IS_JAVA_ARRAY).isPresent();
    }

    /**
     * Determines if a given member represents a nullable type
     *
     * @param shape member to check for nullability
     *
     * @return if the shape is a nullable type
     */
    public static boolean isNullableMember(MemberShape shape) {
        return !shape.isRequired() && !shape.hasNonNullDefault();
    }

    /**
     * Determines if a member targets a Map or List shape.
     *
     * @param model model used for code generation
     * @param member Shape to test
     * @return true if shape targets list or map shape
     */
    public static boolean targetsCollection(Model model, MemberShape member) {
        var target = model.expectShape(member.getTarget());
        return target.isListShape() || target.isMapShape();
    }
}
