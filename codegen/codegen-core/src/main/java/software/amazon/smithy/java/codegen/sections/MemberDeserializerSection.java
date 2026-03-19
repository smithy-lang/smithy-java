/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.sections;

import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.CodeSection;

/**
 * Adds a section for member deserializer code.
 *
 * @param targetedShape A shape that the deserializer code section is created for
 * @param shapeSchemaVariable Name of a local variable containing the shape's schema
 * @param deserializerVariable Name of a local variable containing the deserializer instance
 */
public record MemberDeserializerSection(Shape targetedShape, String shapeSchemaVariable, String deserializerVariable)
        implements CodeSection {}
