/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.sections;

import software.amazon.smithy.model.shapes.Shape;

/**
 * Applied to sections that can have documentation.
 */
public interface DocumentedSection {
    default ApplyDocumentation applyDocumentation() {
        return ApplyDocumentation.DOCUMENT;
    }

    /**
     * @return Shape targeted by this section, or null if target is not a shape
     */
    Shape targetedShape();
}
