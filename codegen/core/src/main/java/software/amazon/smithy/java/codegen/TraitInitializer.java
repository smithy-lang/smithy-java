/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.util.function.BiConsumer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.Trait;

/**
 * Writes an initializer for a trait when adding that trait to a {@link software.amazon.smithy.java.runtime.core.schema.Schema}.
 *
 * <p>{@code TraitInitializer} implementations can be added to a {@link JavaCodegenIntegration} to customize the way
 * in which traits are initialized in a Schema definition. Custom initializers are useful to improve the performance of
 * initializing complex traits in generated code. The following initializers are provided by default by the "core"
 * integration:
 * <ul>
 *     <li>{@link  software.amazon.smithy.model.traits.AnnotationTrait}</li>
 *     <li>{@link software.amazon.smithy.model.traits.StringTrait}</li>
 *     <li>{@link software.amazon.smithy.model.traits.StringListTrait}</li>
 *     <li>Catch-all for {@link Trait}</li>
 * </ul>
 * Custom traits are automatically supported by the catch-all initializer. The catch-all initializer uses the
 * {@code TraitService} service provider interface to identify the correct trait provider class for a given trait ID.
 * The trait is then initialized using the trait provider and a {@code Node}.
 */
public interface TraitInitializer<T extends Trait> extends BiConsumer<JavaWriter, T> {
    Class<T> traitClass();
}
