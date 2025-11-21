/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import software.amazon.smithy.model.shapes.ShapeId;

public abstract class SchemaIndex {

    private static final SchemaIndex COMBINED_SCHEMA_INDEX = createCombinedSchemaIndex();

    private static SchemaIndex createCombinedSchemaIndex() {
        return new CombinedSchemaIndex(
                ServiceLoader.load(SchemaIndex.class).stream().map(ServiceLoader.Provider::get).toList());
    }

    public static SchemaIndex getCombinedSchemaIndex() {
        return COMBINED_SCHEMA_INDEX;
    }

    public abstract Schema getSchema(ShapeId id);

    public abstract void visit(Consumer<Schema> visitor);

    private static final class CombinedSchemaIndex extends SchemaIndex {

        private final List<SchemaIndex> indexes;

        public CombinedSchemaIndex(List<SchemaIndex> indexes) {
            this.indexes = indexes;
        }

        @Override
        public Schema getSchema(ShapeId id) {
            for (var index : indexes) {
                var schema = index.getSchema(id);
                if (schema != null) {
                    return schema;
                }
            }
            throw new IllegalArgumentException("No schema found for id `" + id + "`");
        }

        @Override
        public void visit(Consumer<Schema> visitor) {
            for (var index : indexes) {
                index.visit(visitor);
            }
        }
    }

    public static SchemaIndex compose(SchemaIndex... indexes) {
        return new CombinedSchemaIndex(List.of(indexes));
    }
}
