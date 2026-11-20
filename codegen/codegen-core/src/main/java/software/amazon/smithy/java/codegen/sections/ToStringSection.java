/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.sections;

import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.CodeSection;

/**
 * Contains the generated {@code toString()} method of a structure, or of one union variant.
 *
 * <p>Intercept this section to replace the method, for example with one that calls a custom
 * {@link software.amazon.smithy.java.core.serde.ShapeSerializer}.
 *
 * @param targetedShape The structure shape, or the union member shape for a union variant
 */
public record ToStringSection(Shape targetedShape) implements CodeSection {}
