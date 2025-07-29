/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.rulesengine.language.evaluation.value.Value;
import software.amazon.smithy.rulesengine.language.syntax.expressions.Expression;
import software.amazon.smithy.rulesengine.language.syntax.expressions.ExpressionVisitor;
import software.amazon.smithy.rulesengine.language.syntax.expressions.Reference;
import software.amazon.smithy.rulesengine.language.syntax.expressions.Template;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.FunctionDefinition;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.GetAttr;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.IsSet;
import software.amazon.smithy.rulesengine.language.syntax.expressions.literal.BooleanLiteral;
import software.amazon.smithy.rulesengine.language.syntax.expressions.literal.IntegerLiteral;
import software.amazon.smithy.rulesengine.language.syntax.expressions.literal.Literal;
import software.amazon.smithy.rulesengine.language.syntax.expressions.literal.RecordLiteral;
import software.amazon.smithy.rulesengine.language.syntax.expressions.literal.StringLiteral;
import software.amazon.smithy.rulesengine.language.syntax.expressions.literal.TupleLiteral;
import software.amazon.smithy.rulesengine.language.syntax.rule.Condition;
import software.amazon.smithy.rulesengine.language.syntax.rule.EndpointRule;
import software.amazon.smithy.rulesengine.language.syntax.rule.ErrorRule;
import software.amazon.smithy.rulesengine.language.syntax.rule.NoMatchRule;
import software.amazon.smithy.rulesengine.language.syntax.rule.Rule;
import software.amazon.smithy.rulesengine.logic.bdd.Bdd;
import software.amazon.smithy.rulesengine.logic.bdd.BddNodeConsumer;
import software.amazon.smithy.rulesengine.logic.bdd.BddTrait;

final class BytecodeCompiler {

    private final List<RulesExtension> extensions;
    private final BddTrait bdd;
    private final Map<String, Function<Context, Object>> builtinProviders;
    private final BytecodeWriter writer = new BytecodeWriter();
    private final List<RulesFunction> usedFunctions = new ArrayList<>();
    private final Map<String, Byte> usedFunctionIndex = new HashMap<>();
    private final Map<String, RulesFunction> availableFunctions;
    private final RegisterAllocator registerAllocator;

    BytecodeCompiler(
            List<RulesExtension> extensions,
            BddTrait bdd,
            Map<String, RulesFunction> functions,
            Map<String, Function<Context, Object>> builtinProviders
    ) {
        this.extensions = extensions;
        this.builtinProviders = builtinProviders;
        this.availableFunctions = functions;
        this.bdd = bdd;
        this.registerAllocator = new RegisterAllocator();

        // Add parameters as registry values
        for (var param : bdd.getParameters()) {
            var defaultValue = param.getDefault().map(Value::toObject).orElse(null);
            var builtin = param.getBuiltIn().orElse(null);
            registerAllocator.allocate(param.getName().toString(), param.isRequired(), defaultValue, builtin, false);
        }
    }

    private byte getFunctionIndex(String name) {
        Byte index = usedFunctionIndex.get(name);
        if (index == null) {
            var fn = availableFunctions.get(name);
            if (fn == null) {
                throw new RulesEvaluationError("Rules engine referenced unknown function: " + name);
            }
            index = (byte) usedFunctionIndex.size();
            usedFunctionIndex.put(name, index);
            usedFunctions.add(fn);
            writer.registerFunction(name);
        }
        return index;
    }

    Bytecode compile() {
        // Compile all conditions
        for (int i = 0; i < bdd.getConditions().size(); i++) {
            writer.markConditionStart();
            compileCondition(bdd.getConditions().get(i));
        }

        // Compile all results
        for (Rule result : bdd.getResults()) {
            writer.markResultStart();
            if (result == null || result instanceof NoMatchRule) {
                // No match: push null and return
                writer.writeByte(Opcodes.LOAD_CONST);
                writer.writeByte(writer.getConstantIndex(null));
                writer.writeByte(Opcodes.RETURN_VALUE);
            } else if (result instanceof EndpointRule e) {
                compileEndpointRule(e);
            } else if (result instanceof ErrorRule e) {
                compileErrorRule(e);
            } else {
                throw new UnsupportedOperationException("Unexpected result type: " + result.getClass());
            }
        }

        return buildProgram();
    }

