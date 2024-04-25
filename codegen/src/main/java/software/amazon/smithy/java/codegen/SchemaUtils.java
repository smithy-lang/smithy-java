/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.util.Locale;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.model.loader.Prelude;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.CaseUtils;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Provides various utility functions around SDK schemas
 */
@SmithyInternalApi
public final class SchemaUtils {
    private static final String SCHEMA_STATIC_NAME = "SCHEMA";

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

    private SchemaUtils() {}
}
