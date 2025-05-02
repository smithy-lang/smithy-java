/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.endpointrules;

import java.util.ArrayList;
import java.util.HashMap;
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

    private final EndpointRuleSet rules;

    // The parsed opcodes and operands.
    private final List<Object> instructions = new ArrayList<>();

    // Parameters and captured variables.
    private final List<RulesProgram.Register> registry = new ArrayList<>();

    // A map of variable name to stack index.
    private final Map<String, Integer> registryIndex = new HashMap<>();

    // An array of actually used functions.
    private final List<VmFunction> usedFunctions = new ArrayList<>();

    // Index of function name to the index in usedFunctions.
    private final Map<String, Integer> usedFunctionIndex = new HashMap<>();

    // The resolved VM functions (stdLib + given functions).
    private final Map<String, VmFunction> functions = new HashMap<>();

    // A map of function variable names to function index.
    private final Map<String, Integer> functionIndex = new HashMap<>();

    private final BiFunction<String, Context, Object> builtinProvider;

    private boolean performOptimizations;
    private final Map<Expression, Integer> cse;

    // Stack of bitfields that represent registers that were pushed to during a scope. Supports up to 64.
    private final ArrayList<Long> scopedRegisterStack = new ArrayList<>();

    RulesCompiler(
            EndpointRuleSet rules,
            List<VmFunction> functions,
            BiFunction<String, Context, Object> builtinProvider,
            boolean performOptimizations
    ) {
        this.rules = rules;
        this.builtinProvider = builtinProvider;
        this.performOptimizations = performOptimizations;

        for (var fn : Stdlib.values()) {
            this.functions.put(fn.getFunctionName(), fn);
        }

        for (var fn : functions) {
            this.functions.put(fn.getFunctionName(), fn);
        }

        // Optimize away common subexpressions. Do this up-front so we know they are the 0-N registers.
        cse = performOptimizations ? CseOptimizer.apply(rules.getRules(), 0) : Map.of();
        for (var i = 0; i < cse.size(); i++) {
            addRegister(i + "-cse", false, null, null);
        }

        // Add parameters as registry values.
        for (var param : rules.getParameters()) {
            var defaultValue = param.getDefault().map(EndpointUtils::convertValue).orElse(null);
            var builtinValue = param.getBuiltIn().orElse(null);
            addRegister(param.getName().toString(), param.isRequired(), defaultValue, builtinValue);
        }
    }

    private RulesProgram.Register addRegister(String name, boolean required, Object defaultValue, String builtin) {
        var register = new RulesProgram.Register(name, required, defaultValue, builtin);
        if (registryIndex.containsKey(name)) {
            throw new RulesEvaluationError("Duplicate variable name found in rules: " + name);
        }
        registryIndex.put(name, registry.size());
        registry.add(register);

        // Register scopes are tracking by flipping bits of a long. That means a max of 64 registers.
        // No real rules definition would have more than 64 registers.
        if (registry.size() > 64) {
            throw new RulesEvaluationError("Too many registers added to rules engine");
        }

        return register;
    }

    private int getOrCreateRegister(String name) {
        var index = registryIndex.get(name);
        if (index == null) {
            addRegister(name, false, null, null);
            return registry.size() - 1;
        }
        return index;
    }

    private int getFunctionIndex(String name) {
        var index = usedFunctionIndex.get(name);
        if (index == null) {
            var fn = functions.get(name);
            if (fn == null) {
                throw new RulesEvaluationError("Rules engine referenced unknown function: " + name);
            }
            index = usedFunctionIndex.size();
            usedFunctionIndex.put(name, index);
            usedFunctions.add(fn);
        }
        return index;
    }

    private void addInstruction(byte opcode) {
        instructions.add(opcode);
    }

    private void addInstruction(byte opcode, Object param) {
        instructions.add(opcode);
        instructions.add(param);
    }

    private void addInstruction(byte opcode, Object param1, Object param2) {
        instructions.add(opcode);
        instructions.add(param1);
        instructions.add(param2);
    }

    RulesProgram compile() {
        // Compile common subexpression values up front.
        if (performOptimizations) {
            performOptimizations = false;
            int i = 0;
            for (var e : cse.keySet()) {
                alwaysCompileExpression(e);
                addInstruction(RulesProgram.PUSH_REGISTER, i++);
            }
            performOptimizations = true;
        }

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
        scopedRegisterStack.add(0L);
    }

    private void exitScope() {
        var value = scopedRegisterStack.remove(scopedRegisterStack.size() - 1);
        // Iterate over the bits that were set and ensure their registers pop the value set of the scope.
        while (value != 0) {
            int bitIndex = Long.numberOfTrailingZeros(value);
            addInstruction(RulesProgram.POP_REGISTER, bitIndex);
            value &= value - 1;
        }
    }

    private void compileTreeRule(TreeRule tree) {
        var jump = compileConditions(tree);
        // Compile nested rules.
        for (var rule : tree.getRules()) {
            compileRule(rule);
        }
        // Patch in the actual jump target for each condition so it skips over the rules.
        jump.patchTarget(instructions);
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
            var register = getOrCreateRegister(result.toString());
            var position = scopedRegisterStack.size() - 1;
            var current = scopedRegisterStack.get(position);
            scopedRegisterStack.set(position, current | 1L << register);
            addInstruction(RulesProgram.PUSH_REGISTER, register);
        });
        // Add the jump instruction after each condition to skip over more conditions or skip over the rule.
        addInstruction(RulesProgram.JUMP_IF_FALSEY, -1);
        jump.addPatch(instructions.size() - 1);
    }

    private void addLiteralOpcodes(Literal literal) {
        if (literal instanceof StringLiteral s) {
            var st = StringTemplate.from(s.value());
            if (st.expressionCount() == 0) {
                addInstruction(RulesProgram.PUSH, st.resolve());
            } else if (st.getTemplateOnly() != null) {
                // No need to resolve a template if it's just plucking a single value.
                compileExpression(st.getTemplateOnly());
            } else {
                // String templates need to push their template placeholders in reverse order.
                for (int i = st.getParts().length - 1; i >= 0; i--) {
                    var part = st.getParts()[i];
                    if (part instanceof Expression e) {
                        compileExpression(e);
                    }
                }
                addInstruction(RulesProgram.RESOLVE_TEMPLATE, st);
            }
        } else if (literal instanceof TupleLiteral t) {
            for (var e : t.members()) {
                addLiteralOpcodes(e);
            }
            addInstruction(RulesProgram.CREATE_LIST, t.members().size());
        } else if (literal instanceof RecordLiteral r) {
            for (var e : r.members().entrySet()) {
                addInstruction(RulesProgram.PUSH, e.getKey().toString());
                addLiteralOpcodes(e.getValue());
            }
            addInstruction(RulesProgram.CREATE_MAP, r.members().size());
        } else if (literal instanceof BooleanLiteral b) {
            addInstruction(RulesProgram.PUSH, b.value().getValue());
        } else if (literal instanceof IntegerLiteral i) {
            addInstruction(RulesProgram.PUSH, i.toNode().expectNumberNode().getValue());
        } else {
            throw new UnsupportedOperationException("Unexpected rules engine Literal type: " + literal);
        }
    }

    private void compileExpression(Expression expression) {
        if (performOptimizations) {
            var register = cse.get(expression);
            if (register != null) {
                addInstruction(RulesProgram.LOAD_REGISTER, register);
                return;
            }
        }

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
                var index = getOrCreateRegister(reference.getName().toString());
                addInstruction(RulesProgram.LOAD_REGISTER, index);
                return null;
            }

            @Override
            public Void visitGetAttr(GetAttr getAttr) {
                var attr = AttrExpression.from(getAttr);
                addInstruction(RulesProgram.GET_ATTR, attr);
                return null;
            }

            @Override
            public Void visitIsSet(Expression fn) {
                compileExpression(fn);
                addInstruction(RulesProgram.ISSET);
                return null;
            }

            @Override
            public Void visitNot(Expression not) {
                compileExpression(not);
                addInstruction(RulesProgram.NOT);
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
                    addInstruction(RulesProgram.FN, getFunctionIndex("booleanEquals"));
                }
                return null;
            }

            private void pushBooleanOptimization(BooleanLiteral b, Expression other) {
                compileExpression(other);
                addInstruction(RulesProgram.IS_TRUE);
                if (!b.value().getValue()) {
                    addInstruction(RulesProgram.NOT);
                }
            }

            @Override
            public Void visitStringEquals(Expression left, Expression right) {
                compileExpression(left);
                compileExpression(right);
                addInstruction(RulesProgram.FN, getFunctionIndex("stringEquals"));
                return null;
            }

            @Override
            public Void visitLibraryFunction(FunctionDefinition fn, List<Expression> args) {
                var index = getFunctionIndex(fn.getId());
                var f = usedFunctions.get(index);
                // Detect if the runtime function differs from the defined trait function.
                if (f.getOperandCount() != fn.getArguments().size()) {
                    throw new RulesEvaluationError("Rules engine function " + fn.getId() + " accepts "
                            + fn.getArguments().size() + " arguments in traits, but "
                            + f.getOperandCount() + " in the registered VM function.");
                }
                // Should never happen, but just in case.
                if (fn.getArguments().size() != args.size()) {
                    throw new RulesEvaluationError("Required arguments not given for " + fn);
                }
                for (var arg : args) {
                    compileExpression(arg);
                }
                addInstruction(RulesProgram.FN, index);
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
                // Push the instructions for creating the headers.
                for (var h : entry.getValue()) {
                    compileExpression(h);
                }
                // Process the N header values that are on the stack.
                addInstruction(RulesProgram.CREATE_LIST, entry.getValue().size());
                // Push the header name.
                addInstruction(RulesProgram.PUSH, entry.getKey());
            }
            // Combine the N headers that are on the stack in the form of String followed by List<String>.
            addInstruction(RulesProgram.CREATE_MAP, e.getHeaders().size());
        }

        // Add property instructions.
        if (!e.getProperties().isEmpty()) {
            for (var entry : e.getProperties().entrySet()) {
                addInstruction(RulesProgram.PUSH, entry.getKey().toString());
                compileExpression(entry.getValue());
            }
            addInstruction(RulesProgram.CREATE_MAP, e.getProperties().size());
        }

        // Compile the URL expression (could be a reference, template, etc). This must be the closest on the stack.
        compileExpression(e.getUrl());

        // Add the set endpoint instruction.
        addInstruction(RulesProgram.SET_ENDPOINT, !e.getHeaders().isEmpty(), !e.getProperties().isEmpty());
        // Patch in the actual jump target for each condition so it skips over the endpoint rule.
        jump.patchTarget(instructions);
    }

    private void compileErrorRule(ErrorRule rule) {
        var jump = compileConditions(rule);
        compileExpression(rule.getError()); // error message
        addInstruction(RulesProgram.SET_ERROR);
        // Patch in the actual jump target for each condition so it skips over the error rule.
        jump.patchTarget(instructions);
    }

    RulesProgram buildProgram() {
        var instructions = new Object[this.instructions.size()];
        this.instructions.toArray(instructions);
        var registry = new RulesProgram.Register[this.registry.size()];
        this.registry.toArray(registry);
        var fns = new VmFunction[usedFunctions.size()];
        usedFunctions.toArray(fns);
        return new RulesProgram(
                instructions,
                registry,
                registryIndex,
                fns,
                functionIndex,
                builtinProvider);
    }

    private static final class JumpIfFalsey {
        final List<Integer> instructionPointers = new ArrayList<>();

        void addPatch(int position) {
            instructionPointers.add(position);
        }

        void patchTarget(List<Object> instructions) {
            for (var position : instructionPointers) {
                instructions.set(position, instructions.size());
            }
        }
    }
}
