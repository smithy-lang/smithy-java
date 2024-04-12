/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import java.util.List;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.TraitInitializer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpQueryParamsTrait;
import software.amazon.smithy.model.traits.HttpResponseCodeTrait;
import software.amazon.smithy.model.traits.IdempotentTrait;
import software.amazon.smithy.model.traits.InputTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.OutputTrait;
import software.amazon.smithy.model.traits.ReadonlyTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.SensitiveTrait;
import software.amazon.smithy.model.traits.StreamingTrait;
import software.amazon.smithy.model.traits.TagsTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Base Plugin for
 * TODO: more complete docs once this has all the expected interceptors
 */
public class CoreIntegration implements JavaCodegenIntegration {

    @Override
    public String name() {
        return "core";
    }

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, JavaWriter>> interceptors(
        CodeGenerationContext context
    ) {
        return List.of(
            new TraitInitializerInterceptor(context)
        );
    }

    @Override
    public List<TraitInitializer> traitInitializers() {
        return List.of(
            new TraitInitializer.AnnotationTraitInitializer(RequiredTrait.class),
            new TraitInitializer.AnnotationTraitInitializer(SensitiveTrait.class),
            new TraitInitializer.StringTraitInitializer(JsonNameTrait.class),
            new TraitInitializer.AnnotationTraitInitializer(StreamingTrait.class),
            new TraitInitializer.AnnotationTraitInitializer(HttpResponseCodeTrait.class),
            new TraitInitializer.StringTraitInitializer(ErrorTrait.class),
            new TraitInitializer.AnnotationTraitInitializer(HttpQueryParamsTrait.class),
            new TraitInitializer.StringTraitInitializer(TimestampFormatTrait.class),
            new TraitInitializer.AnnotationTraitInitializer(IdempotentTrait.class),
            new TraitInitializer.AnnotationTraitInitializer(ReadonlyTrait.class),
            new TraitInitializer.StringListTraitInitializer(TagsTrait.class),
            new TraitInitializer.StringTraitInitializer(HttpHeaderTrait.class),
            new TraitInitializer.AnnotationTraitInitializer(InputTrait.class),
            new TraitInitializer.AnnotationTraitInitializer(OutputTrait.class)
        );
    }
}
