/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.util.List;
import software.amazon.smithy.codegen.core.CodegenContext;
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

    /**
     * Customizes {@link JavaCodegenSettings} before code generation begins.
     *
     * <p>This is invoked once per integration immediately after the {@link CodeGenerationContext} is
     * created and <em>before</em> any interceptors are registered or shapes are generated. It is the
     * correct place to programmatically register
     * {@linkplain JavaCodegenSettings#addDefaultPlugin(String) default plugins} or
     * {@linkplain JavaCodegenSettings#addDefaultSetting(String) default settings}, because those values
     * are read while the client builder is generated.
     *
     * <p>Mutating settings from {@link #customize(CodegenContext)} is too late: by the time
     * {@code customize} runs the client builder has already been generated and the change has no effect.
     *
     * <p>The full context is provided so implementations can inspect the model (for example, to register
     * a plugin only for services that use a particular auth scheme). Use
     * {@link CodeGenerationContext#settings()} to register defaults.
     *
     * @param context code generation context whose settings may be customized.
     */
    default void customizeSettings(CodeGenerationContext context) {}
}
