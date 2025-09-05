/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BytecodeTest {

    @Test
    void testConstructorValidation() {
        assertDoesNotThrow(() -> new Bytecode(
                new byte[0],
                new int[0],
                new int[0],
                new RegisterDefinition[0],
                new Object[0],
                new RulesFunction[0],
                new int[0], // Valid: empty BDD nodes
                0));

        assertThrows(IllegalArgumentException.class,
                () -> new Bytecode(
                        new byte[0],
                        new int[0],
                        new int[0],
                        new RegisterDefinition[0],
                        new Object[0],
                        new RulesFunction[0],
                        new int[] {1, 2}, // Invalid: not multiple of 3
                        0));
    }

    @Test
    void testRegisterTemplateCreation() {
        RegisterDefinition[] defs = {
                new RegisterDefinition("noDefault", false, null, null, false),
                new RegisterDefinition("withDefault", false, "defaultValue", null, false),
                new RegisterDefinition("withBuiltin", false, null, "SDK::Endpoint", false),
                new RegisterDefinition("withBoth", false, "defaultValue", "SDK::Endpoint", false)
        };

        Bytecode bytecode = new Bytecode(
                new byte[0],
                new int[0],
                new int[0],
                defs,
                new Object[0],
                new RulesFunction[0],
                new int[0],
                0);

        Object[] template = bytecode.getRegisterTemplate();

        assertNull(template[0]); // No default, no builtin -> null
        assertEquals("defaultValue", template[1]); // Default, no builtin -> default in template
        // No default, has builtin -> null in template (builtin evaluated at runtime)
        assertNull(template[2]);
        // Default and builtin -> null in template (builtin takes precedence, default is fallback)
        assertNull(template[3]);
    }

    @Test
    void testInputRegisterMapExcludesTemp() {
        RegisterDefinition[] defs = {
                new RegisterDefinition("input1", false, null, null, false),
                new RegisterDefinition("temp1", false, null, null, true), // temp
                new RegisterDefinition("input2", false, null, null, false),
                new RegisterDefinition("temp2", false, null, null, true) // temp
        };

        Bytecode bytecode = new Bytecode(
                new byte[0],
                new int[0],
                new int[0],
                defs,
                new Object[0],
                new RulesFunction[0],
                new int[0],
                0);

        Map<String, Integer> inputMap = bytecode.getInputRegisterMap();

        assertEquals(2, inputMap.size());
        assertEquals(0, inputMap.get("input1"));
        assertEquals(2, inputMap.get("input2"));
        assertNull(inputMap.get("temp1"));
        assertNull(inputMap.get("temp2"));
    }

    @Test
    void testBuiltinIndicesTracking() {
        RegisterDefinition[] defs = {
                new RegisterDefinition("noBuiltin", false, null, null, false),
                new RegisterDefinition("withBuiltin1", false, null, "Builtin1", false),
                new RegisterDefinition("withDefault", false, "default", null, false),
                new RegisterDefinition("withBuiltin2", false, "default", "Builtin2", false)
        };

        Bytecode bytecode = new Bytecode(
                new byte[0],
                new int[0],
                new int[0],
                defs,
                new Object[0],
                new RulesFunction[0],
                new int[0],
                0);

        int[] builtinIndices = bytecode.getBuiltinIndices();

        // Should track ALL registers with builtins (including those with defaults)
        assertEquals(2, builtinIndices.length);
        assertEquals(1, builtinIndices[0]); // withBuiltin1 at index 1
        assertEquals(3, builtinIndices[1]); // withBuiltin2 at index 3
    }

    @Test
    void testHardRequiredIndices() {
        RegisterDefinition[] defs = {
                new RegisterDefinition("required1", true, null, null, false), // Hard required
                new RegisterDefinition("hasDefault", true, "default", null, false), // Not hard required (has default)
                new RegisterDefinition("hasBuiltin", true, null, "Builtin", false), // Not hard required (has builtin)
                new RegisterDefinition("required2", true, null, null, false), // Hard required
                new RegisterDefinition("temp", true, null, null, true), // Not hard required (temp)
                new RegisterDefinition("optional", false, null, null, false) // Not required
        };

        Bytecode bytecode = new Bytecode(
                new byte[0],
                new int[0],
                new int[0],
                defs,
                new Object[0],
                new RulesFunction[0],
                new int[0],
                0);

        int[] hardRequired = bytecode.getHardRequiredIndices();

        // Only truly required: no default, no builtin, not temp
        assertEquals(2, hardRequired.length);
        assertEquals(0, hardRequired[0]); // required1
        assertEquals(3, hardRequired[1]); // required2
    }

    @Test
    void testBddNodeCount() {
        int[] bddNodes = {
                0,
                1,
                -1,
                1,
                100_000_000,
                100_000_001,
                2,
                100_000_002,
                -1
        };

        Bytecode bytecode = new Bytecode(
                new byte[0],
                new int[0],
                new int[0],
                new RegisterDefinition[0],
                new Object[0],
                new RulesFunction[0],
                bddNodes,
                1);

        assertEquals(3, bytecode.getBddNodeCount());
        assertEquals(1, bytecode.getBddRootRef());
        assertArrayEquals(bddNodes, bytecode.getBddNodes());
    }

    @Test
    void testGettersReturnCorrectValues() {
        byte[] bytecodeData = {1, 2, 3};
        int[] condOffsets = {0, 10};
        int[] resOffsets = {20};
        Object[] constants = {"const1", 42};
        RulesFunction[] functions = {new TestFunction("fn1", 1)};

        Bytecode bytecode = new Bytecode(
                bytecodeData,
                condOffsets,
                resOffsets,
                new RegisterDefinition[0],
                constants,
                functions,
                new int[0],
                0);

        assertArrayEquals(bytecodeData, bytecode.getBytecode());
        assertEquals(2, bytecode.getConditionCount());
        assertEquals(1, bytecode.getResultCount());
        assertEquals(2, bytecode.getConstantPoolCount());
        assertEquals("const1", bytecode.getConstant(0));
        assertEquals(42, bytecode.getConstant(1));
        assertEquals(1, bytecode.getFunctions().length);
        assertEquals("fn1", bytecode.getFunctions()[0].getFunctionName());
    }

    private static class TestFunction implements RulesFunction {
        private final String name;
        private final int argCount;

        TestFunction(String name, int argCount) {
            this.name = name;
            this.argCount = argCount;
        }

        @Override
        public String getFunctionName() {
            return name;
        }

        @Override
        public int getArgumentCount() {
            return argCount;
        }
    }
}
