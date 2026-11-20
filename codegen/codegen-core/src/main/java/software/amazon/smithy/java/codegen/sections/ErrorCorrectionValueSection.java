/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.sections;

import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.utils.CodeSection;

/**
 * Contains the value a builder sets for a required member that is missing during error correction.
 *
 * <p>Integrations that change the Java type of a member must intercept this section when the default
 * value for the targeted shape type is not compatible with the new type.
 *
 * @param targetedShape Required member that is error corrected
 * @see <a href="https://smithy.io/2.0/spec/aggregate-types.html#client-error-correction">client error correction</a>
 */
public record ErrorCorrectionValueSection(MemberShape targetedShape) implements CodeSection {}
