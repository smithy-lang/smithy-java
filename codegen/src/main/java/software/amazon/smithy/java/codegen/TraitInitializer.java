/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.util.function.BiConsumer;
import software.amazon.smithy.java.codegen.sections.TraitSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.SourceLocation;
import software.amazon.smithy.model.traits.AnnotationTrait;
import software.amazon.smithy.model.traits.StringListTrait;
import software.amazon.smithy.model.traits.StringTrait;
import software.amazon.smithy.model.traits.Trait;

/**
 * Defines how Trait classes are written in the Schema of a Shape with that Trait applied.
 */
public interface TraitInitializer extends BiConsumer<JavaWriter, TraitSection> {
    Class<? extends Trait> traitClass();

    record AnnotationTraitInitializer(Class<? extends AnnotationTrait> traitClass) implements TraitInitializer {
        @Override
        public void accept(JavaWriter writer, TraitSection section) {
            writer.writeInline("new $T()", traitClass);
        }
    }

    record StringTraitInitializer(Class<? extends StringTrait> traitClass) implements TraitInitializer {
        @Override
        public void accept(JavaWriter writer, TraitSection section) {
            var trait = (StringTrait) section.shape().expectTrait(traitClass);
            writer.writeInline("new $T($S)", traitClass, trait.getValue());
        }
    }

    record StringListTraitInitializer(Class<? extends StringListTrait> traitClass) implements TraitInitializer {
        @Override
        public void accept(JavaWriter writer, TraitSection section) {
            var trait = (StringListTrait) section.shape().expectTrait(traitClass);
            writer.writeInline("new $T($S, $T.NONE)", traitClass, trait.getValues(), SourceLocation.class);
        }
    }
}
