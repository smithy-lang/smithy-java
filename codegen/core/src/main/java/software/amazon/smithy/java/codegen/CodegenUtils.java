/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import software.amazon.smithy.codegen.core.ReservedWords;
import software.amazon.smithy.codegen.core.ReservedWordsBuilder;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.Prelude;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.StreamingTrait;
import software.amazon.smithy.utils.CaseUtils;
import software.amazon.smithy.utils.SmithyInternalApi;
import software.amazon.smithy.utils.StringUtils;

/**
 * Provides utility methods for generating Java code.
 */
@SmithyInternalApi
public final class CodegenUtils {
    private static final URL RESERVED_WORDS_FILE = Objects.requireNonNull(
        CodegenUtils.class.getResource("reserved-words.txt")
    );

    private static final String SCHEMA_STATIC_NAME = "SCHEMA";
    public static final ReservedWords SHAPE_ESCAPER = new ReservedWordsBuilder()
        .loadCaseInsensitiveWords(RESERVED_WORDS_FILE, word -> word + "Shape")
        .build();
    public static final ReservedWords MEMBER_ESCAPER = new ReservedWordsBuilder()
        .loadCaseInsensitiveWords(RESERVED_WORDS_FILE, word -> word + "Member")
        .build();

    private CodegenUtils() {
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
            .putProperty(SymbolProperties.IS_PRIMITIVE, clazz.isPrimitive())
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
            .putProperty(SymbolProperties.IS_PRIMITIVE, true)
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

    /**
     * Determines the name to use for the Schema constant for a member.
     *
     * @param memberName Member shape to generate schema name from
     * @return name to use for static schema property
     */
    public static String toMemberSchemaName(String memberName) {
        return SCHEMA_STATIC_NAME + "_" + CaseUtils.toSnakeCase(memberName).toUpperCase(Locale.ENGLISH);
    }

    /**
     * Determines the name to use for shape Schemas.
     *
     * @param shape shape to generate name for
     * @return name to use for static schema property
     */
    public static String toSchemaName(Shape shape) {
        // Shapes that generate their own classes have a static name
        if (shape.isOperationShape() || shape.isStructureShape()) {
            return SCHEMA_STATIC_NAME;
        }
        return CaseUtils.toSnakeCase(shape.toShapeId().getName()).toUpperCase(Locale.ENGLISH);
    }

    /**
     * Writes the schema property to use for a given shape.
     *
     * <p>If a shape is a prelude shape then it will use a property from {@link PreludeSchemas}.
     * Otherwise, the shape will use the generated {@code SharedSchemas} utility class.
     *
     * @param writer Writer to use for writing the Schema type.
     * @param shape shape to write Schema type for.
     */
    public static String getSchemaType(JavaWriter writer, SymbolProvider provider, Shape shape) {
        if (Prelude.isPreludeShape(shape)) {
            return writer.format("$T.$L", PreludeSchemas.class, shape.getType().name());
        } else if (shape.isStructureShape() || shape.isUnionShape()) {
            // Shapes that generate a class have their schemas as static properties on that class
            return writer.format("$T.$L", provider.toSymbol(shape), toSchemaName(shape));
        }
        return writer.format("SharedSchemas.$L", toSchemaName(shape));
    }

    /**
     * Sorts shape members to ensure that required members with no default value come before other members.
     *
     * @param shape Shape to sort members of
     * @return list of sorted members
     */
    public static List<MemberShape> getSortedMembers(Shape shape) {
        return shape.members()
            .stream()
            .sorted(
                (a, b) -> {
                    if (isRequiredWithNoDefault(a) && !isRequiredWithNoDefault(b)) {
                        return -1;
                    } else if (isRequiredWithNoDefault(a)) {
                        return 0;
                    } else {
                        return 1;
                    }
                }
            )
            .collect(Collectors.toList());
    }

    /**
     * Checks if a member is required and has no default.
     *
     * <p>Required members with no default require presence tracking.
     *
     * @param memberShape member shape to check
     * @return true if the member has the required trait and does not have a default.
     */
    public static boolean isRequiredWithNoDefault(MemberShape memberShape) {
        return memberShape.isRequired() && !memberShape.hasNonNullDefault();
    }
}
