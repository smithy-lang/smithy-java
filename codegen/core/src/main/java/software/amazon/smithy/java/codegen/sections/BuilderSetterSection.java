/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.sections;

import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.utils.CodeSection;

/**
 * Contains a setter method on a builder.
 *
 * @param targetShape Smithy member that the Builder setter sets
 */
public record BuilderSetterSection(MemberShape targetShape) implements CodeSection, DocumentedSection {}
