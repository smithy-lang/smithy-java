/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.rt;

import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.ShapeBuilder;

/**
 * Format-independent runtime helpers for generated deserializer code.
 */
public final class CodegenDeserializeHelpers {

    private CodegenDeserializeHelpers() {}

    public static Object tryDeserializeNested(
            Object ctx,
            SpecializedCodecRegistry registry,
            Class<?> structClass
    ) {
        Schema schema = CodegenHelpers.schemaFor(structClass);
        GeneratedStructDeserializer de = registry.getDeserializer(schema, structClass);
        if (de != null) {
            ShapeBuilder<?> builder = schema.shapeBuilder();
            return de.deserialize(ctx, builder);
        }
        return null;
    }

    public static Object tryDeserializeNestedDirect(
            Object ctx,
            Object[] cached,
            Class<?> structClass,
            SpecializedCodecRegistry registry
    ) {
        GeneratedStructDeserializer de = (GeneratedStructDeserializer) cached[0];
        Schema schema = (Schema) cached[1];
        if (de == null) {
            schema = CodegenHelpers.schemaFor(structClass);
            de = registry.getDeserializer(schema, structClass);
            if (de != null) {
                cached[0] = de;
                cached[1] = schema;
            } else {
                return null;
            }
        }
        ShapeBuilder<?> builder = schema.shapeBuilder();
        return de.deserialize(ctx, builder);
    }
}
