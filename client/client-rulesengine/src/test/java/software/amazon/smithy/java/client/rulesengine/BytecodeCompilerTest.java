/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.rulesengine.language.Endpoint;
import software.amazon.smithy.rulesengine.language.evaluation.value.Value;
import software.amazon.smithy.rulesengine.language.syntax.Identifier;
import software.amazon.smithy.rulesengine.language.syntax.expressions.Expression;
import software.amazon.smithy.rulesengine.language.syntax.expressions.Template;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.BooleanEquals;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.GetAttr;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.IsSet;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.IsValidHostLabel;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.LibraryFunction;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.Not;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.ParseUrl;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.StringEquals;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.Substring;
import software.amazon.smithy.rulesengine.language.syntax.expressions.literal.Literal;
import software.amazon.smithy.rulesengine.language.syntax.parameters.Parameter;
import software.amazon.smithy.rulesengine.language.syntax.parameters.ParameterType;
import software.amazon.smithy.rulesengine.language.syntax.parameters.Parameters;
import software.amazon.smithy.rulesengine.language.syntax.rule.Condition;
import software.amazon.smithy.rulesengine.language.syntax.rule.EndpointRule;
import software.amazon.smithy.rulesengine.language.syntax.rule.ErrorRule;
import software.amazon.smithy.rulesengine.language.syntax.rule.NoMatchRule;
import software.amazon.smithy.rulesengine.language.syntax.rule.Rule;
import software.amazon.smithy.rulesengine.logic.bdd.Bdd;
import software.amazon.smithy.rulesengine.traits.EndpointBddTrait;

class BytecodeCompilerTest {

    private Map<String, RulesFunction> functions;
    private Map<String, Function<Context, Object>> builtinProviders;
    private List<RulesExtension> extensions;

    @BeforeEach
    void setUp() {
        functions = new HashMap<>();
        builtinProviders = new HashMap<>();
        extensions = new ArrayList<>();

        functions.put("testFunc", new TestFunction("testFunc", 1));
        functions.put("concat", new TestFunction("concat", 2));
    }

    @Test
    void testCompileEmptyBdd() {
        EndpointBddTrait bdd = createEmptyBdd();
        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        assertNotNull(bytecode);
        assertEquals(0, bytecode.getConditionCount());
        assertEquals(1, bytecode.getResultCount()); // NoMatchRule
    }

    @Test
    void testCompileSimpleCondition() {
        Condition condition = Condition.builder()
                .fn(IsSet.ofExpressions(Expression.getReference(Identifier.of("param1"))))
                .build();

        Parameters params = Parameters.builder()
                .addParameter(Parameter.builder()
                        .name("param1")
                        .type(ParameterType.STRING)
                        .build())
                .build();

        EndpointBddTrait bdd = createBddWithConditionAndParams(condition, params);
        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        assertEquals(1, bytecode.getConditionCount());
        assertTrue(bytecode.getBytecode().length > 0);
        assertOpcodePresent(bytecode, Opcodes.TEST_REGISTER_ISSET);
    }

    @Test
    void testCompileConditionWithBinding() {
        Condition condition = Condition.builder()
                .fn(IsSet.ofExpressions(Expression.getReference(Identifier.of("input"))))
                .result(Identifier.of("hasInput"))
                .build();

        Parameters params = Parameters.builder()
                .addParameter(Parameter.builder()
                        .name("input")
                        .type(ParameterType.STRING)
                        .build())
                .build();

        EndpointBddTrait bdd = createBddWithConditionAndParams(condition, params);
        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        // Should have allocated a register for the binding
        RegisterDefinition[] registers = bytecode.getRegisterDefinitions();
        boolean foundHasInput = false;
        for (RegisterDefinition reg : registers) {
            if ("hasInput".equals(reg.name())) {
                foundHasInput = true;
                break;
            }
        }
        assertTrue(foundHasInput);

        assertOpcodePresent(bytecode, Opcodes.SET_REGISTER);
    }

