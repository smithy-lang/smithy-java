/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.sections;

import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.CodeSection;

/**
 * Contains a Java class definition.
 *
 * @param shape Smithy shape that the Java class defines
 * @param applyDocumentation Whether and how documentation is applied.
 */
public record ClassSection(Shape shape, ApplyDocumentation applyDocumentation) implements CodeSection,
        DocumentedSection {
    public ClassSection(Shape shape) {
        this(shape, ApplyDocumentation.DOCUMENT);
    }
}