    private void compileCondition(Condition condition) {
        compileExpression(condition.getFunction());
        condition.getResult().ifPresent(result -> {
            byte register = registerAllocator.getOrAllocateRegister(result.toString());
            writer.writeByte(Opcodes.SET_REGISTER);
            writer.writeByte(register);
        });
        writer.writeByte(Opcodes.RETURN_VALUE);
    }

    private void compileEndpointRule(EndpointRule rule) {
        var e = rule.getEndpoint();

        // Add endpoint header instructions
        if (!e.getHeaders().isEmpty()) {
            for (var entry : e.getHeaders().entrySet()) {
                // Header values. Then header name.
                for (var h : entry.getValue()) {
                    compileExpression(h);
                }
                // Process the N header values that are on the stack
                compileListCreation(entry.getValue().size());
                // Now the header name
                writer.writeByte(Opcodes.LOAD_CONST);
                writer.writeByte(writer.getConstantIndex(entry.getKey()));
            }
            // Combine the N headers that are on the stack
            compileMapCreation(e.getHeaders().size());
        }

        // Add property instructions
        if (!e.getProperties().isEmpty()) {
            for (var entry : e.getProperties().entrySet()) {
                compileExpression(entry.getValue());
                writer.writeByte(Opcodes.LOAD_CONST);
                writer.writeByte(writer.getConstantIndex(entry.getKey().toString()));
            }
            compileMapCreation(e.getProperties().size());
        }

        // Compile the URL expression
        compileExpression(e.getUrl());

        // Add the return endpoint instruction
        writer.writeByte(Opcodes.RETURN_ENDPOINT);
        byte packed = 0;
        if (!e.getHeaders().isEmpty()) {
            packed |= 1;
        }
        if (!e.getProperties().isEmpty()) {
            packed |= 2;
        }
        writer.writeByte(packed);
    }

    private void compileErrorRule(ErrorRule rule) {
        compileExpression(rule.getError());
        writer.writeByte(Opcodes.RETURN_ERROR);
    }