    @Test
    void testCompileBooleanEquals() {
        Condition condition = Condition.builder()
                .fn(BooleanEquals.ofExpressions(
                        Expression.getReference(Identifier.of("flag")),
                        Literal.booleanLiteral(true)))
                .build();

        Parameters params = Parameters.builder()
                .addParameter(Parameter.builder()
                        .name("flag")
                        .type(ParameterType.BOOLEAN)
                        .build())
                .build();

        EndpointBddTrait bdd = createBddWithConditionAndParams(condition, params);
        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        assertOpcodePresent(bytecode, Opcodes.TEST_REGISTER_IS_TRUE);
    }

    @Test
    void testCompileStringEquals() {
        Condition condition = Condition.builder()
                .fn(StringEquals.ofExpressions(
                        Expression.getReference(Identifier.of("str1")),
                        Expression.getReference(Identifier.of("str2"))))
                .build();

        Parameters params = Parameters.builder()
                .addParameter(Parameter.builder()
                        .name("str1")
                        .type(ParameterType.STRING)
                        .build())
                .addParameter(Parameter.builder()
                        .name("str2")
                        .type(ParameterType.STRING)
                        .build())
                .build();

        EndpointBddTrait bdd = createBddWithConditionAndParams(condition, params);
        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        assertOpcodePresent(bytecode, Opcodes.STRING_EQUALS);
    }

    @Test
    void testCompileNot() {
        Condition condition = Condition.builder()
                .fn(Not.ofExpressions(
                        IsSet.ofExpressions(Expression.getReference(Identifier.of("param")))))
                .build();

        Parameters params = Parameters.builder()
                .addParameter(Parameter.builder()
                        .name("param")
                        .type(ParameterType.STRING)
                        .build())
                .build();

        EndpointBddTrait bdd = createBddWithConditionAndParams(condition, params);
        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        assertOpcodePresent(bytecode, Opcodes.TEST_REGISTER_NOT_SET);
    }

    @Test
    void testCompileEndpointRule() {
        EndpointRule rule = EndpointRule.builder()
                .endpoint(Endpoint.builder()
                        .url(Literal.stringLiteral(Template.fromString("https://example.com")))
                        .build());

        List<Rule> results = List.of(NoMatchRule.INSTANCE, rule);
        EndpointBddTrait bdd = createBddWithResults(results);
        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        assertEquals(2, bytecode.getResultCount());
        assertOpcodePresent(bytecode, Opcodes.RETURN_ENDPOINT);
    }

    @Test
    void testCompileEndpointWithHeaders() {
        Map<String, List<Expression>> headers = new HashMap<>();
        headers.put("X-Custom", List.of(Literal.stringLiteral(Template.fromString("value"))));

        EndpointRule rule = EndpointRule.builder()
                .endpoint(Endpoint.builder()
                        .url(Literal.stringLiteral(Template.fromString("https://example.com")))
                        .headers(headers)
                        .build());

        List<Rule> results = List.of(NoMatchRule.INSTANCE, rule);
        EndpointBddTrait bdd = createBddWithResults(results);
        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        // Should have constants for header name and value
        assertConstantPresent(bytecode, "X-Custom");
    }

    @Test
    void testCompileEndpointWithProperties() {
        Map<Identifier, Literal> properties = new HashMap<>();
        properties.put(Identifier.of("authSchemes"),
                Literal.tupleLiteral(List.of(Literal.stringLiteral(Template.fromString("sigv4")))));

        EndpointRule rule = EndpointRule.builder()
                .endpoint(Endpoint.builder()
                        .url(Literal.stringLiteral(Template.fromString("https://example.com")))
                        .properties(properties)
                        .build());

        List<Rule> results = List.of(NoMatchRule.INSTANCE, rule);
        EndpointBddTrait bdd = createBddWithResults(results);
        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        assertConstantPresent(bytecode, "authSchemes");
    }

