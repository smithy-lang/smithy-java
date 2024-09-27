/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AuthDefinitionTrait;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.EndpointTrait;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.EventHeaderTrait;
import software.amazon.smithy.model.traits.EventPayloadTrait;
import software.amazon.smithy.model.traits.HostLabelTrait;
import software.amazon.smithy.model.traits.IdempotencyTokenTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.MediaTypeTrait;
import software.amazon.smithy.model.traits.PaginatedTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.ProtocolDefinitionTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.RequestCompressionTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.RequiresLengthTrait;
import software.amazon.smithy.model.traits.RetryableTrait;
import software.amazon.smithy.model.traits.SensitiveTrait;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.UniqueItemsTrait;
import software.amazon.smithy.model.traits.XmlAttributeTrait;
import software.amazon.smithy.model.traits.XmlFlattenedTrait;
import software.amazon.smithy.model.traits.XmlNameTrait;
import software.amazon.smithy.model.traits.XmlNamespaceTrait;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Contextual information that is made available during most parts of Java code generation.
 */
@SmithyUnstableApi
public class CodeGenerationContext
    implements CodegenContext<JavaCodegenSettings, JavaWriter, JavaCodegenIntegration> {

    private static final List<ShapeId> PRELUDE_RUNTIME_TRAITS = List.of(
        // Validation Traits
        LengthTrait.ID,
        PatternTrait.ID,
        RangeTrait.ID,
        RequiredTrait.ID,
        SensitiveTrait.ID,
        IdempotencyTokenTrait.ID,
        SparseTrait.ID,
        UniqueItemsTrait.ID,
        RequiresLengthTrait.ID,
        ErrorTrait.ID,
        DefaultTrait.ID,
        // Base Prelude Protocol traits
        JsonNameTrait.ID,
        TimestampFormatTrait.ID,
        MediaTypeTrait.ID,
        XmlNameTrait.ID,
        XmlFlattenedTrait.ID,
        XmlAttributeTrait.ID,
        XmlNamespaceTrait.ID,
        EventHeaderTrait.ID,
        EventPayloadTrait.ID,
        HostLabelTrait.ID,
        EndpointTrait.ID,
        // Prelude behavior traits
        PaginatedTrait.ID,
        IdempotencyTokenTrait.ID,
        RetryableTrait.ID,
        RequestCompressionTrait.ID
    );

    private final Model model;
    private final JavaCodegenSettings settings;
    private final SymbolProvider symbolProvider;
    private final FileManifest fileManifest;
    private final List<JavaCodegenIntegration> integrations;
    private final WriterDelegator<JavaWriter> writerDelegator;
    private final Set<ShapeId> runtimeTraits;
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
        this.runtimeTraits = collectRuntimeTraits();
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

    public Set<ShapeId> runtimeTraits() {
        return runtimeTraits;
    }

    /**
     * Determines the "runtime traits" for a service, i.e. traits that should be included in Shape schemas.
     *
     * <p>Runtime traits are added from the following sources:
     * <dl>
     *     <dt>Protocol-generic prelude traits</dt>
     *     <dd>Static list of prelude traits that should be included regardless of protocol.</dd>
     *     <dt>Protocol traits</dt>
     *     <dd>Traits supported explicitly by protocols the service uses should be included in Schemas.</dd>
     *     <dt>AuthScheme traits</dt>
     *     <dd>Traits supported explicitly by auth schemes used by the service should be included in Schemas.</dd>
     * </dl>
     *
     * @return Set of trait ShapeId's to include in generated Schemas.
     */
    private Set<ShapeId> collectRuntimeTraits() {
        ServiceShape shape = model.expectShape(settings.service())
            .asServiceShape()
            .orElseThrow(
                () -> new CodegenException(
                    "Expected shapeId: "
                        + settings.service() + " to be a service shape."
                )
            );

        // Add all default runtime traits from the prelude
        Set<ShapeId> traits = new HashSet<>(PRELUDE_RUNTIME_TRAITS);
        for (var entry : shape.getAllTraits().entrySet()) {
            Shape traitShape = model.expectShape(entry.getKey());
            // Add all traits supported by a protocol the service supports
            if (traitShape.hasTrait(ProtocolDefinitionTrait.class)) {
                var protocolDef = traitShape.expectTrait(ProtocolDefinitionTrait.class);
                traits.addAll(protocolDef.getTraits());
            }
            // Add all traits supported by auth schemes the service supports
            if (traitShape.hasTrait(AuthDefinitionTrait.class)) {
                var authDef = traitShape.expectTrait(AuthDefinitionTrait.class);
                traits.addAll(authDef.getTraits());
            }
        }

        return Collections.unmodifiableSet(traits);
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
