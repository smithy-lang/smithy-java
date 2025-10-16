/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;

class RegisterFillerTest {

    @Test
    void testDefaultValues() {
        Bytecode bytecode = new Bytecode(
                new byte[0],
                new int[0],
                new int[0],
                new RegisterDefinition[] {
                        new RegisterDefinition("region", true, null, null, false),
                        new RegisterDefinition("useDualStack", false, false, null, false),
                        new RegisterDefinition("useFips", false, true, null, false)
                },
                new Object[0],
                new RulesFunction[0],
                new int[0],
                0);

        RegisterFiller filler = RegisterFiller.of(bytecode, Map.of());

        Map<String, Object> params = Map.of("region", "us-west-2");

        Object[] registers = new Object[3];
        filler.fillRegisters(registers, Context.empty(), params);

        assertEquals("us-west-2", registers[0]);
        assertEquals(false, registers[1]);
        assertEquals(true, registers[2]);
    }

    @Test
    void testBuiltinProvider() {
        Bytecode bytecode = new Bytecode(
                new byte[0],
                new int[0],
                new int[0],
                new RegisterDefinition[] {new RegisterDefinition("endpoint", false, null, "SDK::Endpoint", false)},
                new Object[0],
                new RulesFunction[0],
                new int[0],
                0);

        Map<String, Function<Context, Object>> builtinProviders = Map.of(
                "SDK::Endpoint",
                ctx -> "https://custom.example.com");

        RegisterFiller filler = RegisterFiller.of(bytecode, builtinProviders);

        Object[] registers = new Object[1];
        filler.fillRegisters(registers, Context.empty(), Map.of());

        assertEquals("https://custom.example.com", registers[0]);
    }

    @Test
    void testParameterOverridesDefault() {
        Bytecode bytecode = new Bytecode(
                new byte[0],
                new int[0],
                new int[0],
                new RegisterDefinition[] {new RegisterDefinition("useDualStack", false, false, null, false)},
                new Object[0],
                new RulesFunction[0],
                new int[0],
                0);

        RegisterFiller filler = RegisterFiller.of(bytecode, Map.of());

        Map<String, Object> params = Map.of("useDualStack", true);

        Object[] registers = new Object[1];
        filler.fillRegisters(registers, Context.empty(), params);

        assertEquals(true, registers[0]);
    }

    @Test
    void testParameterOverridesBuiltin() {
        Bytecode bytecode = new Bytecode(
                new byte[0],
                new int[0],
                new int[0],
                new RegisterDefinition[] {new RegisterDefinition("endpoint", false, null, "SDK::Endpoint", false)},
                new Object[0],
                new RulesFunction[0],
                new int[0],
                0);

        Map<String, Function<Context, Object>> builtinProviders = Map.of(
                "SDK::Endpoint",
                ctx -> "https://builtin.example.com");

        RegisterFiller filler = RegisterFiller.of(bytecode, builtinProviders);

        Map<String, Object> params = Map.of("endpoint", "https://override.example.com");

        Object[] registers = new Object[1];
        filler.fillRegisters(registers, Context.empty(), params);

        assertEquals("https://override.example.com", registers[0]);
    }

    @Test
    void testMissingRequiredParameterThrows() {
        Bytecode bytecode = new Bytecode(
                new byte[0],
                new int[0],
                new int[0],
                new RegisterDefinition[] {new RegisterDefinition("region", true, null, null, false)},
                new Object[0],
                new RulesFunction[0],
                new int[0],
                0);

        RegisterFiller filler = RegisterFiller.of(bytecode, Map.of());

        Object[] registers = new Object[1];

        RulesEvaluationError error = assertThrows(RulesEvaluationError.class,
                () -> filler.fillRegisters(registers, Context.empty(), Map.of()));

        assertTrue(error.getMessage().contains("region"));
    }

    @Test
    void testLargeRegisterCount() {
        // Test with 100 registers to trigger LargeRegisterFiller
        RegisterDefinition[] definitions = new RegisterDefinition[100];

        for (int i = 0; i < 100; i++) {
            definitions[i] = new RegisterDefinition("param" + i, false, "default" + i, null, false);
        }

        Bytecode bytecode = new Bytecode(
                new byte[0],
                new int[0],
                new int[0],
                definitions,
                new Object[0],
                new RulesFunction[0],
                new int[0],
                0);

        RegisterFiller filler = RegisterFiller.of(bytecode, Map.of());

        Map<String, Object> params = Map.of(
                "param0",
                "override0",
                "param50",
                "override50",
                "param99",
                "override99");

        Object[] registers = new Object[100];
        filler.fillRegisters(registers, Context.empty(), params);

        assertEquals("override0", registers[0]);
        assertEquals("default25", registers[25]);
        assertEquals("override50", registers[50]);
        assertEquals("default75", registers[75]);
        assertEquals("override99", registers[99]);
    }