    @Test
    void testCompileErrorRule() {
        ErrorRule rule = ErrorRule.builder().error(Literal.stringLiteral(Template.fromString("Invalid input")));

        List<Rule> results = List.of(NoMatchRule.INSTANCE, rule);
        EndpointBddTrait bdd = createBddWithResults(results);
        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        assertOpcodePresent(bytecode, Opcodes.RETURN_ERROR);
    }

    @Test
    void testCompileStringTemplate() {
        Template template = Template.fromString("Hello {name}!");
        Condition condition = Condition.builder()
                .fn(StringEquals.ofExpressions(
                        Literal.stringLiteral(template),
                        Literal.stringLiteral(Template.fromString("test"))))
                .build();

        Parameters params = Parameters.builder()
                .addParameter(Parameter.builder()
                        .name("name")
                        .type(ParameterType.STRING)
                        .build())
                .build();

        EndpointBddTrait bdd = createBddWithConditionAndParams(condition, params);
        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        assertOpcodePresent(bytecode, Opcodes.RESOLVE_TEMPLATE);
    }

    @Test
    void testCompileTupleLiteral() {
        Condition condition = createConditionWithExpression(
                Literal.tupleLiteral(List.of(
                        Literal.stringLiteral(Template.fromString("a")),
                        Literal.stringLiteral(Template.fromString("b")))));

        EndpointBddTrait bdd = createBddWithCondition(condition);
        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        assertOpcodePresent(bytecode, Opcodes.LIST2);
    }

    @Test
    void testCompileRecordLiteral() {
        Map<Identifier, Literal> members = new HashMap<>();
        members.put(Identifier.of("key1"), Literal.stringLiteral(Template.fromString("value1")));
        members.put(Identifier.of("key2"), Literal.stringLiteral(Template.fromString("value2")));

        Condition condition = createConditionWithExpression(Literal.recordLiteral(members));

        EndpointBddTrait bdd = createBddWithCondition(condition);
        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        assertOpcodePresent(bytecode, Opcodes.MAP2);
    }

    @Test
    void testCompileGetAttrWithPropertyAccess() {
        GetAttr getAttr = GetAttr.ofExpressions(
                Expression.getReference(Identifier.of("obj")),
                Expression.of("prop"));

        Condition condition = createConditionWithExpression(getAttr);

        Parameters params = Parameters.builder()
                .addParameter(Parameter.builder()
                        .name("obj")
                        .type(ParameterType.STRING)
                        .build())
                .build();

        EndpointBddTrait bdd = createBddWithConditionAndParams(condition, params);
        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        assertOpcodePresent(bytecode, Opcodes.GET_PROPERTY_REG);
    }

    @Test
    void testCompileGetAttrWithIndex() {
        GetAttr getAttr = GetAttr.ofExpressions(
                Expression.getReference(Identifier.of("array")),
                Expression.of("[0]"));

        Condition condition = createConditionWithExpression(getAttr);

        Parameters params = Parameters.builder()
                .addParameter(Parameter.builder()
                        .name("array")
                        .type(ParameterType.STRING_ARRAY)
                        .build())
                .build();

        EndpointBddTrait bdd = createBddWithConditionAndParams(condition, params);
        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        assertOpcodePresent(bytecode, Opcodes.GET_INDEX_REG);
    }

    @Test
    void testCompileBuiltinFunctions() {
        LibraryFunction substring = Substring.ofExpressions(
                Expression.getReference(Identifier.of("str")),
                Literal.integerLiteral(0),
                Literal.integerLiteral(5),
                Literal.booleanLiteral(false));

        Condition condition = createConditionWithExpression(substring);

        Parameters params = Parameters.builder()
                .addParameter(Parameter.builder()
                        .name("str")
                        .type(ParameterType.STRING)
                        .build())
                .build();

        EndpointBddTrait bdd = createBddWithConditionAndParams(condition, params);
        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        assertOpcodePresent(bytecode, Opcodes.SUBSTRING);
    }

