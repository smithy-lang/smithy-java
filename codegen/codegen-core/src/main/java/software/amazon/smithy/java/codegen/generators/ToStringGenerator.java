/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.java.codegen.sections.ToStringSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Writes the toString method for a serializable Class.
 *
 * @param shape The structure, or the union member for a union variant.
 */
record ToStringGenerator(JavaWriter writer, Shape shape) implements Runnable {
    @Override
    public void run() {
        writer.pushState(new ToStringSection(shape));
        writer.putContext("string", String.class);
        writer.putContext("schemaUtils", SchemaUtils.class);
        writer.write("""
                @Override
                public ${string:T} toString() {
                    return ${schemaUtils:T}.toString(this);
                }
                """);
        writer.popState();
    }
}
