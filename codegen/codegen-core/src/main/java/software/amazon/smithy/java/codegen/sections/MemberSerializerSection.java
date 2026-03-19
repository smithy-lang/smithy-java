/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.sections;

import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.CodeSection;

/**
 * Adds a section for member serializer code.
 *
 * @param targetedShape A shape that the serializer code section is created for
 * @param stateVariable Name of a local variable containing the state to serialize
 * @param shapeSchemaVariable Name of a local variable containing the schema for a given {@code targetedShape}
 */
public record MemberSerializerSection(Shape targetedShape, String stateVariable, String shapeSchemaVariable)
        implements CodeSection {}