    @Test
    void testCompileIsValidHostLabel() {
        LibraryFunction isValidHost = IsValidHostLabel.ofExpressions(
                Expression.getReference(Identifier.of("host")),
                Literal.booleanLiteral(true));

        Condition condition = createConditionWithExpression(isValidHost);

        Parameters params = Parameters.builder()
                .addParameter(Parameter.builder()
                        .name("host")
                        .type(ParameterType.STRING)
                        .build())
                .build();

        EndpointBddTrait bdd = createBddWithConditionAndParams(condition, params);
        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        assertOpcodePresent(bytecode, Opcodes.IS_VALID_HOST_LABEL);
    }

    @Test
    void testCompileParseUrl() {
        LibraryFunction parseUrl = ParseUrl.ofExpressions(
                Expression.getReference(Identifier.of("url")));

        Condition condition = createConditionWithExpression(parseUrl);

        Parameters params = Parameters.builder()
                .addParameter(Parameter.builder()
                        .name("url")
                        .type(ParameterType.STRING)
                        .build())
                .build();

        EndpointBddTrait bdd = createBddWithConditionAndParams(condition, params);
        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        assertOpcodePresent(bytecode, Opcodes.PARSE_URL);
    }

    @Test
    void testCompileWithParameters() {
        Parameter param1 = Parameter.builder()
                .name("Region")
                .type(ParameterType.STRING)
                .required(true)
                .build();

        Parameter param2 = Parameter.builder()
                .name("UseDualStack")
                .type(ParameterType.BOOLEAN)
                .required(true)
                .defaultValue(Value.booleanValue(false))
                .build();

        Parameter param3 = Parameter.builder()
                .name("Endpoint")
                .type(ParameterType.STRING)
                .builtIn("SDK::Endpoint")
                .build();

        Parameters params = Parameters.builder()
                .addParameter(param1)
                .addParameter(param2)
                .addParameter(param3)
                .build();

        EndpointBddTrait bdd = createBddWithParameters(params);

        builtinProviders.put("SDK::Endpoint", ctx -> "https://custom.endpoint");

        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        RegisterDefinition[] registers = bytecode.getRegisterDefinitions();
        assertEquals(3, registers.length);

        RegisterDefinition reg1 = findRegister(registers, "Region");
        assertNotNull(reg1);
        assertTrue(reg1.required());

        RegisterDefinition reg2 = findRegister(registers, "UseDualStack");
        assertNotNull(reg2);
        assertEquals(false, reg2.defaultValue());

        RegisterDefinition reg3 = findRegister(registers, "Endpoint");
        assertNotNull(reg3);
        assertEquals("SDK::Endpoint", reg3.builtin());
    }

    @Test
    void testCompileLargeList() {
        List<Literal> elements = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            elements.add(Literal.integerLiteral(i));
        }

