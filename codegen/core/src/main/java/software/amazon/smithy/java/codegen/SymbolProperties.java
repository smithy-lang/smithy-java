/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import software.amazon.smithy.codegen.core.Property;
import software.amazon.smithy.codegen.core.Symbol;

public final class SymbolProperties {
    /**
     * The boxed version of a primitive symbol.
     *
     * <p>This is the symbol that should be used if a primitive type is nullable.
     * For example a Symbol representing an {@code int} would have a Symbol
     * representing a {@code Integer} as the value of this property.
     */
    public static final Property<Symbol> BOXED_TYPE = Property.named("boxed-type");

    /**
     * Indicates if a symbol represents a Java primitive type.
     */
    public static final Property<Boolean> IS_PRIMITIVE = Property.named("is-primitive");

    /**
     * Indicates if a symbol represents a Java array such as {@code byte[]}.
     */
    public static final Property<Boolean> IS_JAVA_ARRAY = Property.named("is-java-array");

    /**
     * Method on {@link java.util.Collection} to use to create an immutable copy of the collection type
     * a symbol represents.
     */
    public static final Property<String> COLLECTION_COPY_METHOD = Property.named("collection-factory");

    /**
     * Class to use when instantiating a new instance of a collection.
     *
     * <p>For example, a Symbol representing a {@link java.util.List} might specify the {@link java.util.ArrayList}
     * class as its implementing class.
     */
    public static final Property<Class<?>> COLLECTION_IMPLEMENTATION_CLASS = Property.named("collection-impl-class");

    /**
     * Method on {@link java.util.Collection} to use to create an empty version of the collection type
     * a symbol represents.
     */
    public static final Property<String> COLLECTION_EMPTY_METHOD = Property.named("collection-empty");

    /**
     * Indicates that a type should define a static default value.
     */
    public static final Property<Boolean> REQUIRES_STATIC_DEFAULT = Property.named("requires-static-default");

    private SymbolProperties() {}
}
