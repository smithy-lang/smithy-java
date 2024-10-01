/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.sections;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.utils.CodeSection;

/**
 * TODO: DOCS
 *
 * @param operation Operation that java docs are being added to.
 */
public record OperationSection(
    OperationShape operation,
    SymbolProvider symbolProvider,
    Model model
) implements CodeSection {
}
