/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.codegen;

/**
 * Lightweight StringBuilder wrapper for generating indented Java source code.
 */
final class SourceBuilder {

    private final StringBuilder sb = new StringBuilder(1024);
    private int indent;

    SourceBuilder line(String s) {
        writeIndent();
        sb.append(s);
        sb.append('\n');
        return this;
    }

    SourceBuilder beginBlock(String header) {
        writeIndent();
        sb.append(header);
        sb.append(" {\n");
        indent++;
        return this;
    }

    SourceBuilder endBlock() {
        indent--;
        writeIndent();
        sb.append("}\n");
        return this;
    }

    SourceBuilder append(String s) {
        sb.append(s);
        return this;
    }

    SourceBuilder emptyLine() {
        sb.append('\n');
        return this;
    }

    private void writeIndent() {
        for (int i = 0; i < indent; i++) {
            sb.append("    ");
        }
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
