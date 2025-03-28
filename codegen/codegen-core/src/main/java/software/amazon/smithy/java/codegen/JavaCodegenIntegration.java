/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.util.List;
import software.amazon.smithy.codegen.core.SmithyIntegration;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.Trait;

/**
 * Java SPI for customizing Java code generation, renaming shapes, modifying the model,
 * adding custom code, etc.
 */
public interface JavaCodegenIntegration
        extends SmithyIntegration<JavaCodegenSettings, JavaWriter, CodeGenerationContext> {

    /**
     * List of {@link TraitInitializer}'s to use when writing traits in Schema definitions.
     */
    default List<TraitInitializer<? extends Trait>> traitInitializers() {
        return List.of();
    }
}
