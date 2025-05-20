/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.rulesengine.language.EndpointRuleSet;
import software.amazon.smithy.rulesengine.language.syntax.expressions.Expression;
import software.amazon.smithy.rulesengine.language.syntax.expressions.ExpressionVisitor;
import software.amazon.smithy.rulesengine.language.syntax.expressions.Reference;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.FunctionDefinition;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.GetAttr;
import software.amazon.smithy.rulesengine.language.syntax.expressions.literal.BooleanLiteral;
import software.amazon.smithy.rulesengine.language.syntax.expressions.literal.IntegerLiteral;
import software.amazon.smithy.rulesengine.language.syntax.expressions.literal.Literal;
import software.amazon.smithy.rulesengine.language.syntax.expressions.literal.RecordLiteral;
import software.amazon.smithy.rulesengine.language.syntax.expressions.literal.StringLiteral;
import software.amazon.smithy.rulesengine.language.syntax.expressions.literal.TupleLiteral;
import software.amazon.smithy.rulesengine.language.syntax.rule.Condition;
import software.amazon.smithy.rulesengine.language.syntax.rule.EndpointRule;
import software.amazon.smithy.rulesengine.language.syntax.rule.ErrorRule;
import software.amazon.smithy.rulesengine.language.syntax.rule.Rule;
import software.amazon.smithy.rulesengine.language.syntax.rule.TreeRule;

final class RulesCompiler {

    private final List<RulesExtension> extensions;
    private final EndpointRuleSet rules;

    private final Map<Object, Integer> constantPool = new LinkedHashMap<>();
    private final BiFunction<String, Context, Object> builtinProvider;

    // The parsed opcodes and operands.
    private byte[] instructions = new byte[64];
    private int instructionSize;

    // Parameters and captured variables.
    private final List<ParamDefinition> registry = new ArrayList<>();

    // A map of variable name to stack index.
    private final Map<String, Deque<Byte>> registryIndex = new HashMap<>();

    // An array of actually used functions.
    private final List<RulesFunction> usedFunctions = new ArrayList<>();

    // Index of function name to the index in usedFunctions.
    private final Map<String, Byte> usedFunctionIndex = new HashMap<>();

    // The resolved VM functions (stdLib + given functions).
    private final Map<String, RulesFunction> functions;

    // Stack of available and reusable registers.
    private final ArrayList<Map<Byte, String>> scopedRegisterStack = new ArrayList<>();
    private final Deque<Byte> availableRegisters = new ArrayDeque<>();
    private int temporaryRegisters = 0;

    RulesCompiler(
            List<RulesExtension> extensions,
            EndpointRuleSet rules,
            Map<String, RulesFunction> functions,
            BiFunction<String, Context, Object> builtinProvider,
            boolean performOptimizations
    ) {
        this.extensions = extensions;
        this.rules = rules;
        this.builtinProvider = builtinProvider;
        this.functions = functions;

        // Byte 1 is the version byte.
        instructions[0] = RulesProgram.VERSION;
        // Byte 2 the number of parameters. Byte 3 is the number of temporary registers. Both filled in at the end.
        instructionSize = 3; // start from here

        // Add parameters as registry values.
        for (var param : rules.getParameters()) {
            var defaultValue = param.getDefault().map(EndpointUtils::convertInputParamValue).orElse(null);
            var builtinValue = param.getBuiltIn().orElse(null);
            addRegister(param.getName().toString(), param.isRequired(), defaultValue, builtinValue);
        }
    }

    private byte addRegister(String name, boolean required, Object defaultValue, String builtin) {
        var register = new ParamDefinition(name, required, defaultValue, builtin);
        if (registryIndex.containsKey(name)) {
            throw new RulesEvaluationError("Duplicate variable name found in rules: " + name);
        }
        Deque<Byte> stack = new ArrayDeque<>();
        stack.push((byte) registry.size());
        registryIndex.put(name, stack);
        registry.add(register);

        // Register scopes are tracking by flipping bits of a long. That means a max of 64 registers.
        // No real rules definition would have more than 64 registers.
        if (registry.size() > 255) {
            throw new RulesEvaluationError("Too many registers added to rules engine");
        }

        return (byte) (registry.size() - 1);
    }

    private int getConstant(Object value) {
        Integer index = constantPool.get(value);
        if (index == null) {
            index = constantPool.size();
            constantPool.put(value, index);
        }
        return index;
    }

    // Gets a register that _has_ to exist by name.
    private byte getRegister(String name) {
        return registryIndex.get(name).peek();
    }

    // Used to assign registers in a stack-like manner. Not used to initialize registers.
    private byte assignRegister(String name) {
        var indices = registryIndex.get(name);
        var index = getTempRegister();
        if (indices == null) {
            indices = new ArrayDeque<>();
            registryIndex.put(name, indices);
        }
        indices.add(index);
        return index;
    }

