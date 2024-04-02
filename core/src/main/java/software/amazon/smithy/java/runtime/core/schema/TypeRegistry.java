/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Supplier;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyBuilder;

/**
 * Creates unknown shapes and polymorphic shapes using a shape registry.
 */
public final class TypeRegistry {

    public record Entry<T extends SerializableShape>(Class<T> type, Supplier<SdkShapeBuilder<T>> supplier) {
    };

    private final Map<ShapeId, Entry<?>> supplierMap;

    private TypeRegistry(Builder builder) {
        this.supplierMap = builder.supplierMap;
        builder.supplierMap = new HashMap<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("unchecked")
    public <T extends SerializableShape> Optional<SdkShapeBuilder<T>> create(ShapeId shapeId, Class<T> type) {
        var mapping = supplierMap.get(shapeId);
        if (mapping == null) {
            return Optional.empty();
        } else if (type.isAssignableFrom(mapping.type)) {
            return Optional.ofNullable((SdkShapeBuilder<T>) mapping.supplier.get());
        } else {
            throw new NoSuchElementException("Polymorphic shape " + shapeId + " is not compatible with " + type);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<SmithyBuilder<T>> create(
        ShapeId shapeId,
        Class<T> type,
        SdkSchema knownSchema,
        Supplier<SmithyBuilder<T>> supplier
    ) {
        if (shapeId.equals(knownSchema.id())) {
            return Optional.of(supplier.get());
        }
        var mapping = supplierMap.get(shapeId);
        if (mapping == null) {
            return Optional.empty();
        } else if (mapping.type.isAssignableFrom(type)) {
            return Optional.ofNullable((SmithyBuilder<T>) mapping.supplier);
        } else {
            throw new NoSuchElementException("Polymorphic shape " + shapeId + " is not compatible with " + type);
        }
    }

    public static final class Builder implements SmithyBuilder<TypeRegistry> {

        private Map<ShapeId, Entry<?>> supplierMap = new HashMap<>();

        private Builder() {
        }

        @Override
        public TypeRegistry build() {
            return new TypeRegistry(this);
        }

        public <T extends SerializableShape> Builder putType(
            ShapeId shapeId,
            Class<T> type,
            Supplier<SdkShapeBuilder<T>> supplier
        ) {
            supplierMap.put(shapeId, new Entry<>(type, supplier));
            return this;
        }

        public Builder putAllTypes(TypeRegistry... others) {
            for (TypeRegistry other : others) {
                supplierMap.putAll(other.supplierMap);
            }
            return this;
        }
    }
}
