/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RegisterAllocatorTest {

    @Test
    void testAllocateInputParameter() {
        RegisterAllocator allocator = new RegisterAllocator();

        byte index = allocator.allocate("region", true, null, null, false);

        assertEquals(0, index);
        assertEquals(1, allocator.getRegistry().size());

        RegisterDefinition def = allocator.getRegistry().get(0);
        assertEquals("region", def.name());
        assertTrue(def.required());
        assertNull(def.defaultValue());
        assertNull(def.builtin());
        assertFalse(def.temp());
    }

    @Test
    void testAllocateWithDefault() {
        RegisterAllocator allocator = new RegisterAllocator();

        byte index = allocator.allocate("useDualStack", false, false, null, false);

        assertEquals(0, index);
        RegisterDefinition def = allocator.getRegistry().get(0);
        assertEquals("useDualStack", def.name());
        assertFalse(def.required());
        assertEquals(false, def.defaultValue());
    }

    @Test
    void testAllocateWithBuiltin() {
        RegisterAllocator allocator = new RegisterAllocator();

        byte index = allocator.allocate("endpoint", false, null, "SDK::Endpoint", false);

        assertEquals(0, index);
        RegisterDefinition def = allocator.getRegistry().get(0);
        assertEquals("endpoint", def.name());
        assertEquals("SDK::Endpoint", def.builtin());
    }

    @Test
    void testAllocateTempRegister() {
        RegisterAllocator allocator = new RegisterAllocator();

        byte index = allocator.allocate("temp_var", false, null, null, true);

        assertEquals(0, index);
        RegisterDefinition def = allocator.getRegistry().get(0);
        assertTrue(def.temp());
    }

    @Test
    void testMultipleAllocations() {
        RegisterAllocator allocator = new RegisterAllocator();

        byte idx1 = allocator.allocate("var1", true, null, null, false);
        byte idx2 = allocator.allocate("var2", false, "default", null, false);
        byte idx3 = allocator.allocate("var3", false, null, "builtin", false);

        assertEquals(0, idx1);
        assertEquals(1, idx2);
        assertEquals(2, idx3);
        assertEquals(3, allocator.getRegistry().size());
    }

    @Test
    void testDuplicateNameThrows() {
        RegisterAllocator allocator = new RegisterAllocator();

        allocator.allocate("duplicate", true, null, null, false);

        assertThrows(RulesEvaluationError.class, () -> allocator.allocate("duplicate", false, null, null, false));
    }

    @Test
    void testGetOrAllocateRegisterExisting() {
        RegisterAllocator allocator = new RegisterAllocator();

        byte first = allocator.allocate("existing", true, null, null, false);
        byte second = allocator.getOrAllocateRegister("existing");

        assertEquals(first, second);
        assertEquals(1, allocator.getRegistry().size());
    }

    @Test
    void testGetOrAllocateRegisterNew() {
        RegisterAllocator allocator = new RegisterAllocator();

        byte index = allocator.getOrAllocateRegister("new_temp");

        assertEquals(0, index);
        assertEquals(1, allocator.getRegistry().size());

        RegisterDefinition def = allocator.getRegistry().get(0);
        assertEquals("new_temp", def.name());
        assertFalse(def.required());
        assertNull(def.defaultValue());
        assertNull(def.builtin());
        assertTrue(def.temp());
    }

    @Test
    void testGetRegisterExisting() {
        RegisterAllocator allocator = new RegisterAllocator();

        allocator.allocate("test", true, null, null, false);
        byte index = allocator.getRegister("test");

        assertEquals(0, index);
    }

    @Test
    void testGetRegisterNonExistentThrows() {
        RegisterAllocator allocator = new RegisterAllocator();

        assertThrows(IllegalStateException.class, () -> allocator.getRegister("nonexistent"));
    }

    @Test
    void testMaxRegisters() {
        RegisterAllocator allocator = new RegisterAllocator();

        // Allocate 255 registers (max for byte indexing)
        for (int i = 0; i < 256; i++) {
            allocator.allocate("var" + i, false, null, null, false);
        }

        assertEquals(256, allocator.getRegistry().size());

        // 256th should fail
        assertThrows(IllegalStateException.class, () -> allocator.allocate("var256", false, null, null, false));
    }
}