    // Gets the next available temporary register or creates one.
    private byte getTempRegister() {
        return !availableRegisters.isEmpty()
                ? availableRegisters.pop()
                : addRegister("r" + temporaryRegisters++, false, null, null);
    }

    private byte getFunctionIndex(String name) {
        Byte index = usedFunctionIndex.get(name);
        if (index == null) {
            var fn = functions.get(name);
            if (fn == null) {
                throw new RulesEvaluationError("Rules engine referenced unknown function: " + name);
            }
            index = (byte) usedFunctionIndex.size();
            usedFunctionIndex.put(name, index);
            usedFunctions.add(fn);
        }
        return index;
    }

    RulesProgram compile() {
        for (var rule : rules.getRules()) {
            compileRule(rule);
        }

        return buildProgram();
    }

    private void compileRule(Rule rule) {
        enterScope();
        if (rule instanceof TreeRule t) {
            compileTreeRule(t);
        } else if (rule instanceof EndpointRule e) {
            compileEndpointRule(e);
        } else if (rule instanceof ErrorRule e) {
            compileErrorRule(e);
        }
        exitScope();
    }

    private void enterScope() {
        scopedRegisterStack.add(new HashMap<>());
    }

    private void exitScope() {
        var value = scopedRegisterStack.remove(scopedRegisterStack.size() - 1);
        // Free up assigned temp registers.
        for (var entry : value.entrySet()) {
            var indices = registryIndex.get(entry.getValue());
            indices.pop();
            availableRegisters.add(entry.getKey());
        }
    }

    private void compileTreeRule(TreeRule tree) {
        var jump = compileConditions(tree);
        // Compile nested rules.
        for (var rule : tree.getRules()) {
            compileRule(rule);
        }
        // Patch in the actual jump target for each condition so it skips over the rules.
        jump.patchTarget(instructions, instructionSize);
    }

    private JumpIfFalsey compileConditions(Rule rule) {
        var jump = new JumpIfFalsey();
        for (var condition : rule.getConditions()) {
            compileCondition(condition, jump);
        }
        return jump;
    }

    private void compileCondition(Condition condition, JumpIfFalsey jump) {
        compileExpression(condition.getFunction());
        // Add an instruction to store the result as a register if the condition requests it.
        condition.getResult().ifPresent(result -> {
            var varName = result.toString();
            var register = assignRegister(varName);
            var position = scopedRegisterStack.size() - 1;
            scopedRegisterStack.get(position).put(register, varName);
            add_SET_REGISTER(register);
        });
        // Add the jump instruction after each condition to skip over more conditions or skip over the rule.
        add_JUMP_IF_FALSEY(0);
        jump.addPatch(instructionSize - 2);
    }

    private void addLiteralOpcodes(Literal literal) {
        if (literal instanceof StringLiteral s) {
            var st = StringTemplate.from(s.value());
            if (st.expressionCount() == 0) {
                add_LOAD_CONST(st.resolve(0, null));
            } else if (st.singularExpression() != null) {
                // No need to resolve a template if it's just plucking a single value.
                compileExpression(st.singularExpression());
            } else {
                // String templates need to push their template placeholders in reverse order.
                st.forEachExpression(this::compileExpression);
                add_RESOLVE_TEMPLATE(st);
            }
        } else if (literal instanceof TupleLiteral t) {
            for (var e : t.members()) {
                addLiteralOpcodes(e);
            }
            add_CREATE_LIST((short) t.members().size());
        } else if (literal instanceof RecordLiteral r) {
            for (var e : r.members().entrySet()) {
                addLiteralOpcodes(e.getValue()); // value then key to make popping ordered
                add_LOAD_CONST(e.getKey().toString());
            }
            add_CREATE_MAP((short) r.members().size());
        } else if (literal instanceof BooleanLiteral b) {
            add_LOAD_CONST(b.value().getValue());
        } else if (literal instanceof IntegerLiteral i) {
            add_LOAD_CONST(i.toNode().expectNumberNode().getValue());
        } else {
            throw new UnsupportedOperationException("Unexpected rules engine Literal type: " + literal);
        }
    }

    private void compileExpression(Expression expression) {
        alwaysCompileExpression(expression);
    }

