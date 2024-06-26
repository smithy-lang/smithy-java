/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import java.util.List;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.TraitInitializer;
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
    public List<TraitInitializer<? extends Trait>> traitInitializers() {
        return List.of(
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