    private void compileExpression(Expression expression) {
        expression.accept(new ExpressionVisitor<Void>() {
            @Override
            public Void visitLiteral(Literal literal) {
                compileLiteral(literal);
                return null;
            }

            @Override
            public Void visitRef(Reference reference) {
                var index = registerAllocator.getRegister(reference.getName().toString());
                writer.writeByte(Opcodes.LOAD_REGISTER);
                writer.writeByte(index);
                return null;
            }

            @Override
            public Void visitGetAttr(GetAttr getAttr) {
                compileGetAttr(getAttr);
                return null;
            }

            @Override
            public Void visitIsSet(Expression fn) {
                if (fn instanceof Reference ref) {
                    writer.writeByte(Opcodes.TEST_REGISTER_ISSET);
                    writer.writeByte(registerAllocator.getRegister(ref.getName().toString()));
                } else {
                    compileExpression(fn);
                    writer.writeByte(Opcodes.ISSET);
                }
                return null;
            }

            @Override
            public Void visitNot(Expression not) {
                if (not instanceof IsSet isset && isset.getArguments().get(0) instanceof Reference ref) {
                    writer.writeByte(Opcodes.TEST_REGISTER_NOT_SET);
                    writer.writeByte(registerAllocator.getRegister(ref.getName().toString()));
                } else {
                    compileExpression(not);
                    writer.writeByte(Opcodes.NOT);
                }
                return null;
            }

            @Override
            public Void visitBoolEquals(Expression left, Expression right) {
                if (left instanceof BooleanLiteral b) {
                    compileBooleanOptimization(b, right);
                } else if (right instanceof BooleanLiteral b) {
                    compileBooleanOptimization(b, left);
                } else {
                    compileExpression(left);
                    compileExpression(right);
                    writer.writeByte(Opcodes.BOOLEAN_EQUALS);
                }
                return null;
            }

            private void compileBooleanOptimization(BooleanLiteral b, Expression other) {
                var value = b.value().getValue();
                if (other instanceof Reference ref) {
                    if (value) {
                        writer.writeByte(Opcodes.TEST_REGISTER_IS_TRUE);
                    } else {
                        writer.writeByte(Opcodes.TEST_REGISTER_IS_FALSE);
                    }
                    writer.writeByte(registerAllocator.getRegister(ref.getName().toString()));
                } else {
                    compileExpression(other);
                    writer.writeByte(Opcodes.IS_TRUE);
                    if (!value) {
                        writer.writeByte(Opcodes.NOT);
                    }
                }
            }

            @Override
            public Void visitStringEquals(Expression left, Expression right) {
                compileExpression(left);
                compileExpression(right);
                writer.writeByte(Opcodes.STRING_EQUALS);
                return null;
            }

            @Override
            public Void visitLibraryFunction(FunctionDefinition fn, List<Expression> args) {
                String fnId = fn.getId();

                // Handle special built-in functions
                switch (fnId) {
                    case "substring" -> {
                        compileExpression(args.get(0)); // string
                        writer.writeByte(Opcodes.SUBSTRING);
                        writer.writeByte(args.get(1).toNode().expectNumberNode().getValue().byteValue());
                        writer.writeByte(args.get(2).toNode().expectNumberNode().getValue().byteValue());
                        writer.writeByte(args.get(3).toNode().expectBooleanNode().getValue() ? (byte) 1 : (byte) 0);
                        return null;
                    }
                    case "isValidHostLabel" -> {
                        compileExpression(args.get(0)); // string
                        compileExpression(args.get(1)); // allowDots
                        writer.writeByte(Opcodes.IS_VALID_HOST_LABEL);
                        return null;
                    }
                    case "parseURL" -> {
                        compileExpression(args.get(0)); // urlString
                        writer.writeByte(Opcodes.PARSE_URL);
                        return null;
                    }
                    case "uriEncode" -> {
                        compileExpression(args.get(0)); // string
                        writer.writeByte(Opcodes.URI_ENCODE);
                        return null;
                    }
                }

                // Regular function call
                var index = getFunctionIndex(fnId);

                // Compile arguments
                for (var arg : args) {
                    compileExpression(arg);
                }

                // Use the appropriate function opcode based on argument count
                switch (args.size()) {
                    case 0 -> {
                        writer.writeByte(Opcodes.FN0);
                        writer.writeByte(index);
                    }
                    case 1 -> {
                        writer.writeByte(Opcodes.FN1);
                        writer.writeByte(index);
                    }
                    case 2 -> {
                        writer.writeByte(Opcodes.FN2);
                        writer.writeByte(index);
                    }
                    case 3 -> {
                        writer.writeByte(Opcodes.FN3);
                        writer.writeByte(index);
                    }
                    default -> {
                        writer.writeByte(Opcodes.FN);
                        writer.writeByte(index);
                    }
                }
                return null;
            }
        });
    }

    private void compileGetAttr(GetAttr getAttr) {
        var path = getAttr.getPath();
        if (path.isEmpty()) {
            throw new UnsupportedOperationException("Invalid getAttr expression: requires at least one part");
        }

        // Check if we can optimize to register-based access
        if (getAttr.getTarget() instanceof Reference ref && path.size() == 1) {
            var part = path.get(0);
            byte regIndex = registerAllocator.getRegister(ref.getName().toString());

            if (part instanceof GetAttr.Part.Key key) {
                writer.writeByte(Opcodes.GET_PROPERTY_REG);
                writer.writeByte(regIndex);
                int propIndex = writer.getConstantIndex(key.key().toString());
                writer.writeShort(propIndex);
            } else if (part instanceof GetAttr.Part.Index idx) {
                writer.writeByte(Opcodes.GET_INDEX_REG);
                writer.writeByte(regIndex);
                writer.writeByte(idx.index());
            }
            return;
        }

        // Compile the target
        compileExpression(getAttr.getTarget());

        // Apply each part of the path
        for (var part : path) {
            if (part instanceof GetAttr.Part.Key key) {
                int propIndex = writer.getConstantIndex(key.key().toString());
                writer.writeByte(Opcodes.GET_PROPERTY);
                writer.writeShort(propIndex);
            } else if (part instanceof GetAttr.Part.Index idx) {
                writer.writeByte(Opcodes.GET_INDEX);
                writer.writeByte(idx.index());
            }
        }
    }

