/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.TraitInitializer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Core integration for Java code generation.
 *
 * <p>This integration registers a set of base {@code TraitInitializers} as well as
 * custom initializers for all relevant Prelude traits.
 * <p>Note: This integration will run after all other integrations.
 */
@SmithyInternalApi
public class CoreIntegration implements JavaCodegenIntegration {

    @Override
    public String name() {
        return "core";
    }

    @Override
    public byte priority() {
        return -1;
    }

    @Override
    public SymbolProvider decorateSymbolProvider(
        Model model,
        JavaCodegenSettings settings,
        SymbolProvider symbolProvider
    ) {
        return new SymbolProvider() {
            @Override
            public Symbol toSymbol(Shape shape) {
                // Add symbols to generated symbol map, so we can resolve any implicit usages
                // from symbols in the same package.
                var symbol = symbolProvider.toSymbol(shape);
                if (symbol != null) {
                    settings.addSymbol(symbol);
                }
                return symbol;
            }

            // Necessary to ensure initial toMemberName is not squashed by decorating
            @Override
            public String toMemberName(MemberShape shape) {
                return symbolProvider.toMemberName(shape);
            }
        };
    }

    @Override
    public List<TraitInitializer<? extends Trait>> traitInitializers() {
        return List.of(
            new PaginatedTraitInitializer(),
            new HttpApiKeyAuthTraitInitializer(),
            new RequestCompressionTraitInitializer(),
            new DefaultTraitInitializer(),
            new HttpTraitInitializer(),
            new XmlNamespaceTraitInitializer(),
            new EndpointTraitInitializer(),
            new RetryableTraitInitializer(),
            new LengthTraitInitializer(),
            new RangeTraitInitializer(),
            new AnnotationTraitInitializer(),
            new StringTraitInitializer(),
            new StringListTraitInitializer(),
            new GenericTraitInitializer()
        );
    }
}
