/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.sections;

import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.utils.CodeSection;

/**
 * Contains a getter method.
 *
 * @param targetShape Member shape that getter provides
 */
public record GetterSection(MemberShape targetShape) implements CodeSection, DocumentedSection {}
