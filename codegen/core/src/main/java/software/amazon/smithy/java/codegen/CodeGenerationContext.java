/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AddedDefaultTrait;
import software.amazon.smithy.model.traits.ClientOptionalTrait;
import software.amazon.smithy.model.traits.DeprecatedTrait;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.EnumValueTrait;
import software.amazon.smithy.model.traits.ExamplesTrait;
import software.amazon.smithy.model.traits.ExternalDocumentationTrait;
import software.amazon.smithy.model.traits.InputTrait;
import software.amazon.smithy.model.traits.InternalTrait;
import software.amazon.smithy.model.traits.MixinTrait;
import software.amazon.smithy.model.traits.OutputTrait;
import software.amazon.smithy.model.traits.RecommendedTrait;
import software.amazon.smithy.model.traits.SinceTrait;
import software.amazon.smithy.model.traits.SuppressTrait;
import software.amazon.smithy.model.traits.TagsTrait;
import software.amazon.smithy.model.traits.TitleTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.UnstableTrait;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Contextual information that is made available during most parts of Java code generation.
 */
@SmithyUnstableApi
public class CodeGenerationContext
    implements CodegenContext<JavaCodegenSettings, JavaWriter, JavaCodegenIntegration> {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(CodeGenerationContext.class);

    /*
     * These traits are only used for modeling, and are not needed at runtime.
     */
    public static final List<ShapeId> BUILD_ONLY_TRAITS = List.of(
        // Type Refinement Traits
        AddedDefaultTrait.ID, // Documentation only
        ClientOptionalTrait.ID, // Handled by nullability index
        EnumValueTrait.ID, // Code generator converts enums to shapes
        InputTrait.ID,
        OutputTrait.ID,
        MixinTrait.ID, // Flattened by generators
        // Documentation Traits
        DeprecatedTrait.ID,
        DocumentationTrait.ID,
        ExamplesTrait.ID,
        ExternalDocumentationTrait.ID,
        InternalTrait.ID,
        RecommendedTrait.ID,
        SinceTrait.ID,
        TagsTrait.ID,
        TitleTrait.ID,
        UnstableTrait.ID,
        // Other
        SuppressTrait.ID
    );

    private final Model model;
    private final JavaCodegenSettings settings;
    private final SymbolProvider symbolProvider;
    private final FileManifest fileManifest;
    private final List<JavaCodegenIntegration> integrations;
    private final WriterDelegator<JavaWriter> writerDelegator;
    private final List<TraitInitializer<?>> traitInitializers;

    public CodeGenerationContext(
        Model model,
        JavaCodegenSettings settings,
        SymbolProvider symbolProvider,
        FileManifest fileManifest,
        List<JavaCodegenIntegration> integrations
    ) {
        this.model = model;
        this.settings = settings;
        this.symbolProvider = symbolProvider;
        this.fileManifest = fileManifest;
        this.integrations = integrations;
        this.writerDelegator = new WriterDelegator<>(fileManifest, symbolProvider, new JavaWriter.Factory(settings));
        this.traitInitializers = collectTraitInitializers();
    }

    @Override
    public Model model() {
        return model;
    }

    @Override
    public JavaCodegenSettings settings() {
        return settings;
    }

    @Override
    public SymbolProvider symbolProvider() {
        return symbolProvider;
    }

    @Override
    public FileManifest fileManifest() {
        return fileManifest;
    }

    @Override
    public WriterDelegator<JavaWriter> writerDelegator() {
        return writerDelegator;
    }

    @Override
    public List<JavaCodegenIntegration> integrations() {
        return integrations;
    }

    private List<TraitInitializer<?>> collectTraitInitializers() {
        List<TraitInitializer<?>> initializers = new ArrayList<>();
        for (var integration : integrations) {
            initializers.addAll(integration.traitInitializers());
        }
        return initializers;
    }

    /**
     * Gets the {@link TraitInitializer} for a given trait.
     *
     * @param trait trait to get initializer for.
     * @return Trait initializer for trait class.
     * @throws IllegalArgumentException if no initializer can be found for a trait.
     */
    @SuppressWarnings("unchecked")
    public <T extends Trait> TraitInitializer<T> getInitializer(T trait) {
        for (var initializer : traitInitializers) {
            if (initializer.traitClass().isInstance(trait)) {
                return (TraitInitializer<T>) initializer;
            }
        }
        throw new IllegalArgumentException("Could not find initializer for " + trait);
    }
}
