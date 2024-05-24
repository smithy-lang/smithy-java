/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Generates a {@code build()} method implementation for structure shapes.
 */
record StructureBuilderBuildGenerator(JavaWriter writer, Shape shape) implements Runnable {
    @Override
    public void run() {
        writer.pushState();
        writer.putContext(
            "hasRequiredMembers",
            shape.members().stream().anyMatch(CodegenUtils::isRequiredWithNoDefault)
        );
        writer.write("""
            @Override
            public ${shape:T} build() {${?hasRequiredMembers}
                tracker.validate();${/hasRequiredMembers}
                return new ${shape:T}(this);
            }
            """);
        writer.popState();
    }
}
