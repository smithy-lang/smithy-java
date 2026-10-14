/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Compiles the stack bytecode of a single condition or result into a {@link CompiledNode} tree.
 *
 * <p>Conditions and results are compiled expressions: the instruction stream for one of them is a
 * post-order walk of an expression tree, where the only control flow is the structured {@code coalesce}
 * ({@code JNN_OR_POP}) and {@code ite} ({@code JMP_IF_FALSE} plus {@code JUMP}) idioms the rules compiler
 * emits. That lets the tree be recovered by simulating the operand stack symbolically, pushing nodes
 * instead of values, and recursing into the two structured branch forms.
 *
 * <p>Every opcode {@link BytecodeWriter} can emit is supported. Since there is no interpreter to fall
 * back to, a stream this cannot decode is malformed or came from a newer writer, and
 * {@link #compile} reports that as a {@link RulesEvaluationError} rather than degrading silently.
 */
final class NodeCompiler {

    private final byte[] code;
    private final Object[] constantPool;
    private final RulesFunction[] functions;

    /**
     * Target of the {@code JUMP} that ended the most recent {@link #decode} call, or -1 if that call
     * ended some other way. The {@code JMP_IF_FALSE} handler reads it straight after recursing into the
     * true arm, to learn where the two arms rejoin.
     *
     * <p>This cannot be a local. The value has to cross a recursive {@code decode} call, from the callee
     * that consumed the {@code JUMP} back to the caller that needs its target; as a local, every caller
     * reads its own -1 and no {@code ite} ever compiles. Decoding is strictly nested and
     * {@link Bytecode} builds a fresh compiler per call, so a field is safe.
     */
    private int pendingJumpTarget = -1;

    NodeCompiler(byte[] code, Object[] constantPool, RulesFunction[] functions) {
        this.code = code;
        this.constantPool = constantPool;
        this.functions = functions;
    }

    /**
     * Compiles the condition or result starting at the given offset.
     *
     * <p>Every opcode the writer can emit is supported, so failing here means the bytecode is malformed
     * or was produced by a newer writer, which is reported as a {@link RulesEvaluationError}. Compilation
     * is lazy, so the error surfaces on the first resolution that reaches the offending condition or
     * result.
     *
     * @param start offset of the first instruction.
     * @return the compiled node, never null.
     * @throws RulesEvaluationError if the instruction stream cannot be decoded.
     */
    CompiledNode compile(int start) {
        try {
            CompiledNode node = decode(start, code.length);
            if (node == null) {
                throw new IllegalStateException("program did not reduce to a single value");
            }
            return node;
        } catch (RuntimeException e) {
            throw new RulesEvaluationError("Cannot compile rules program: " + e.getMessage(), start);
        }
    }

    /**
     * Decodes {@code [start, limit)} with a fresh symbolic stack, returning the single value it leaves
     * behind. Stops early at a terminator instruction or at a {@code JUMP}.
     */
    private CompiledNode decode(int start, int limit) {
        List<CompiledNode> stack = new ArrayList<>(8);
        int pc = start;
        pendingJumpTarget = -1;

        while (pc < limit) {
            int opcode = code[pc++] & 0xFF;
            switch (opcode) {
                case Opcodes.LOAD_CONST -> stack.add(new CompiledNode.Const(constantPool[code[pc++] & 0xFF]));
                case Opcodes.LOAD_CONST_W -> {
                    stack.add(new CompiledNode.Const(constantPool[u16(pc)]));
                    pc += 2;
                }
                // SET_REGISTER leaves the value on the stack, which is exactly what SetReg yields.
                case Opcodes.SET_REGISTER -> {
                    int reg = code[pc++] & 0xFF;
                    stack.add(new CompiledNode.SetReg(reg, pop(stack)));
                }
                case Opcodes.LOAD_REGISTER -> stack.add(new CompiledNode.Reg(code[pc++] & 0xFF));
                case Opcodes.NOT -> stack.add(new CompiledNode.Not(pop(stack)));
                case Opcodes.ISSET -> stack.add(new CompiledNode.IsSet(pop(stack)));
                case Opcodes.IS_TRUE -> stack.add(new CompiledNode.IsTrue(pop(stack)));
                case Opcodes.TEST_REGISTER_ISSET -> stack.add(new CompiledNode.RegIsSet(code[pc++] & 0xFF));
                case Opcodes.TEST_REGISTER_NOT_SET -> stack.add(new CompiledNode.RegNotSet(code[pc++] & 0xFF));
                case Opcodes.TEST_REGISTER_IS_TRUE -> stack.add(new CompiledNode.RegIsTrue(code[pc++] & 0xFF));
                case Opcodes.TEST_REGISTER_IS_FALSE -> stack.add(new CompiledNode.RegIsFalse(code[pc++] & 0xFF));

                case Opcodes.LIST0 -> stack.add(new CompiledNode.Const(Collections.emptyList()));
                case Opcodes.LIST1 -> stack.add(list(stack, 1));
                case Opcodes.LIST2 -> stack.add(list(stack, 2));
                case Opcodes.LISTN -> stack.add(list(stack, code[pc++] & 0xFF));

                case Opcodes.MAP0 -> stack.add(new CompiledNode.Const(Map.of()));
                case Opcodes.MAP1 -> stack.add(map(stack, 1));
                case Opcodes.MAP2 -> stack.add(map(stack, 2));
                // MAP3 is decoded with the same value-then-key layout as every other arity. The
                // retired interpreter read its slots in a different order, which built a map of values
                // keyed by values and threw ClassCastException unless they happened to be strings; no
                // working program can have depended on that, so decoding it consistently is a fix.
                case Opcodes.MAP3 -> stack.add(map(stack, 3));
                case Opcodes.MAP4 -> stack.add(map(stack, 4));
                case Opcodes.MAPN -> stack.add(map(stack, code[pc++] & 0xFF));
                case Opcodes.STRUCTN -> stack.add(struct(stack, code[pc++] & 0xFF));

                case Opcodes.RESOLVE_TEMPLATE -> {
                    int count = code[pc++] & 0xFF;
                    stack.add(CompiledNode.template(drain(stack, count)));
                }

                case Opcodes.FN0 -> stack.add(new CompiledNode.Fn0(fn(code[pc++] & 0xFF)));
                case Opcodes.FN1 -> {
                    var fn = fn(code[pc++] & 0xFF);
                    stack.add(new CompiledNode.Fn1(fn, pop(stack)));
                }
                case Opcodes.FN2 -> {
                    var fn = fn(code[pc++] & 0xFF);
                    var args = drain(stack, 2);
                    stack.add(new CompiledNode.Fn2(fn, args.get(0), args.get(1)));
                }
                case Opcodes.FN3 -> {
                    var fn = fn(code[pc++] & 0xFF);
                    var args = drain(stack, 3);
                    stack.add(new CompiledNode.Fn3(fn, args.get(0), args.get(1), args.get(2)));
                }
                case Opcodes.FN -> {
                    var fn = fn(code[pc++] & 0xFF);
                    var args = drain(stack, fn.getArgumentCount());
                    stack.add(new CompiledNode.FnN(fn, args.toArray(new CompiledNode[0])));
                }

                case Opcodes.GET_PROPERTY -> {
                    var name = string(u16(pc));
                    pc += 2;
                    stack.add(CompiledNode.property(pop(stack), name));
                }
                case Opcodes.GET_PROPERTY_REG -> {
                    int reg = code[pc++] & 0xFF;
                    var name = string(u16(pc));
                    pc += 2;
                    stack.add(new CompiledNode.PropReg(reg, name));
                }
                case Opcodes.GET_INDEX -> stack.add(new CompiledNode.Index(pop(stack), code[pc++] & 0xFF));
                case Opcodes.GET_INDEX_REG -> {
                    int reg = code[pc++] & 0xFF;
                    stack.add(new CompiledNode.Index(new CompiledNode.Reg(reg), code[pc++] & 0xFF));
                }
                case Opcodes.GET_NEGATIVE_INDEX ->
                    stack.add(new CompiledNode.NegIndex(pop(stack), code[pc++] & 0xFF));
                case Opcodes.GET_NEGATIVE_INDEX_REG -> {
                    int reg = code[pc++] & 0xFF;
                    stack.add(new CompiledNode.NegIndex(new CompiledNode.Reg(reg), code[pc++] & 0xFF));
                }

                case Opcodes.EQUALS -> {
                    var args = drain(stack, 2);
                    stack.add(new CompiledNode.EqualsNode(args.get(0), args.get(1)));
                }
                case Opcodes.BOOLEAN_EQUALS -> {
                    var args = drain(stack, 2);
                    stack.add(new CompiledNode.BooleanEq(args.get(0), args.get(1)));
                }
                case Opcodes.STRING_EQUALS -> {
                    var args = drain(stack, 2);
                    stack.add(CompiledNode.stringEquals(args.get(0), args.get(1)));
                }
                case Opcodes.STRING_EQUALS_REG_CONST -> {
                    int reg = code[pc++] & 0xFF;
                    var expected = string(u16(pc));
                    pc += 2;
                    stack.add(new CompiledNode.StringEqRegConst(reg, expected));
                }
                case Opcodes.SUBSTRING -> {
                    int start2 = code[pc++] & 0xFF;
                    int end = code[pc++] & 0xFF;
                    boolean reverse = (code[pc++] & 0xFF) != 0;
                    stack.add(new CompiledNode.Substring(pop(stack), start2, end, reverse));
                }
                case Opcodes.SUBSTRING_EQ -> {
                    int reg = code[pc++] & 0xFF;
                    int start2 = code[pc++] & 0xFF;
                    int end = code[pc++] & 0xFF;
                    boolean reverse = (code[pc++] & 0x01) != 0;
                    var expected = string(u16(pc));
                    pc += 2;
                    stack.add(new CompiledNode.SubstringEqReg(reg, start2, end, reverse, expected));
                }
                case Opcodes.IS_VALID_HOST_LABEL -> {
                    var args = drain(stack, 2);
                    stack.add(CompiledNode.isValidHostLabel(args.get(0), args.get(1)));
                }
                case Opcodes.PARSE_URL -> stack.add(new CompiledNode.ParseUrlNode(pop(stack)));
                case Opcodes.URI_ENCODE -> stack.add(new CompiledNode.UriEncode(pop(stack)));
                case Opcodes.SPLIT -> {
                    var args = drain(stack, 3);
                    stack.add(new CompiledNode.SplitNode(args.get(0), args.get(1), args.get(2)));
                }
                case Opcodes.SPLIT_GET -> {
                    int reg = code[pc++] & 0xFF;
                    var delimiter = string(u16(pc));
                    pc += 2;
                    int element = code[pc++]; // signed
                    stack.add(new CompiledNode.SplitGetReg(reg, delimiter, element));
                }
                case Opcodes.SELECT_BOOL_REG -> {
                    int reg = code[pc++] & 0xFF;
                    Object whenTrue = constantPool[u16(pc)];
                    pc += 2;
                    Object whenFalse = constantPool[u16(pc)];
                    pc += 2;
                    stack.add(new CompiledNode.SelectBoolReg(reg, whenTrue, whenFalse));
                }
                case Opcodes.BUILD_URI -> {
                    var scheme = string(u16(pc));
                    pc += 2;
                    var args = drain(stack, 2);
                    stack.add(CompiledNode.buildUri(scheme, args.get(0), args.get(1)));
                }

                // coalesce(a, b, ...): the arms after this one are the self-contained range up to the
                // shared join label, so decoding that range yields the fallback expression.
                case Opcodes.JNN_OR_POP -> {
                    int target = pc + 2 + u16(pc);
                    if (target > limit) {
                        return null;
                    }
                    var preferred = pop(stack);
                    var fallback = decode(pc + 2, target);
                    if (fallback == null) {
                        return null;
                    }
                    stack.add(new CompiledNode.Coalesce(new CompiledNode[] {preferred, fallback}));
                    pc = target;
                }

                // ite(c, t, f): the true arm runs to an unconditional JUMP over the false arm, and that
                // jump's target marks where both arms rejoin.
                case Opcodes.JMP_IF_FALSE -> {
                    int falseTarget = pc + 2 + u16(pc);
                    if (falseTarget > limit) {
                        return null;
                    }
                    var condition = pop(stack);
                    var whenTrue = decode(pc + 2, falseTarget);
                    int endTarget = pendingJumpTarget;
                    if (whenTrue == null || endTarget < falseTarget || endTarget > limit) {
                        return null;
                    }
                    var whenFalse = decode(falseTarget, endTarget);
                    if (whenFalse == null) {
                        return null;
                    }
                    stack.add(new CompiledNode.Ite(condition, whenTrue, whenFalse));
                    pc = endTarget;
                    pendingJumpTarget = -1;
                }

                case Opcodes.JUMP -> {
                    // Ends the true arm of an ite; report where it lands and hand back the arm's value.
                    pendingJumpTarget = pc + 2 + u16(pc);
                    return stack.size() == 1 ? stack.get(0) : null;
                }

                case Opcodes.RETURN_VALUE -> {
                    return stack.size() == 1 ? stack.get(0) : null;
                }
                case Opcodes.SET_REG_RETURN -> {
                    int reg = code[pc] & 0xFF;
                    return stack.size() == 1 ? new CompiledNode.SetReg(reg, stack.get(0)) : null;
                }
                case Opcodes.RETURN_ERROR -> {
                    // pc already sits past the opcode byte, which is the position to report.
                    return stack.size() == 1 ? new CompiledNode.ReturnError(stack.get(0), pc) : null;
                }
                case Opcodes.RETURN_ENDPOINT -> {
                    int packed = code[pc] & 0xFF;
                    var url = pop(stack);
                    var properties = (packed & 2) != 0 ? pop(stack) : null;
                    var headers = (packed & 1) != 0 ? pop(stack) : null;
                    return stack.isEmpty() ? CompiledNode.returnEndpoint(url, properties, headers) : null;
                }

                default -> {
                    return null;
                }
            }
        }

        // Ran off the end of the range without a terminator: valid only for a coalesce or ite arm, which
        // leaves exactly its value behind.
        return stack.size() == 1 ? stack.get(0) : null;
    }

    private int u16(int offset) {
        return ((code[offset] & 0xFF) << 8) | (code[offset + 1] & 0xFF);
    }

    private String string(int constantIndex) {
        Object value = constantPool[constantIndex];
        if (value instanceof String s) {
            return s;
        }
        throw new IllegalArgumentException("Expected a string constant at " + constantIndex);
    }

    private RulesFunction fn(int index) {
        if (functions == null || index >= functions.length) {
            throw new IllegalArgumentException("Unknown function index " + index);
        }
        return functions[index];
    }

    private static CompiledNode pop(List<CompiledNode> stack) {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Symbolic stack underflow");
        }
        return stack.removeLast();
    }

    /** Removes the top {@code count} entries and returns them in push order. */
    private static List<CompiledNode> drain(List<CompiledNode> stack, int count) {
        if (count > stack.size()) {
            throw new IllegalStateException("Symbolic stack underflow");
        }
        var sub = stack.subList(stack.size() - count, stack.size());
        var copy = new ArrayList<CompiledNode>(sub);
        sub.clear();
        return copy;
    }

    /** Picks the list node for this arity, so evaluation never re-decides it. */
    private static CompiledNode list(List<CompiledNode> stack, int size) {
        var elements = drain(stack, size);
        return switch (size) {
            case 1 -> new CompiledNode.List1(elements.get(0));
            case 2 -> new CompiledNode.List2(elements.get(0), elements.get(1));
            default -> new CompiledNode.ListN(elements.toArray(new CompiledNode[0]));
        };
    }

    /** Map entries are pushed value-then-key, so the keys sit at the odd offsets of the drained run. */
    private static CompiledNode map(List<CompiledNode> stack, int size) {
        var slots = drain(stack, size * 2);
        var keys = new String[size];
        var values = new CompiledNode[size];
        for (int i = 0; i < size; i++) {
            String key = CompiledNode.constantString(slots.get(i * 2 + 1));
            if (key == null) {
                throw new IllegalArgumentException("Map key is not a string constant");
            }
            keys[i] = key;
            values[i] = slots.get(i * 2);
        }
        return switch (size) {
            case 1 -> new CompiledNode.Map1(keys[0], values[0]);
            case 2 -> new CompiledNode.Map2(keys[0], values[0], keys[1], values[1]);
            default -> new CompiledNode.MapN(keys, values);
        };
    }

    /**
     * {@code STRUCTN} consumes an interleaved value/key run and hands it straight to
     * {@link ArrayPropertyGetter}. Keys are always constants, so they stay on the compiled node.
     */
    private static CompiledNode struct(List<CompiledNode> stack, int size) {
        var slots = drain(stack, size * 2);
        var keys = new String[size];
        var values = new CompiledNode[size];
        for (int i = 0; i < size; i++) {
            String key = CompiledNode.constantString(slots.get(i * 2 + 1));
            if (key == null) {
                throw new IllegalArgumentException("Struct key is not a string constant");
            }
            keys[i] = key;
            values[i] = slots.get(i * 2);
        }
        return new CompiledNode.StructNode(keys, values);
    }
}
