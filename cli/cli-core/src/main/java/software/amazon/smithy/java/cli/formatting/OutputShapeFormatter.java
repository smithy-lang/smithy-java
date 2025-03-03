/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli.formatting;

import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.node.Node;

public enum OutputShapeFormatter implements ShapeFormatter {
    JSON("json") {
        @Override
        public void writeShape(SerializableShape shape, CliPrinter printer) {
            try (var codec = JsonCodec.builder().build()) {
                // TODO: should just be settable via setting on codec.
                printer.append(Node.prettyPrintJson(Node.parse(
                        new String(codec.serialize(shape).array(), StandardCharsets.UTF_8))));
                printer.flush();
            }
        }
    },
    TXT("txt") {
        @Override
        public void writeShape(SerializableShape shape, CliPrinter printer) {
            printer.append(shape.toString()).append(System.lineSeparator());
        }
    };

    private final String value;

    OutputShapeFormatter(String value) {
        this.value = value;
    }

    public static OutputShapeFormatter fromString(String text) {
        for (OutputShapeFormatter b : OutputShapeFormatter.values()) {
            if (b.value.equalsIgnoreCase(text)) {
                return b;
            }
        }
        throw new IllegalArgumentException("Unknown output format: " + text);
    }
}
