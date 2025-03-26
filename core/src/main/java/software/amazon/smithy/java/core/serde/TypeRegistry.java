/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.document.DiscriminatorException;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Supports on-demand deserialization of types by providing a registry of shape IDs to shape builders.
 */
public interface TypeRegistry {
    TypeRegistry EMPTY = TypeRegistry.builder().build();

    /**
     * Gets the shape class registered for the given shape ID.
     *
     * @param shapeId Shape ID to check.
     * @return the shape class registered for this ID, or null if not found.
     */
    Class<? extends SerializableStruct> getShapeClass(ShapeId shapeId);

    /**
     * Create a shape builder only on the shape ID.
     *
     * @param shapeId Shape ID to attempt to create.
     * @return the created builder, or null if no matching builder was found.
     */
    ShapeBuilder<?> createBuilder(ShapeId shapeId);

    /**
     * Returns true if this type registry contains the specified {@link ShapeId}.
     *
     * @param shapeId shapeId to check
     * @return true if this type registry contains the {@code ShapeId}
     */
    boolean contains(ShapeId shapeId);

    /**
     * Get an empty TypeRegistry.
     *
     * @return the empty TypeRegistry.
     */
    static TypeRegistry empty() {
        return EMPTY;
    }

    /**
     * Create a shape builder based on a shape ID and expected type.
     *
     * @param shapeId Shape ID to attempt to create.
     * @param type The expected class of the created shape.
     * @return the created builder, or null if no matching builder was found.
     * @param <T> Shape type to create.
     * @throws SerializationException if the given type isn't compatible with the shape in the registry.
     */
    @SuppressWarnings("unchecked")
    default <T extends SerializableStruct> ShapeBuilder<T> createBuilder(ShapeId shapeId, Class<T> type) {
        var builder = createBuilder(shapeId);
        if (builder == null) {
            return null;
        }

        var expectedType = getShapeClass(shapeId);
        if (!type.isAssignableFrom(expectedType)) {
            throw new SerializationException("Polymorphic shape " + shapeId + " is not compatible with " + type);
        }

        return (ShapeBuilder<T>) builder;
    }

    /**
     * Deserializes a document into a shape from the registry.
     *
     * @param document Document to deserialize.
     * @return the deserialized shape.
     * @throws DiscriminatorException if the document has no discriminator.
     * @throws UnsupportedOperationException if the document doesn't match a registered shape.
     */
    default SerializableStruct deserialize(Document document) {
        var shapeId = document.discriminator();
        var builder = createBuilder(shapeId, SerializableStruct.class);
        if (builder == null) {
            throw new UnsupportedOperationException(
                    "Cannot find a registered shape to deserialize document: "
                            + shapeId);
        }
        return document.asShape(builder);
    }

    /**
     * Compose multiple type registries together.
     *
     * @param first First type registry to check.
     * @param second Subsequent type registry to check.
     * @return the composed type registry.
     */
    static TypeRegistry compose(TypeRegistry first, TypeRegistry second) {
        if (first instanceof TypeRegistry.Builder.DefaultRegistry dt) {
            if (dt.supplierMap.isEmpty()) {
                return second;
            }
        }

        if (second instanceof TypeRegistry.Builder.DefaultRegistry dt) {
            if (dt.supplierMap.isEmpty()) {
                return first;
            }
        }

        return new TypeRegistry() {
            @Override
            public Class<? extends SerializableStruct> getShapeClass(ShapeId shapeId) {
                var result = first.getShapeClass(shapeId);
                if (result != null) {
                    return result;
                }
                return second.getShapeClass(shapeId);
            }

            @Override
            public ShapeBuilder<?> createBuilder(ShapeId shapeId) {
                var result = first.createBuilder(shapeId);
                if (result != null) {
                    return result;
                }
                return second.createBuilder(shapeId);
            }

            @Override
            public boolean contains(ShapeId shapeId) {
                return first.contains(shapeId) || second.contains(shapeId);
            }
        };
    }

    /**
     * Build up a TypeRegistry.
     *
     * @return the type registry builder.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder used to create a type registry.
     */
    final class Builder {

        private interface Entry<T extends SerializableStruct> {
            Class<T> type();

            Supplier<ShapeBuilder<T>> builderSupplier();
        }

        private record EagerEntry<T extends SerializableStruct>(
                Class<T> type,
                Supplier<ShapeBuilder<T>> builderSupplier) implements Entry<T> {}

        private record LazyEntry<T extends SerializableStruct>(
                Supplier<Class<T>> typeSupplier,
                Supplier<Supplier<ShapeBuilder<T>>> lazyBuilderSupplier) implements Entry<T> {
            @Override
            public Class<T> type() {
                return typeSupplier.get();
            }

            @Override
            public Supplier<ShapeBuilder<T>> builderSupplier() {
                return lazyBuilderSupplier.get();
            }
        }

        private final Map<ShapeId, Entry<? extends SerializableStruct>> supplierMap = new HashMap<>();

        private Builder() {}

        /**
         * Create a type registry for the registered classes.
         *
         * @return the created registry.
         */
        public TypeRegistry build() {
            return new DefaultRegistry(supplierMap);
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
        public <T extends SerializableStruct> Builder putType(
                ShapeId shapeId,
                Class<T> type,
                Supplier<ShapeBuilder<T>> supplier
        ) {
            supplierMap.put(shapeId, new EagerEntry<>(type, supplier));
            return this;
        }

        /**
         * Put a shape into the registry in a way that does not eagerly load the targeted class.
         *
         * <p>This alternative API can be used to build up large type registries that don't eagerly load classes
         * just by adding them to the registry, leading to potentially faster startup.
         *
         * @param shapeId             ID of the shape.
         * @param typeSupplier        Supplier to return the class.
         * @param lazyBuilderSupplier Supplier to create a new builder supplier for this shape.
         * @return the builder.
         * @param <T> shape type.
         */
        public <T extends SerializableStruct> Builder putType(
                ShapeId shapeId,
                Supplier<Class<T>> typeSupplier,
                Supplier<Supplier<ShapeBuilder<T>>> lazyBuilderSupplier
        ) {
            supplierMap.put(shapeId, new LazyEntry<>(typeSupplier, lazyBuilderSupplier));
            return this;
        }

        private static final class DefaultRegistry implements TypeRegistry {
            private final Map<ShapeId, Entry<?>> supplierMap;

            private DefaultRegistry(Map<ShapeId, Entry<? extends SerializableStruct>> supplierMap) {
                this.supplierMap = Map.copyOf(supplierMap);
            }

            @Override
            public Class<? extends SerializableStruct> getShapeClass(ShapeId shapeId) {
                var entry = supplierMap.get(shapeId);
                return entry == null ? null : entry.type();
            }

            @Override
            public ShapeBuilder<?> createBuilder(ShapeId shapeId) {
                var entry = supplierMap.get(shapeId);
                return entry == null ? null : entry.builderSupplier().get();
            }

            @Override
            public boolean contains(ShapeId shapeId) {
                return supplierMap.containsKey(shapeId);
            }
        }
    }
}