    private void alwaysCompileExpression(Expression expression) {
        expression.accept(new ExpressionVisitor<Void>() {
            @Override
            public Void visitLiteral(Literal literal) {
                addLiteralOpcodes(literal);
                return null;
            }

            @Override
            public Void visitRef(Reference reference) {
                var index = getRegister(reference.getName().toString());
                add_LOAD_REGISTER(index);
                return null;
            }

            @Override
            public Void visitGetAttr(GetAttr getAttr) {
                compileExpression(getAttr.getTarget());
                add_GET_ATTR(AttrExpression.from(getAttr));
                return null;
            }

            @Override
            public Void visitIsSet(Expression fn) {
                if (fn instanceof Reference ref) {
                    add_TEST_REGISTER_ISSET(ref.getName().toString());
                } else {
                    compileExpression(fn);
                    add_ISSET();
                }
                return null;
            }

            @Override
            public Void visitNot(Expression not) {
                compileExpression(not);
                add_NOT();
                return null;
            }

            @Override
            public Void visitBoolEquals(Expression left, Expression right) {
                if (left instanceof BooleanLiteral b) {
                    pushBooleanOptimization(b, right);
                } else if (right instanceof BooleanLiteral b) {
                    pushBooleanOptimization(b, left);
                } else {
                    compileExpression(left);
                    compileExpression(right);
                    add_FN(getFunctionIndex("booleanEquals"));
                }
                return null;
            }

            private void pushBooleanOptimization(BooleanLiteral b, Expression other) {
                var value = b.value().getValue();
                if (other instanceof Reference ref) {
                    if (value) {
                        add_TEST_REGISTER_IS_TRUE(ref.getName().toString());
                    } else {
                        add_TEST_REGISTER_IS_FALSE(ref.getName().toString());
                    }
                } else {
                    compileExpression(other);
                    add_IS_TRUE();
                    if (!value) {
                        add_NOT();
                    }
                }
            }

            @Override
            public Void visitStringEquals(Expression left, Expression right) {
                compileExpression(left);
                compileExpression(right);
                add_EQUALS();
                return null;
            }

            @Override
            public Void visitLibraryFunction(FunctionDefinition fn, List<Expression> args) {
                if (fn.getId().equals("substring")) {
                    compileExpression(args.get(0)); // string
                    add_SUBSTRING(args);
                    return null;
                }

                var index = getFunctionIndex(fn.getId());
                var f = usedFunctions.get(index);

                // Detect if the runtime function differs from the defined trait function.
                if (f.getOperandCount() != fn.getArguments().size()) {
                    throw new RulesEvaluationError("Rules engine function `" + fn.getId() + "` accepts "
                            + fn.getArguments().size() + " arguments in Smithy traits, but "
                            + f.getOperandCount() + " in the registered VM function.");
                }
                // Should never happen, but just in case.
                if (fn.getArguments().size() != args.size()) {
                    throw new RulesEvaluationError("Required arguments not given for " + fn);
                }
                for (var arg : args) {
                    compileExpression(arg);
                }
                add_FN(index);
                return null;
            }
        });
    }

    private void compileEndpointRule(EndpointRule rule) {
        // Adds to stack: headers map, auth schemes map, URL.
        var jump = compileConditions(rule);
        var e = rule.getEndpoint();

        // Add endpoint header instructions.
        if (!e.getHeaders().isEmpty()) {
            for (var entry : e.getHeaders().entrySet()) {
                // Header values. Then header name.
                for (var h : entry.getValue()) {
                    compileExpression(h);
                }
                // Process the N header values that are on the stack.
                add_CREATE_LIST((short) entry.getValue().size());
                // Now the header name.
                add_LOAD_CONST(entry.getKey());
            }
            // Combine the N headers that are on the stack in the form of String followed by List<String>.
            add_CREATE_MAP((short) e.getHeaders().size());
        }

        // Add property instructions.
        if (!e.getProperties().isEmpty()) {
            for (var entry : e.getProperties().entrySet()) {
                compileExpression(entry.getValue());
                add_LOAD_CONST(entry.getKey().toString());
            }
            add_CREATE_MAP((short) e.getProperties().size());
        }

        // Compile the URL expression (could be a reference, template, etc). This must be the closest on the stack.
        compileExpression(e.getUrl());
        // Add the set endpoint instruction.
        add_RETURN_ENDPOINT(!e.getHeaders().isEmpty(), !e.getProperties().isEmpty());
        // Patch in the actual jump target for each condition so it skips over the endpoint rule.
        jump.patchTarget(instructions, instructionSize);
    }

    private void compileErrorRule(ErrorRule rule) {
        var jump = compileConditions(rule);
        compileExpression(rule.getError()); // error message
        add_RETURN_ERROR();
        // Patch in the actual jump target for each condition so it skips over the error rule.
        jump.patchTarget(instructions, instructionSize);
    }

