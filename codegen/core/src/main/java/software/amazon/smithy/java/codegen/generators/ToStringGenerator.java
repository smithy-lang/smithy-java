/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;

/**
 * Writes the toString method for a serializable Class.
 */
record ToStringGenerator(JavaWriter writer) implements Runnable {
    @Override
    public void run() {
        writer.pushState();
        writer.putContext("string", String.class);
        writer.putContext("toStringSerializer", ToStringSerializer.class);
        writer.write("""
            @Override
            public ${string:N} toString() {
                return ${toStringSerializer:T}.serialize(this);
            }
            """);
        writer.popState();
    }
}
