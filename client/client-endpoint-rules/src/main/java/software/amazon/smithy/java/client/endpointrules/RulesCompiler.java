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
    private final List<Instruction> instructions = new ArrayList<>();

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

    RulesProgram.Register addRegister(String name, boolean required, Object defaultValue, String builtin) {
        var register = new RulesProgram.Register(name, required, defaultValue, builtin);
        if (registryIndex.containsKey(name)) {
            throw new RulesEvaluationError("Duplicate variable name found in rules: " + name);
        }
        registryIndex.put(name, registry.size());
        registry.add(register);
        return register;
    }

    int getOrCreateRegister(String name) {
        var index = registryIndex.get(name);
        if (index == null) {
            addRegister(name, false, null, null);
            return registry.size() - 1;
        }
        return index;
    }

    int getFunctionIndex(String name) {
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

    RulesProgram compile() {
        // Compile common subexpression values up front.
        if (performOptimizations) {
            performOptimizations = false;
            int i = 0;
            for (var e : cse.keySet()) {
                alwaysCompileExpression(e);
                instructions.add(new Instruction.PushRegister(i++));
            }
            performOptimizations = true;
        }

        for (var rule : rules.getRules()) {
            compileRule(rule);
        }

        return buildProgram();
    }

    private void compileRule(Rule rule) {
        if (rule instanceof TreeRule t) {
            compileTreeRule(t);
        } else if (rule instanceof EndpointRule e) {
            compileEndpointRule(e);
        } else if (rule instanceof ErrorRule e) {
            compileErrorRule(e);
        }
    }

    private void compileTreeRule(TreeRule tree) {
        var jump = compileConditions(tree);
        // Compile nested rules.
        for (var rule : tree.getRules()) {
            compileRule(rule);
        }
        // Patch in the actual jump target for each condition so it skips over the rules.
        jump.patchTarget(instructions.size());
    }

    private Instruction.JumpIfFalsey compileConditions(Rule rule) {
        var jump = new Instruction.JumpIfFalsey(-1);
        for (var condition : rule.getConditions()) {
            compileCondition(condition, jump);
        }
        return jump;
    }

    private void compileCondition(Condition condition, Instruction.JumpIfFalsey jump) {
        compileExpression(condition.getFunction());
        // Add an instruction to store the result as a register if the condition requests it.
        condition.getResult().ifPresent(result -> {
            instructions.add(new Instruction.PushRegister(getOrCreateRegister(result.toString())));
        });
        // Add the jump instruction after each condition to skip over more conditions or skip over the rule.
        instructions.add(jump);
    }

    private void addLiteralOpcodes(Literal literal) {
        if (literal instanceof StringLiteral s) {
            var st = StringTemplate.from(s.value());
            if (st.expressionCount() == 0) {
                instructions.add(new Instruction.Push(st.resolve()));
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
                instructions.add(new Instruction.ResolveTemplate(st));
            }
        } else if (literal instanceof TupleLiteral t) {
            for (var e : t.members()) {
                addLiteralOpcodes(e);
            }
            instructions.add(new Instruction.CreateList(t.members().size()));
        } else if (literal instanceof RecordLiteral r) {
            for (var e : r.members().entrySet()) {
                instructions.add(new Instruction.Push(e.getKey().toString()));
                addLiteralOpcodes(e.getValue());
            }
            instructions.add(new Instruction.CreateMap(r.members().size()));
        } else if (literal instanceof BooleanLiteral b) {
            instructions.add(new Instruction.Push(b.value().getValue()));
        } else if (literal instanceof IntegerLiteral i) {
            instructions.add(new Instruction.Push(i.toNode().expectNumberNode().getValue()));
        } else {
            throw new UnsupportedOperationException("Unexpected rules engine Literal type: " + literal);
        }
    }

    private void compileExpression(Expression expression) {
        if (performOptimizations) {
            var register = cse.get(expression);
            if (register != null) {
                instructions.add(new Instruction.LoadRegister(register));
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
                instructions.add(new Instruction.LoadRegister(index));
                return null;
            }

            @Override
            public Void visitGetAttr(GetAttr getAttr) {
                var attr = AttrExpression.from(getAttr);
                instructions.add(new Instruction.GetAttr(attr));
                return null;
            }

            @Override
            public Void visitIsSet(Expression fn) {
                compileExpression(fn);
                instructions.add(new Instruction.Isset());
                return null;
            }

            @Override
            public Void visitNot(Expression not) {
                compileExpression(not);
                instructions.add(new Instruction.Not());
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
                    instructions.add(new Instruction.Fn(getFunctionIndex("booleanEquals")));
                }
                return null;
            }

            private void pushBooleanOptimization(BooleanLiteral b, Expression other) {
                compileExpression(other);
                instructions.add(new Instruction.IsTrue());
                if (!b.value().getValue()) {
                    instructions.add(new Instruction.Not());
                }
            }

            @Override
            public Void visitStringEquals(Expression left, Expression right) {
                compileExpression(left);
                compileExpression(right);
                instructions.add(new Instruction.Fn(getFunctionIndex("stringEquals")));
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
                instructions.add(new Instruction.Fn(index));
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
                instructions.add(new Instruction.CreateList(entry.getValue().size()));
                // Push the header name.
                instructions.add(new Instruction.Push(entry.getKey()));
            }
            // Combine the N headers that are on the stack in the form of String followed by List<String>.
            instructions.add(new Instruction.CreateMap(e.getHeaders().size()));
        }

        // Add property instructions.
        if (!e.getProperties().isEmpty()) {
            for (var entry : e.getProperties().entrySet()) {
                instructions.add(new Instruction.Push(entry.getKey().toString()));
                compileExpression(entry.getValue());
            }
            instructions.add(new Instruction.CreateMap(e.getProperties().size()));
        }

        // Compile the URL expression (could be a reference, template, etc). This must be the closest on the stack.
        compileExpression(e.getUrl());

        // Add the set endpoint instruction.
        instructions.add(new Instruction.SetEndpoint(!e.getHeaders().isEmpty(), !e.getProperties().isEmpty()));
        // Patch in the actual jump target for each condition so it skips over the endpoint rule.
        jump.patchTarget(instructions.size());
    }

    private void compileErrorRule(ErrorRule rule) {
        var jump = compileConditions(rule);
        compileExpression(rule.getError()); // error message
        instructions.add(new Instruction.SetError());
        // Patch in the actual jump target for each condition so it skips over the error rule.
        jump.patchTarget(instructions.size());
    }

    RulesProgram buildProgram() {
        var instructions = new Instruction[this.instructions.size()];
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
}