    @Test
    void testTempRegistersIgnored() {
        Bytecode bytecode = new Bytecode(
                new byte[0],
                new int[0],
                new int[0],
                new RegisterDefinition[] {
                        new RegisterDefinition("input", false, "default", null, false),
                        new RegisterDefinition("temp1", false, null, null, true),
                        new RegisterDefinition("temp2", false, null, null, true)
                },
                new Object[0],
                new RulesFunction[0],
                new int[0],
                0);

        RegisterFiller filler = RegisterFiller.of(bytecode, Map.of());

        // Try to set temp registers via params, but will be ignored
        Map<String, Object> params = Map.of(
                "input",
                "value",
                "temp1",
                "should_be_ignored",
                "temp2",
                "also_ignored");

        Object[] registers = new Object[3];
        filler.fillRegisters(registers, Context.empty(), params);

        assertEquals("value", registers[0]);
        assertNull(registers[1]);
        assertNull(registers[2]);
    }

    @Test
    void testBuiltinReturnsNull() {
        Bytecode bytecode = new Bytecode(
                new byte[0],
                new int[0],
                new int[0],
                new RegisterDefinition[] {new RegisterDefinition("endpoint", false, null, "SDK::Endpoint", false)},
                new Object[0],
                new RulesFunction[0],
                new int[0],
                0);

        Map<String, Function<Context, Object>> builtinProviders = Map.of(
                "SDK::Endpoint",
                ctx -> null);

        RegisterFiller filler = RegisterFiller.of(bytecode, builtinProviders);

        Object[] registers = new Object[1];
        filler.fillRegisters(registers, Context.empty(), Map.of());

        assertNull(registers[0]);
    }

    @Test
    void testMixedRequiredOptionalAndBuiltin() {
        Bytecode bytecode = new Bytecode(
                new byte[0],
                new int[0],
                new int[0],
                new RegisterDefinition[] {
                        new RegisterDefinition("required1", true, null, null, false),
                        new RegisterDefinition("optional1", false, "defaultOpt", null, false),
                        new RegisterDefinition("builtin1", false, null, "TestBuiltin", false),
                        new RegisterDefinition("required2", true, null, null, false),
                        new RegisterDefinition("optionalWithBuiltin", false, "defaultVal", "TestBuiltin2", false)
                },
                new Object[0],
                new RulesFunction[0],
                new int[0],
                0);

        Map<String, Function<Context, Object>> builtinProviders = Map.of(
                "TestBuiltin",
                ctx -> "builtinValue",
                "TestBuiltin2",
                ctx -> "builtinValue2");

        RegisterFiller filler = RegisterFiller.of(bytecode, builtinProviders);

        Map<String, Object> params = Map.of("required1", "req1", "required2", "req2");

        Object[] registers = new Object[5];
        filler.fillRegisters(registers, Context.empty(), params);

        assertEquals("req1", registers[0]);
        assertEquals("defaultOpt", registers[1]);
        assertEquals("builtinValue", registers[2]);
        assertEquals("req2", registers[3]);
        assertEquals("builtinValue2", registers[4]);
    }

    @Test
    void testSmallRegisterCountUsesFastFiller() {
        RegisterDefinition[] definitions = new RegisterDefinition[30];
        for (int i = 0; i < 30; i++) {
            definitions[i] = new RegisterDefinition(
                    "param" + i,
                    i < 5, // first 5 are required
                    i >= 10 ? "default" + i : null, // 10+ have defaults
                    null,
                    false);
        }

        Bytecode bytecode = new Bytecode(
                new byte[0],
                new int[0],
                new int[0],
                definitions,
                new Object[0],
                new RulesFunction[0],
                new int[0],
                0);

        RegisterFiller filler = RegisterFiller.of(bytecode, Map.of());

        Map<String, Object> params = new HashMap<>();
        // Fill required params
        for (int i = 0; i < 5; i++) {
            params.put("param" + i, "value" + i);
        }
        // Override some defaults
        params.put("param15", "override15");

        Object[] registers = new Object[30];
        filler.fillRegisters(registers, Context.empty(), params);

        // Check required params filled
        for (int i = 0; i < 5; i++) {
            assertEquals("value" + i, registers[i]);
        }

        // Check overridden default
        assertEquals("override15", registers[15]); // should be overridden value, not default

        // Check non-overridden defaults
        assertEquals("default20", registers[20]); // should use default
        assertEquals("default25", registers[25]); // should use default

        // Check unfilled (no default, not required, not provided)
        assertNull(registers[7]);
        assertNull(registers[8]);
    }
}
