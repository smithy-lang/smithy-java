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
import software.amazon.smithy.model.traits.UnitTypeTrait;
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

    public static final ReservedWords SHAPE_ESCAPER = new ReservedWordsBuilder()
        .loadCaseInsensitiveWords(RESERVED_WORDS_FILE, word -> word + "Shape")
        .build();
    public static final ReservedWords MEMBER_ESCAPER = new ReservedWordsBuilder()
        .loadCaseInsensitiveWords(RESERVED_WORDS_FILE, word -> word + "Member")
        .build();

    private static final String SCHEMA_STATIC_NAME = "SCHEMA";

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
            .putProperty(SymbolProperties.REQUIRES_STATIC_DEFAULT, true)
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
            .putProperty(SymbolProperties.REQUIRES_STATIC_DEFAULT, false)
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
            var shapeName = shape.hasTrait(UnitTypeTrait.class) ? "UNIT" : shape.getType().name();
            return writer.format("$T.$L", PreludeSchemas.class, shapeName);
        } else if (shape.isStructureShape() || shape.isUnionShape() || shape.isIntEnumShape() || shape.isEnumShape()) {
            // Shapes that generate a class have their schemas as static properties on that class
            return writer.format("$T.SCHEMA", provider.toSymbol(shape));
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

    /**
     * Checks if a member shape requires a null check in the builder setter.
     *
     * <p>Non-primitive, required members need a null check.
     */
    public static boolean requiresSetterNullCheck(SymbolProvider provider, MemberShape memberShape) {
        return (memberShape.isRequired() || memberShape.hasNonNullDefault())
            && !provider.toSymbol(memberShape).expectProperty(SymbolProperties.IS_PRIMITIVE);
    }

    /**
     * Primitives (excluding blobs) use the Java default for error correction and so do not need to be set.
     *
     * <p>Documents are also not set because they use a null default for error correction.
     *
     * @see <a href="https://smithy.io/2.0/spec/aggregate-types.html#client-error-correction">client error correction</a>
     * @return true if the member shape has a builtin default
     */
    public static boolean hasBuiltinDefault(SymbolProvider provider, Model model, MemberShape memberShape) {
        var target = model.expectShape(memberShape.getTarget());
        return (provider.toSymbol(memberShape).expectProperty(SymbolProperties.IS_PRIMITIVE)
            || target.isDocumentShape())
            && !target.isBlobShape();
    }

    /**
     * Gets the name to use when defining the default value of a member.
     *
     * @param memberName name of member to get default name for.
     * @return Upper snake case name of default
     */
    public static String toDefaultValueName(String memberName) {
        return CaseUtils.toSnakeCase(memberName).toUpperCase(Locale.ENGLISH) + "_DEFAULT";
    }

    /**
     * Gets the file name to use for the SharedSerde utility class
     *
     * @param settings Settings to use for package namespace
     * @return serde file name
     */
    public static String getSerdeFileName(JavaCodegenSettings settings) {
        return String.format("./%s/model/SharedSerde.java", settings.packageNamespace().replace(".", "/"));
    }

    /**
     * Gets the namespace to use for generated pojo files
     *
     * @param settings Settings to use for package namespace
     * @return schema file namespace
     */
    public static String getModelNamespace(JavaCodegenSettings settings) {
        return settings.packageNamespace() + ".model";
    }

    /**
     * Gets the uppers snake case name to use for an enum variant for a member shape.
     *
     * @param provider symbol provider
     * @param memberShape member shape to get enum name for
     */
    public static String getEnumVariantName(SymbolProvider provider, MemberShape memberShape) {
        return toUpperSnakeCase(provider.toMemberName(memberShape));
    }

    /**
     * Converts a string from camel-case or pascal-case to upper snake-case.
     *
     * <p>For example {@code MyString} would be converted to {@code MY_STRING}.
     *
     * @param string String to convert to upper snake case
     * @return Upper snake-case string
     */
    public static String toUpperSnakeCase(String string) {
        return CaseUtils.toSnakeCase(string).toUpperCase(Locale.ENGLISH);
    }

    /**
     * Gets a symbol representing a nested {@code Type} enum for the given root symbol.
     *
     * @return Symbol representing a nested {@code Type} enum
     */
    public static Symbol getInnerTypeEnumSymbol(Symbol symbol) {
        return symbol.toBuilder()
            .name("Type")
            .build();
    }
}
