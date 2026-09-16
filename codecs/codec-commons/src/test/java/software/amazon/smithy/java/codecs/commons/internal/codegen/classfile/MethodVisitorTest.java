/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen.classfile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

final class MethodVisitorTest {
    @Test
    void recordsLookupSwitchCasesInAscendingOrder() {
        var visitor = new MethodVisitor();
        var defaultTarget = new Label();
        var first = new Label();
        var second = new Label();
        var third = new Label();
        int[] keys = {20, -10, 5};
        Label[] targets = {third, first, second};

        visitor.visitLookupSwitchInsn(defaultTarget, keys, targets);

        Object[] operands = visitor.instructions().getFirst().operands();
        assertSame(defaultTarget, operands[0]);
        assertArrayEquals(new int[] {-10, 5, 20}, (int[]) operands[1]);
        assertArrayEquals(new Label[] {first, second, third}, (Label[]) operands[2]);
        assertArrayEquals(new int[] {20, -10, 5}, keys);
        assertArrayEquals(new Label[] {third, first, second}, targets);
    }
}
