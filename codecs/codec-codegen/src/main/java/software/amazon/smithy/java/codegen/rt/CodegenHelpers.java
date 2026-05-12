/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.rt;

import java.lang.invoke.MethodHandles;
import software.amazon.smithy.java.core.schema.Schema;

/**
 * Format-independent runtime helpers for generated serializer code.
 */
public final class CodegenHelpers {

    private CodegenHelpers() {}

    private static final ClassValue<Schema> SCHEMA_CACHE = new ClassValue<>() {
        @Override
        protected Schema computeValue(Class<?> type) {
            try {
                return (Schema) MethodHandles.publicLookup()
                        .findStaticGetter(type, "$SCHEMA", Schema.class)
                        .invoke();
            } catch (Throwable e) {
                throw new RuntimeException("Cannot access $SCHEMA on " + type.getName(), e);
            }
        }
    };

    public static Schema schemaFor(Class<?> shapeClass) {
        return SCHEMA_CACHE.get(shapeClass);
    }
}
