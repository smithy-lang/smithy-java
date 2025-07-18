/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract class RegisterAllocator {

    // Allocate an input parameter register.
    abstract byte allocate(String name, boolean required, Object defaultValue, String builtin, boolean temp);

    // Allocate a temp register for a variable by name.
    abstract byte shadow(String name);

    // Gets a register that _has_ to exist by name.
    abstract byte getRegister(String name);

    // Get the number of temp registers required by the program.
    abstract int getTempRegisterCount();

    abstract List<RegisterDefinition> getRegistry();

    static final class FlatAllocator extends RegisterAllocator {
        private final List<RegisterDefinition> registry = new ArrayList<>();
        private final Map<String, Byte> registryIndex = new HashMap<>();
        private int tempRegisters = 0;

        @Override
        public byte allocate(String name, boolean required, Object defaultValue, String builtin, boolean temp) {
            if (registryIndex.containsKey(name)) {
                throw new RulesEvaluationError("Duplicate variable name found in rules: " + name);
            }
            var register = new RegisterDefinition(name, required, defaultValue, builtin, temp);
            registryIndex.put(name, (byte) registry.size());
            registry.add(register);
            return (byte) (registry.size() - 1);
        }

        @Override
        public byte shadow(String name) {
            Byte current = registryIndex.get(name);
            if (current != null) {
                return current;
            }
            tempRegisters++;
            return allocate(name, false, null, null, true);
        }

        @Override
        public byte getRegister(String name) {
            var result = registryIndex.get(name);
            if (result == null) {
                return allocate(name, false, null, null, true);
            }
            return result;
        }

        @Override
        List<RegisterDefinition> getRegistry() {
            return registry;
        }

        @Override
        int getTempRegisterCount() {
            return tempRegisters;
        }
    }
}
