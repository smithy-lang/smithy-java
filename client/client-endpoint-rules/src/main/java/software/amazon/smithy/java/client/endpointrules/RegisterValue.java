/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.endpointrules;

import java.util.ArrayDeque;
import java.util.Deque;

final class RegisterValue {

    final RegisterDefinition definition;
    private Object value;
    private Deque<Object> stack;

    RegisterValue(RegisterDefinition registerDefinition) {
        this.definition = registerDefinition;
    }

    Object get() {
        return value;
    }

    void push(Object value) {
        // Only deal with stacks when there actually needs to be a stack.
        // Most rules don't end up actually needing the stack.
        if (this.value != null) {
            if (stack == null) {
                stack = new ArrayDeque<>();
            }
            stack.push(this.value);
        }
        this.value = value;
    }

    Object pop() {
        if (stack == null) {
            value = null;
        } else {
            stack.pop();
            value = stack.peek();
        }
        return value;
    }
}
