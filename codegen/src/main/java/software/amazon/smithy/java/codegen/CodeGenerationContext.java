/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Contextual information that is made available during most parts of Java code generation.
 */
@SmithyUnstableApi
public class CodeGenerationContext
    implements CodegenContext<JavaCodegenSettings, JavaWriter, JavaCodegenIntegration> {

    private final Model model;
    private final JavaCodegenSettings settings;
    private final SymbolProvider symbolProvider;
    private final FileManifest fileManifest;
    private final List<JavaCodegenIntegration> integrations;
    private final WriterDelegator<JavaWriter> writerDelegator;
    private final Map<Class<? extends Trait>, TraitInitializer> traitInitializers = new HashMap<>();

    CodeGenerationContext(
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
        setTraitInitializers();
        this.writerDelegator = new WriterDelegator<>(fileManifest, symbolProvider, new JavaWriter.Factory(settings));
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

    public TraitInitializer initializer(Class<? extends Trait> trait) {
        return traitInitializers.get(trait);
    }

    private void setTraitInitializers() {
        for (var integration : integrations) {
            for (var initializer : integration.traitInitializers()) {
                var existing = traitInitializers.put(initializer.traitClass(), initializer);
                if (existing != null) {
                    throw new CodegenException(
                        "Attempted to add initializer for integration "
                            + integration.name() + " but founding existing initializer for trait "
                            + initializer.traitClass() + ". "
                    );
                }
            }
        }
    }
}
