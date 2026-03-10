/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.sections;

import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.CodeSection;

/**
 * Adds a section for Javadocs.
 *
 * <p>This section does not initially contain any content. Interceptors
 * are used to populate this section with content and to format it as a doc
 * comment.
 *
 * @param targetedShape Shape that java docs are being added to.
 * @param parent Code section that this javadoc section is attached to
 */
public record JavadocSection(Shape targetedShape, CodeSection parent) implements CodeSection {}
