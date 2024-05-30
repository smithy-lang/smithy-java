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
public record IdStringGenerator(JavaWriter writer, Shape shape) implements Runnable {
    @Override
    public void run() {
        writer.pushState();
        writer.putContext("shapeId", ShapeId.class);
        writer.write("public static final ${shapeId:T} ID = ${shapeId:T}.from($S);", shape.getId());
        writer.popState();
    }
}
