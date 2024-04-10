/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.util.Locale;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.model.loader.Prelude;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ToShapeId;
import software.amazon.smithy.utils.CaseUtils;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Provides various utility functions around SDK schemas
 */
@SmithyInternalApi
public final class SchemaUtils {

    /**
     * Determines the name to use for the Schema constant for a member.
     *
     * @param memberName Member shape to generate schema name from
     * @return name to use for static schema property
     */
    public static String toMemberSchemaName(String memberName) {
        return "SCHEMA_" + CaseUtils.toSnakeCase(memberName).toUpperCase(Locale.ROOT);
    }

    /**
     * Determines the name to use for shape Schemas.
     *
     * @param toShapeId shape to generate name for
     * @return name to use for static schema property
     */
    public static String toSchemaName(ToShapeId toShapeId) {
        return CaseUtils.toSnakeCase(toShapeId.toShapeId().getName()).toUpperCase(Locale.ROOT);
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
    public static void writeSchemaType(JavaWriter writer, Shape shape) {
        if (Prelude.isPreludeShape(shape)) {
            writer.write("$T.$L", PreludeSchemas.class, shape.getType().name());
        } else {
            writer.write("SharedSchemas.$L", toSchemaName(shape));
        }
    }

    private SchemaUtils() {}
}
