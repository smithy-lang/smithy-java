/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.schema.SchemaUtils;

/**
 * Writes the toString method for a serializable Class.
 */
record ToStringGenerator(JavaWriter writer) implements Runnable {
    @Override
    public void run() {
        writer.pushState();
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
