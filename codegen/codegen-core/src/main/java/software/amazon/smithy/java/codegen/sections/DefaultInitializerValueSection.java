/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.sections;

import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.utils.CodeSection;

/**
 * Contains the value a builder property is initialized with when its member has a default value.
 *
 * <p>Integrations that change the Java type of a member must intercept this section when the default
 * value for the targeted shape type is not compatible with the new type.
 *
 * @param targetedShape Member with a default value
 */
public record DefaultInitializerValueSection(MemberShape targetedShape) implements CodeSection {}
