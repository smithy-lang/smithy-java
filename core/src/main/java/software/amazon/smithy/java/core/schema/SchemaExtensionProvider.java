/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

/**
 * SPI for contributing extension data during {@link Schema} construction.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} and invoked
 * for every Schema built through the schema construction pipeline. Each provider
 * declares a fixed {@link SchemaExtensionKey} and computes a value for a given schema.
 *
 * <p>Register implementations in
 * {@code META-INF/services/software.amazon.smithy.java.core.schema.SchemaExtensionProvider}.
 *
 * @param <T> The type of extension data this provider produces.
 */
public interface SchemaExtensionProvider<T> {

    /**
     * The key under which this provider's extension data is stored.
     *
     * @return the extension key.
     */
    SchemaExtensionKey<T> key();

    /**
     * Compute the extension value for the given schema.
     *
     * @param schema The schema to compute extension data for.
     * @return the extension value, or {@code null} if this provider has no data for this schema.
     */
    T provide(Schema schema);
}
