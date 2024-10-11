/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.javadoc;

import software.amazon.smithy.java.codegen.sections.BuilderSetterSection;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.sections.EnumVariantSection;
import software.amazon.smithy.java.codegen.sections.GetterSection;
import software.amazon.smithy.java.codegen.sections.JavadocSection;
import software.amazon.smithy.java.codegen.sections.OperationSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.DeprecatedTrait;
import software.amazon.smithy.model.traits.UnstableTrait;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Adds a javadoc section to classes, getters, and enum variants.
 *
 * <p>This interceptor will also add any relevant documentation annotation to classes, getters, or enum variants.
 */
final class JavadocInjectorInterceptor implements CodeInterceptor.Prepender<CodeSection, JavaWriter> {

    @Override
    public Class<CodeSection> sectionType() {
        return CodeSection.class;
    }

    @Override
    public boolean isIntercepted(CodeSection section) {
        // Javadocs are generated for Classes, on member Getters, and on enum variants, operations, and builder setters.
        return section instanceof ClassSection
            || section instanceof GetterSection
            || section instanceof EnumVariantSection
            || section instanceof OperationSection
            || section instanceof BuilderSetterSection;
    }

    @Override
    public void prepend(JavaWriter writer, CodeSection section) {
        var shape = getShape(section);
        writer.injectSection(new JavadocSection(shape, section));
        if (shape.hasTrait(UnstableTrait.class)) {
            writer.write("@$T", SmithyUnstableApi.class);
        }

        if (shape.hasTrait(DeprecatedTrait.class)) {
            var deprecated = shape.expectTrait(DeprecatedTrait.class);
            writer.pushState();
            writer.putContext("since", deprecated.getSince().orElse(""));
            writer.write("@$T${?since}(since = ${since:S})${/since}", Deprecated.class);
            writer.popState();
        }
    }

    private static Shape getShape(CodeSection section) {
        if (section instanceof ClassSection cs) {
            return cs.shape();
        } else if (section instanceof GetterSection gs) {
            return gs.memberShape();
        } else if (section instanceof EnumVariantSection es) {
            return es.memberShape();
        } else if (section instanceof BuilderSetterSection bs) {
            return bs.memberShape();
        } else if (section instanceof OperationSection os) {
            return os.operation();
        } else {
            throw new IllegalArgumentException("Javadocs cannot be injected for section: " + section);
        }
    }
}
