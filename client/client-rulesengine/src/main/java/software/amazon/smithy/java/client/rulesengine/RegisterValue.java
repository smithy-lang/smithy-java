/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.ArrayDeque;
import java.util.Deque;

final class RegisterValue {

    final RegisterDefinition definition;
    private Object value;
    private int position = -1;
    private Deque<Object> stack;

    RegisterValue(RegisterDefinition registerDefinition) {
        this.definition = registerDefinition;
    }

    Object get() {
        return value;
    }

    void push(Object value) {
        if (position++ > -1) {
            if (stack == null) {
                stack = new ArrayDeque<>();
                stack.push(this.value);
            }
            stack.push(value);
        }
        this.value = value;
    }

    void pop() {
        if (stack == null) {
            value = null;
            position = -1;
        } else {
            stack.pop();
            value = stack.peek();
            position--;
        }
    }
}
