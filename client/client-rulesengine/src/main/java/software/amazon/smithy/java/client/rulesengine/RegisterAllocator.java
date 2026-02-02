/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.context.Context;

final class RegisterAllocator {
    private final List<RegisterDefinition> registry = new ArrayList<>();
    private final Map<String, Byte> registryIndex = new HashMap<>();
    private final Map<String, Context.Key<?>> builtinKeys;

    RegisterAllocator(Map<String, Context.Key<?>> builtinKeys) {
        this.builtinKeys = builtinKeys;
    }

    // Allocate an input parameter register.
    byte allocate(String name, boolean required, Object defaultValue, String builtin, boolean temp) {
        if (registryIndex.containsKey(name)) {
            throw new RulesEvaluationError("Duplicate variable name found in rules: " + name);
        } else if (registry.size() >= 256) {
            throw new IllegalStateException("Too many registers: " + registry.size());
        }
        // Resolve builtin key at compile time
        Context.Key<?> builtinKey = builtin != null ? builtinKeys.get(builtin) : null;
        var register = new RegisterDefinition(name, required, defaultValue, builtin, builtinKey, temp);
        byte index = (byte) registry.size();
        registryIndex.put(name, index);
        registry.add(register);
        return index;
    }

    // Get or allocate a temp register for a variable by name.
    byte getOrAllocateRegister(String name) {
        Byte existing = registryIndex.get(name);
        if (existing != null) {
            return existing;
        }
        return allocate(name, false, null, null, true);
    }

    // Gets a register by name, throwing if it doesn't exist.
    byte getRegister(String name) {
        Byte result = registryIndex.get(name);
        if (result == null) {
            throw new IllegalStateException("Variable '" + name + "' is referenced but never defined");
        }
        return result;
    }

    List<RegisterDefinition> getRegistry() {
        return registry;
    }
}
