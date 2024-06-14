/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.sections;

import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.utils.CodeSection;

/**
 * Used to add documentation to an Enum Variant member shape.
 *
 * @param memberShape Member shape for enum variant
 */
public record EnumVariantSection(MemberShape memberShape) implements CodeSection {}
