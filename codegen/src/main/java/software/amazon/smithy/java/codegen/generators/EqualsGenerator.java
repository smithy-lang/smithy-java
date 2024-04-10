/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;


import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.shapes.Shape;

final class EqualsGenerator implements Runnable {
    private final JavaWriter writer;
    private final Shape shape;

    EqualsGenerator(JavaWriter writer, Shape shape) {
        this.writer = writer;
        this.shape = shape;
    }

    @Override
    public void run() {
        writer.write("""
            @Override
            public boolean equals(Object other) {
                if (other == this) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                $1T that = ($1T) other;
                return ${C|};
            }
            """, );
    }
}
