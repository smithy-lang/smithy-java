/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli.formatting;

import software.amazon.smithy.java.core.schema.SerializableShape;

public interface ShapeFormatter {
    void writeShape(SerializableShape shape, CliPrinter printer);
}
