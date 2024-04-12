/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.util.Collections;
import java.util.List;
import software.amazon.smithy.codegen.core.SmithyIntegration;
import software.amazon.smithy.java.codegen.writer.JavaWriter;

/**
 * Java SPI for customizing Java code generation, renaming shapes, modifying the model,
 * adding custom code, etc.
 */
public interface JavaCodegenIntegration
    extends SmithyIntegration<JavaCodegenSettings, JavaWriter, CodeGenerationContext> {

    /**
     * Gets a list of trait initializers to use when initializing traits for schemas.
     *
     * @return Returns the list of {@link TraitInitializer}'s.
     */
    default List<TraitInitializer> traitInitializers() {
        return Collections.emptyList();
    }
}
