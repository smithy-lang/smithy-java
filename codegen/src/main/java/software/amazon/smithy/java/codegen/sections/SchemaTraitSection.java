/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.sections;

import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.CodeSection;

/**
 * Injected section used to populate trait values for SdkSchemas
 *
 * @param shape Shape traits are a attached to.
 */
public record SchemaTraitSection(Shape shape) implements CodeSection {}
