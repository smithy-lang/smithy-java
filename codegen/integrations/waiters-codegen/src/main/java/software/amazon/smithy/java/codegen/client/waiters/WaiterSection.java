/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.waiters;

import software.amazon.smithy.java.codegen.sections.DocumentedSection;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.waiters.Waiter;

/**
 * Contains a waiter method.
 *
 * @param waiter waiter method definition contained by this section
 */
record WaiterSection(Waiter waiter) implements CodeSection, DocumentedSection {
    @Override
    public Shape targetedShape() {
        // Never targets a shape.
        return null;
    }
}