        Condition condition = createConditionWithExpression(Literal.tupleLiteral(elements));
        EndpointBddTrait bdd = createBddWithCondition(condition);
        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        OpcodeWithValue result = findOpcodeWithValue(bytecode, Opcodes.LISTN);
        assertTrue(result.found());
        assertEquals(5, result.value());
    }

    @Test
    void testCompileLargeMap() {
        Map<Identifier, Literal> members = new HashMap<>();
        for (int i = 0; i < 6; i++) {
            members.put(Identifier.of("key" + i), Literal.integerLiteral(i));
        }

        Condition condition = createConditionWithExpression(Literal.recordLiteral(members));
        EndpointBddTrait bdd = createBddWithCondition(condition);
        BytecodeCompiler compiler = new BytecodeCompiler(extensions, bdd, functions, builtinProviders);

        Bytecode bytecode = compiler.compile();

        assertOpcodePresent(bytecode, Opcodes.MAPN);
    }

    private void assertOpcodePresent(Bytecode bytecode, byte expectedOpcode) {
        BytecodeWalker walker = new BytecodeWalker(bytecode.getBytecode());
        boolean found = false;

        while (walker.hasNext()) {
            if (walker.currentOpcode() == expectedOpcode) {
                found = true;
                break;
            }
            if (!walker.advance()) {
                break; // Unknown opcode encountered
            }
        }

        assertTrue(found, "Expected opcode " + expectedOpcode + " to be present in bytecode");
    }

    private void assertConstantPresent(Bytecode bytecode, Object expectedConstant) {
        boolean found = false;
        for (int i = 0; i < bytecode.getConstantPoolCount(); i++) {
            if (expectedConstant.equals(bytecode.getConstant(i))) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Expected constant " + expectedConstant + " to be present in constant pool");
    }

    private OpcodeWithValue findOpcodeWithValue(Bytecode bytecode, byte expectedOpcode) {
        BytecodeWalker walker = new BytecodeWalker(bytecode.getBytecode());

        while (walker.hasNext()) {
            if (walker.currentOpcode() == expectedOpcode) {
                try {
                    int value = walker.getOperand(0);
                    return new OpcodeWithValue(true, value);
                } catch (IllegalArgumentException e) {
                    // Opcode doesn't have operands
                    return new OpcodeWithValue(true, 0);
                }
            }
            if (!walker.advance()) {
                break;
            }
        }
        return new OpcodeWithValue(false, 0);
    }

    private record OpcodeWithValue(boolean found, int value) {}

    private EndpointBddTrait createEmptyBdd() {
        return EndpointBddTrait.builder()
                .parameters(Parameters.builder().build())
                .conditions(List.of())
                .results(List.of(NoMatchRule.INSTANCE))
                .bdd(new Bdd(1, 0, 1, 1, nc -> nc.accept(-1, 1, -1)))
                .build();
    }

    private EndpointBddTrait createBddWithCondition(Condition condition) {
        return createBddWithConditionAndParams(condition, Parameters.builder().build());
    }

    private EndpointBddTrait createBddWithConditionAndParams(Condition condition, Parameters params) {
        return EndpointBddTrait.builder()
                .parameters(params)
                .conditions(List.of(condition))
                .results(List.of(NoMatchRule.INSTANCE))
                .bdd(new Bdd(2, 1, 1, 2, nc -> {
                    nc.accept(-1, 1, -1); // Terminal node at index 0
                    nc.accept(0, 100000000, -1); // Condition node at index 1
                }))
                .build();
    }

    private EndpointBddTrait createBddWithResults(List<Rule> results) {
        return EndpointBddTrait.builder()
                .parameters(Parameters.builder().build())
                .conditions(List.of())
                .results(results)
                .bdd(new Bdd(100000000, 0, results.size(), 1, nc -> nc.accept(-1, 1, -1)))
                .build();
    }

    private EndpointBddTrait createBddWithParameters(Parameters params) {
        return EndpointBddTrait.builder()
                .parameters(params)
                .conditions(List.of())
                .results(List.of(NoMatchRule.INSTANCE))
                .bdd(new Bdd(1, 0, 1, 1, nc -> nc.accept(-1, 1, -1)))
                .build();
    }

    private Condition createConditionWithExpression(Expression expr) {
        return Condition.builder()
                .fn(IsSet.ofExpressions(expr))
                .build();
    }

    private RegisterDefinition findRegister(RegisterDefinition[] registers, String name) {
        for (RegisterDefinition reg : registers) {
            if (name.equals(reg.name())) {
                return reg;
            }
        }
        return null;
    }

    private static class TestFunction implements RulesFunction {
        private final String name;
        private final int argCount;

        TestFunction(String name, int argCount) {
            this.name = name;
            this.argCount = argCount;
        }

        @Override
        public int getArgumentCount() {
            return argCount;
        }

        @Override
        public String getFunctionName() {
            return name;
        }

        @Override
        public Object apply(Object... arguments) {
            return "test-result";
        }

        @Override
        public Object apply1(Object arg1) {
            return "test-result";
        }

        @Override
        public Object apply2(Object arg1, Object arg2) {
            return arg1 + "-" + arg2;
        }
    }
}
