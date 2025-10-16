/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.context.Context;

class BytecodeEvaluatorTest {

    private BytecodeEvaluator evaluator;
    private Bytecode bytecode;
    private BytecodeWriter writer;
    private RulesFunction mockFunction;

    @BeforeEach
    void setUp() {
        writer = new BytecodeWriter();
        mockFunction = new TestFunction();
    }

    @Test
    void testBasicConditionEvaluation() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex(Boolean.TRUE));
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        assertTrue(evaluator.test(0));
    }

    @Test
    void testBooleanOperations() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex(Boolean.FALSE));
        writer.writeByte(Opcodes.NOT);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        assertTrue(evaluator.test(0));
    }

    @Test
    void testIsSetOperation() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("value"));
        writer.writeByte(Opcodes.ISSET);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        assertTrue(evaluator.test(0));

        writer = new BytecodeWriter();
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex(null));
        writer.writeByte(Opcodes.ISSET);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        assertFalse(evaluator.test(0));
    }

    @Test
    void testListOperations() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("a"));
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("b"));
        writer.writeByte(Opcodes.LIST2);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    void testMapOperations() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("value"));
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("key"));
        writer.writeByte(Opcodes.MAP1);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    void testStringEqualsOperation() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("test"));
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("test"));
        writer.writeByte(Opcodes.STRING_EQUALS);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        assertTrue(evaluator.test(0));

        writer = new BytecodeWriter();
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("test1"));
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("test2"));
        writer.writeByte(Opcodes.STRING_EQUALS);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        assertFalse(evaluator.test(0));
    }

    @Test
    void testGetPropertyOperation() {
        Map<String, Object> map = new HashMap<>();
        map.put("prop", "value");

        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex(map));
        writer.writeByte(Opcodes.GET_PROPERTY);
        writer.writeShort(writer.getConstantIndex("prop"));
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    void testGetIndexOperation() {
        List<String> list = Arrays.asList("a", "b", "c");

        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex(list));
        writer.writeByte(Opcodes.GET_INDEX);
        writer.writeByte(1);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    void testFunctionCall() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("input"));
        writer.writeByte(Opcodes.FN1);
        writer.writeByte(0);
        writer.writeByte(Opcodes.RETURN_VALUE);

        writer.registerFunction("testFn");

        RulesFunction[] functions = {mockFunction};
        bytecode = buildBytecode(new RegisterDefinition[0], functions);
        evaluator = createEvaluator(bytecode);

        assertTrue(evaluator.test(0));
    }

    @Test
    void testResolveTemplate() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("Hello"));
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex(" "));
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("World"));
        writer.writeByte(Opcodes.RESOLVE_TEMPLATE);
        writer.writeByte(3);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    void testJumpNotNullOrPop() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("value"));
        writer.writeByte(Opcodes.JNN_OR_POP);
        writer.writeShort(2);
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("fallback"));
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    public void testDontJumpAndReturnFallback() {
        writer = new BytecodeWriter();
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex(null));
        writer.writeByte(Opcodes.JNN_OR_POP);
        writer.writeShort(2);
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("fallback"));
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    void testReturnEndpoint() {
        writer.markResultStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("https://example.com"));
        writer.writeByte(Opcodes.RETURN_ENDPOINT);
        writer.writeByte(0);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        Endpoint endpoint = evaluator.resolveResult(0);
        assertNotNull(endpoint);
        assertEquals(URI.create("https://example.com"), endpoint.uri());
    }

    @Test
    void testReturnError() {
        writer.markResultStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("Error message"));
        writer.writeByte(Opcodes.RETURN_ERROR);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        assertThrows(RulesEvaluationError.class, () -> evaluator.resolveResult(0));
    }

    @Test
    void testLoadConstantWide() {
        for (int i = 0; i < 300; i++) {
            writer.getConstantIndex("const" + i);
        }

        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST_W);
        writer.writeShort(256);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    void testStackResizing() {
        writer.markConditionStart();
        for (int i = 0; i < 20; i++) {
            writer.writeByte(Opcodes.LOAD_CONST);
            writer.writeByte(writer.getConstantIndex(i));
        }
        writer.writeByte(Opcodes.LISTN);
        writer.writeByte(20);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    void testSubstringOperation() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("Hello World"));
        writer.writeByte(Opcodes.SUBSTRING);
        writer.writeByte(0);
        writer.writeByte(5);
        writer.writeByte(0);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    void testParseUrlOperation() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("https://example.com/path"));
        writer.writeByte(Opcodes.PARSE_URL);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    void testUnknownOpcode() {
        writer.markConditionStart();
        writer.writeByte((byte) 250);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        assertThrows(RulesEvaluationError.class, () -> evaluator.test(0));
    }

    @Test
    void testTestRegisterNotSet() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.TEST_REGISTER_NOT_SET);
        writer.writeByte(0);
        writer.writeByte(Opcodes.RETURN_VALUE);

        RegisterDefinition[] registers = {
                new RegisterDefinition("param1", false, null, null, false)
        };

        bytecode = buildBytecode(registers);
        evaluator = createEvaluator(bytecode);

        assertTrue(evaluator.test(0));
    }

    @Test
    void testList0() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LIST0);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    void testList1() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("item"));
        writer.writeByte(Opcodes.LIST1);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    void testMap0() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.MAP0);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    void testMap2() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("v1"));
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("k1"));
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("v2"));
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("k2"));
        writer.writeByte(Opcodes.MAP2);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    void testMap3() {
        writer.markConditionStart();
        for (int i = 1; i <= 3; i++) {
            writer.writeByte(Opcodes.LOAD_CONST);
            writer.writeByte(writer.getConstantIndex("v" + i));
            writer.writeByte(Opcodes.LOAD_CONST);
            writer.writeByte(writer.getConstantIndex("k" + i));
        }
        writer.writeByte(Opcodes.MAP3);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    void testMap4() {
        writer.markConditionStart();
        for (int i = 1; i <= 4; i++) {
            writer.writeByte(Opcodes.LOAD_CONST);
            writer.writeByte(writer.getConstantIndex("v" + i));
            writer.writeByte(Opcodes.LOAD_CONST);
            writer.writeByte(writer.getConstantIndex("k" + i));
        }
        writer.writeByte(Opcodes.MAP4);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    void testMapN() {
        writer.markConditionStart();
        for (int i = 1; i <= 5; i++) {
            writer.writeByte(Opcodes.LOAD_CONST);
            writer.writeByte(writer.getConstantIndex("v" + i));
            writer.writeByte(Opcodes.LOAD_CONST);
            writer.writeByte(writer.getConstantIndex("k" + i));
        }
        writer.writeByte(Opcodes.MAPN);
        writer.writeByte(5);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    void testFn2() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("arg1"));
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("arg2"));
        writer.writeByte(Opcodes.FN2);
        writer.writeByte(0);
        writer.writeByte(Opcodes.RETURN_VALUE);

        writer.registerFunction("fn2");

        RulesFunction fn2 = new RulesFunction() {
            public int getArgumentCount() {
                return 2;
            }

            public String getFunctionName() {
                return "fn2";
            }

            public Object apply2(Object a, Object b) {
                return Boolean.TRUE;
            }
        };

        bytecode = buildBytecode(new RegisterDefinition[0], new RulesFunction[] {fn2});
        evaluator = createEvaluator(bytecode);

        assertTrue(evaluator.test(0));
    }

    @Test
    void testFn3() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("a"));
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("b"));
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("c"));
        writer.writeByte(Opcodes.FN3);
        writer.writeByte(0);
        writer.writeByte(Opcodes.RETURN_VALUE);

        writer.registerFunction("fn3");

        RulesFunction fn3 = new RulesFunction() {
            public int getArgumentCount() {
                return 3;
            }

            public String getFunctionName() {
                return "fn3";
            }

            public Object apply(Object... args) {
                return Boolean.TRUE;
            }
        };

        bytecode = buildBytecode(new RegisterDefinition[0], new RulesFunction[] {fn3});
        evaluator = createEvaluator(bytecode);

        assertTrue(evaluator.test(0));
    }

    @Test
    void testFn() {
        writer.markConditionStart();
        for (int i = 0; i < 4; i++) {
            writer.writeByte(Opcodes.LOAD_CONST);
            writer.writeByte(writer.getConstantIndex("arg" + i));
        }
        writer.writeByte(Opcodes.FN);
        writer.writeByte(0);
        writer.writeByte(Opcodes.RETURN_VALUE);

        writer.registerFunction("fn4");

        RulesFunction fn4 = new RulesFunction() {
            public int getArgumentCount() {
                return 4;
            }

            public String getFunctionName() {
                return "fn4";
            }

            public Object apply(Object... args) {
                return Boolean.TRUE;
            }
        };

        bytecode = buildBytecode(new RegisterDefinition[0], new RulesFunction[] {fn4});
        evaluator = createEvaluator(bytecode);

        assertTrue(evaluator.test(0));
    }

    @Test
    void testGetPropertyReg() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");

        writer.markConditionStart();
        writer.writeByte(Opcodes.GET_PROPERTY_REG);
        writer.writeByte(0);
        writer.writeShort(writer.getConstantIndex("key"));
        writer.writeByte(Opcodes.RETURN_VALUE);

        RegisterDefinition[] registers = {
                new RegisterDefinition("param1", false, map, null, false)
        };

        bytecode = buildBytecode(registers);
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    void testGetIndexReg() {
        List<String> list = Arrays.asList("a", "b", "c");

        writer.markConditionStart();
        writer.writeByte(Opcodes.GET_INDEX_REG);
        writer.writeByte(0);
        writer.writeByte(1);
        writer.writeByte(Opcodes.RETURN_VALUE);

        RegisterDefinition[] registers = {
                new RegisterDefinition("param1", false, list, null, false)
        };

        bytecode = buildBytecode(registers);
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    void testIsTrue() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex(Boolean.TRUE));
        writer.writeByte(Opcodes.IS_TRUE);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        assertTrue(evaluator.test(0));
    }

    @Test
    void testTestRegisterIsTrue() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.TEST_REGISTER_IS_TRUE);
        writer.writeByte(0);
        writer.writeByte(Opcodes.RETURN_VALUE);

        RegisterDefinition[] registers = {
                new RegisterDefinition("param1", false, Boolean.TRUE, null, false)
        };

        bytecode = buildBytecode(registers);
        evaluator = createEvaluator(bytecode);

        assertTrue(evaluator.test(0));
    }

    @Test
    void testTestRegisterIsFalse() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.TEST_REGISTER_IS_FALSE);
        writer.writeByte(0);
        writer.writeByte(Opcodes.RETURN_VALUE);

        RegisterDefinition[] registers = {
                new RegisterDefinition("param1", false, Boolean.FALSE, null, false)
        };

        bytecode = buildBytecode(registers);
        evaluator = createEvaluator(bytecode);

        assertTrue(evaluator.test(0));
    }

    @Test
    void testEquals() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex(42));
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex(42));
        writer.writeByte(Opcodes.EQUALS);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        assertTrue(evaluator.test(0));
    }

    @Test
    void testBooleanEquals() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex(Boolean.TRUE));
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex(Boolean.TRUE));
        writer.writeByte(Opcodes.BOOLEAN_EQUALS);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        assertTrue(evaluator.test(0));
    }

    @Test
    void testIsValidHostLabel() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("example"));
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex(Boolean.FALSE));
        writer.writeByte(Opcodes.IS_VALID_HOST_LABEL);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        assertTrue(evaluator.test(0));
    }

    @Test
    void testUriEncode() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("hello world"));
        writer.writeByte(Opcodes.URI_ENCODE);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        evaluator.test(0);
    }

    @Test
    void testSplitWithLimit() {
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("a--b--c--d"));
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("--"));
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex(2));
        writer.writeByte(Opcodes.SPLIT);
        // Get the second element (should be "b--c--d")
        writer.writeByte(Opcodes.GET_INDEX);
        writer.writeByte(1);
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(writer.getConstantIndex("b--c--d"));
        writer.writeByte(Opcodes.STRING_EQUALS);
        writer.writeByte(Opcodes.RETURN_VALUE);

        bytecode = buildBytecode();
        evaluator = createEvaluator(bytecode);

        assertTrue(evaluator.test(0));
    }

    private BytecodeEvaluator createEvaluator(Bytecode bytecode) {
        RegisterFiller filler = RegisterFiller.of(bytecode, Collections.emptyMap());
        BytecodeEvaluator eval = new BytecodeEvaluator(bytecode, new RulesExtension[0], filler);
        eval.reset(Context.empty(), Collections.emptyMap());
        return eval;
    }

    private Bytecode buildBytecode() {
        return buildBytecode(new RegisterDefinition[0]);
    }

    private Bytecode buildBytecode(RegisterDefinition[] registers) {
        return buildBytecode(registers, new RulesFunction[0]);
    }

    private Bytecode buildBytecode(RegisterDefinition[] registers, RulesFunction[] functions) {
        int[] bddNodes = new int[] {-1, 1, -1};
        return writer.build(registers, functions, bddNodes, 1);
    }

    private static class TestFunction implements RulesFunction {
        @Override
        public int getArgumentCount() {
            return 1;
        }

        @Override
        public String getFunctionName() {
            return "testFn";
        }

        @Override
        public Object apply1(Object arg) {
            return Boolean.TRUE;
        }
    }
}