    private void compileLiteral(Literal literal) {
        if (literal instanceof StringLiteral s) {
            var template = s.value();
            var parts = template.getParts();

            if (parts.size() == 1 && parts.get(0) instanceof Template.Literal) {
                // Simple string with no interpolation
                addLoadConst(parts.get(0).toString());
            } else if (parts.size() == 1 && parts.get(0) instanceof Template.Dynamic dynamic) {
                // Single dynamic expression, so just evaluate it
                compileExpression(dynamic.toExpression());
            } else {
                // Multiple parts - need to concatenate
                int expressionCount = 0;
                for (var part : parts) {
                    if (part instanceof Template.Dynamic d) {
                        compileExpression(d.toExpression());
                    } else {
                        addLoadConst(part.toString());
                    }
                    expressionCount++;
                }
                writer.writeByte(Opcodes.RESOLVE_TEMPLATE);
                writer.writeByte(expressionCount);
            }
        } else if (literal instanceof TupleLiteral t) {
            for (var e : t.members()) {
                compileLiteral(e);
            }
            compileListCreation(t.members().size());
        } else if (literal instanceof RecordLiteral r) {
            for (var e : r.members().entrySet()) {
                compileLiteral(e.getValue()); // value then key to make popping ordered
                addLoadConst(e.getKey().toString());
            }
            compileMapCreation(r.members().size());
        } else if (literal instanceof BooleanLiteral b) {
            addLoadConst(b.value().getValue());
        } else if (literal instanceof IntegerLiteral i) {
            addLoadConst(i.toNode().expectNumberNode().getValue());
        } else {
            throw new UnsupportedOperationException("Unexpected rules engine Literal type: " + literal);
        }
    }

    private void compileListCreation(int size) {
        switch (size) {
            case 0 -> writer.writeByte(Opcodes.LIST0);
            case 1 -> writer.writeByte(Opcodes.LIST1);
            case 2 -> writer.writeByte(Opcodes.LIST2);
            default -> {
                writer.writeByte(Opcodes.LISTN);
                writer.writeByte(size);
            }
        }
    }

    private void compileMapCreation(int size) {
        switch (size) {
            case 0 -> writer.writeByte(Opcodes.MAP0);
            case 1 -> writer.writeByte(Opcodes.MAP1);
            case 2 -> writer.writeByte(Opcodes.MAP2);
            case 3 -> writer.writeByte(Opcodes.MAP3);
            case 4 -> writer.writeByte(Opcodes.MAP4);
            default -> {
                writer.writeByte(Opcodes.MAPN);
                writer.writeByte(size);
            }
        }
    }

    private void addLoadConst(Object value) {
        int constant = writer.getConstantIndex(value);
        if (constant < 256) {
            writer.writeByte(Opcodes.LOAD_CONST);
            writer.writeByte(constant);
        } else {
            writer.writeByte(Opcodes.LOAD_CONST_W);
            writer.writeShort(constant);
        }
    }

    private Bytecode buildProgram() {
        var registerDefs = registerAllocator.getRegistry().toArray(new RegisterDefinition[0]);
        var fns = usedFunctions.toArray(new RulesFunction[0]);

        Bdd bddData = bdd.getBdd();
        int nodeCount = bddData.getNodeCount();
        int[] bddNodes = new int[nodeCount * 3];
        bddData.getNodes(new BddNodeConsumer() {
            private int index = 0;
            @Override
            public void accept(int var, int high, int low) {
                bddNodes[index++] = var;
                bddNodes[index++] = high;
                bddNodes[index++] = low;
            }
        });

        return writer.build(registerDefs, fns, bddNodes, bddData.getRootRef());
    }
}
