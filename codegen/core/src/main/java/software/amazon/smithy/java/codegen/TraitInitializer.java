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
 * <p>TraitInitializer implementations can be added to a {@link JavaCodegenIntegration} to customize the way in which
 * traits are initialized in a Schema definition. Initializers for:
 * <ul>
 *     <li>{@link  software.amazon.smithy.model.traits.AnnotationTrait}</li>
 *     <li>{@link software.amazon.smithy.model.traits.StringTrait}</li>
 *     <li>{@link software.amazon.smithy.model.traits.StringListTrait}</li>
 *     <li>Catch-all for {@link Trait}</li>
 * </ul>
 * are provided by the "core" integration. Custom traits are automatically supported by the catch-all initializer.
 * Custom initializers can be used instead of the catch-all implementation in order to simplify and clean up
 * generated code.
 */
public interface TraitInitializer<T extends Trait> extends BiConsumer<JavaWriter, T> {
    Class<T> traitClass();
}
