/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;

/**
 * Generates the implementation of the
 * {@link software.amazon.smithy.java.runtime.core.schema.SerializableShape#serialize(ShapeSerializer)}
 * method for a class.
 */
public class SerializerGenerator implements Runnable {
    private final JavaWriter writer;

    public SerializerGenerator(JavaWriter writer) {
        this.writer = writer;
    }

    @Override
    public void run() {
        // TODO: Replace with actual implementation. This is just a placeholder.
        writer.write("""
            @Override
            public void serialize($T serializer) {
                // Placeholder. Do nothing
            }
            """, ShapeSerializer.class);
    }
}