    RulesProgram buildProgram() {
        // Fill in the register and temporary register sizes.
        instructions[1] = (byte) (this.registry.size() - temporaryRegisters);
        instructions[2] = (byte) temporaryRegisters;
        var fns = new RulesFunction[usedFunctions.size()];
        usedFunctions.toArray(fns);
        var constPool = new Object[this.constantPool.size()];
        constantPool.keySet().toArray(constPool);
        return new RulesProgram(
                extensions,
                this.instructions,
                0,
                instructionSize,
                registry,
                fns,
                builtinProvider,
                constPool);
    }

    private static final class JumpIfFalsey {
        final List<Integer> instructionPointers = new ArrayList<>();

        void addPatch(int position) {
            instructionPointers.add(position);
        }

        void patchTarget(byte[] instructions, int instructionSize) {
            byte low = (byte) (instructionSize & 0xFF);
            byte high = (byte) ((instructionSize >> 8) & 0xFF);
            for (var position : instructionPointers) {
                instructions[position] = low;
                instructions[position + 1] = high;
            }
        }
    }

    private void add_LOAD_CONST(Object value) {
        var constant = getConstant(value);
        if (constant < 256) {
            addInstruction(RulesProgram.LOAD_CONST);
            addInstruction((byte) constant);
        } else {
            addInstruction(RulesProgram.LOAD_CONST, constant);
        }
    }

    private void add_SET_REGISTER(byte register) {
        addInstruction(RulesProgram.SET_REGISTER);
        addInstruction(register);
    }

    private void add_LOAD_REGISTER(byte register) {
        addInstruction(RulesProgram.LOAD_REGISTER);
        addInstruction(register);
    }

    private void add_JUMP_IF_FALSEY(int target) {
        addInstruction(RulesProgram.JUMP_IF_FALSEY, target);
    }

    private void add_NOT() {
        addInstruction(RulesProgram.NOT);
    }

    private void add_ISSET() {
        addInstruction(RulesProgram.ISSET);
    }

    private void add_TEST_REGISTER_ISSET(String register) {
        addInstruction(RulesProgram.TEST_REGISTER_ISSET);
        addInstruction(getRegister(register));
    }

    private void add_RETURN_ERROR() {
        addInstruction(RulesProgram.RETURN_ERROR);
    }

    private void add_RETURN_ENDPOINT(boolean hasHeaders, boolean hasProperties) {
        addInstruction(RulesProgram.RETURN_ENDPOINT);
        byte packed = 0;
        if (hasHeaders) {
            packed |= 1;
        }
        if (hasProperties) {
            packed |= 2;
        }
        addInstruction(packed);
    }

    private void add_CREATE_LIST(int length) {
        addInstruction(RulesProgram.CREATE_LIST);
        addInstruction((byte) length);
    }

    private void add_CREATE_MAP(int length) {
        addInstruction(RulesProgram.CREATE_MAP);
        addInstruction((byte) length);
    }

    private void add_RESOLVE_TEMPLATE(StringTemplate template) {
        addInstruction(RulesProgram.RESOLVE_TEMPLATE, getConstant(template));
    }

    private void add_FN(byte functionIndex) {
        addInstruction(RulesProgram.FN);
        addInstruction(functionIndex);
    }

    private void add_GET_ATTR(AttrExpression expression) {
        addInstruction(RulesProgram.GET_ATTR, getConstant(expression));
    }

    private void add_IS_TRUE() {
        addInstruction(RulesProgram.IS_TRUE);
    }

    private void add_TEST_REGISTER_IS_TRUE(String register) {
        addInstruction(RulesProgram.TEST_REGISTER_IS_TRUE);
        addInstruction(getRegister(register));
    }

    private void add_TEST_REGISTER_IS_FALSE(String register) {
        addInstruction(RulesProgram.TEST_REGISTER_IS_FALSE);
        addInstruction(getRegister(register));
    }

    private void add_EQUALS() {
        addInstruction(RulesProgram.EQUALS);
    }

    private void add_SUBSTRING(List<Expression> args) {
        addInstruction(RulesProgram.SUBSTRING);
        addInstruction(args.get(1).toNode().expectNumberNode().getValue().byteValue());
        addInstruction(args.get(2).toNode().expectNumberNode().getValue().byteValue());
        addInstruction(args.get(3).toNode().expectBooleanNode().getValue() ? (byte) 1 : (byte) 0);
    }

    private void addInstruction(byte value) {
        if (instructionSize >= instructions.length) {
            // Double the size when needed.
            byte[] newInstructions = new byte[instructions.length * 2];
            System.arraycopy(instructions, 0, newInstructions, 0, instructions.length);
            instructions = newInstructions;
        }
        instructions[instructionSize++] = value;
    }

    private void addInstruction(byte opcode, int value) {
        addInstruction(opcode);
        addInstruction((byte) 0);
        addInstruction((byte) 0);
        EndpointUtils.shortToTwoBytes(value, instructions, instructionSize - 2);
    }
}
