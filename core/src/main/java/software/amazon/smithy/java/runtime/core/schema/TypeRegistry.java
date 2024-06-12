/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyBuilder;

/**
 * Creates shapes and polymorphic shapes using a shape registry of shape IDs to builders.
 */
public final class TypeRegistry {

    private record Entry<T extends SerializableShape>(Class<T> type, Supplier<ShapeBuilder<T>> supplier) {}

    private final Map<ShapeId, Entry<?>> supplierMap;

    private TypeRegistry(Builder builder) {
        this.supplierMap = builder.supplierMap;
        builder.supplierMap = new HashMap<>();
    }

    /**
     * Create a builder to create a TypeRegistry.
     *
     * @return the created builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a shape builder only on the shape ID.
     *
     * @param shapeId Shape ID to attempt to create.
     * @return the optionally created builder.
     */
    public Optional<ShapeBuilder<?>> create(ShapeId shapeId) {
        var mapping = supplierMap.get(shapeId);
        if (mapping == null) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(mapping.supplier.get());
        }
    }

    /**
     * Create a shape builder based on a shape ID and expected type.
     *
     * @param shapeId Shape ID to attempt to create.
     * @param type    Shape class.
     * @return the optionally created builder.
     * @param <T> Shape type.
     * @throws SerializationException if the given type isn't compatible with the shape in the registry.
     */
    @SuppressWarnings("unchecked")
    public <T extends SerializableShape> Optional<ShapeBuilder<T>> create(ShapeId shapeId, Class<T> type) {
        var mapping = supplierMap.get(shapeId);
        if (mapping == null) {
            return Optional.empty();
        } else if (type.isAssignableFrom(mapping.type)) {
            return Optional.ofNullable((ShapeBuilder<T>) mapping.supplier.get());
        } else {
            throw new SerializationException("Polymorphic shape " + shapeId + " is not compatible with " + type);
        }
    }

    /**
     * Builder used to create a {@link TypeRegistry}.
     */
    public static final class Builder implements SmithyBuilder<TypeRegistry> {

        private Map<ShapeId, Entry<?>> supplierMap = new HashMap<>();

        private Builder() {}

        @Override
        public TypeRegistry build() {
            return new TypeRegistry(this);
        }

        /**
         * Put a shape into the registry.
         *
         * @param shapeId  ID of the shape.
         * @param type     Shape class.
         * @param supplier Supplier to create a new builder for this shape.
         * @return the builder.
         * @param <T> shape type.
         */
        public <T extends SerializableShape> Builder putType(
            ShapeId shapeId,
            Class<T> type,
            Supplier<ShapeBuilder<T>> supplier
        ) {
            supplierMap.put(shapeId, new Entry<>(type, supplier));
            return this;
        }

        /**
         * Put all the types contained in other registries into this registry.
         *
         * @param others Registries to copy.
         * @return the builder.
         */
        public Builder putAllTypes(TypeRegistry... others) {
            for (TypeRegistry other : others) {
                supplierMap.putAll(other.supplierMap);
            }
            return this;
        }
    }
}
