/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.sections;

import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.utils.CodeSection;

/**
 * Contains an inner class definition representing the member of a union.
 *
 * @param shape Smithy member shape that the inner class defines
 */
public record UnionMemberSection(MemberShape shape) implements CodeSection {}
