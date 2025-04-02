/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import software.amazon.smithy.java.codegen.TraitInitializer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.AnnotationTrait;

final class AnnotationTraitInitializer implements TraitInitializer<AnnotationTrait> {
    @Override
    public Class<AnnotationTrait> traitClass() {
        return AnnotationTrait.class;
    }

    @Override
    public void accept(JavaWriter writer, AnnotationTrait annotationTrait) {
        writer.writeInline("new $T()", annotationTrait.getClass());
    }
}
