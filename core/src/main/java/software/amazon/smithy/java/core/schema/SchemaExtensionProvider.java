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
     * <p>Extension values are cached: once computed for a given schema and key, the result is stored
     * and this method is not called again.
     *
     * <p><b>Thread safety requirements:</b> The returned object is published to other threads via a
     * plain (non-volatile) array store. For the object's state to be visible to all threads,
     * implementations must ensure one of the following:
     * <ul>
     *   <li>All fields of the returned object are {@code final} (preferred — Java records satisfy this
     *       automatically). The JVM guarantees that final fields are visible to all threads once the
     *       constructor completes (JLS 17.5).</li>
     *   <li>The returned object is effectively immutable and all fields are set before the constructor
     *       returns.</li>
     * </ul>
     *
     * <p><b>Idempotency:</b> Under concurrent access, multiple threads may invoke this method
     * simultaneously for the same schema and key (benign race). All invocations must produce
     * equivalent results.
     *
     * @param schema The schema to compute extension data for.
     * @return the extension value, or {@code null} if this provider has no data for this schema.
     */
    T provide(Schema schema);
}
