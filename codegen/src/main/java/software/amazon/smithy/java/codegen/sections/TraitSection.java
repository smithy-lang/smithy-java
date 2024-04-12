/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.sections;

import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Injects Trait definitions for a Smithy Shape.
 *
 * @param shape Smithy shape to inject trait definitions for.
 */
@SmithyInternalApi
public record TraitSection(Shape shape) implements CodeSection {}
