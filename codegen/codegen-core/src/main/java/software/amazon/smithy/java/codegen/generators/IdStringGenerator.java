/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Writes the ID string constant for a shape class.
 *
 * @param shape Shape to write ID for
 */
@SmithyInternalApi
public record IdStringGenerator(JavaWriter writer, Shape shape, boolean isInterface) implements Runnable {

    public IdStringGenerator(JavaWriter writer, Shape shape) {
        this(writer, shape, false);
    }

    @Override
    public void run() {
        writer.pushState();
        writer.putContext("shapeId", ShapeId.class);
        var qualifiers = isInterface ? "" : "public static final ";
        writer.write(qualifiers + "${shapeId:T} $$ID = $$SCHEMA.id();");
        writer.popState();
    }
}
