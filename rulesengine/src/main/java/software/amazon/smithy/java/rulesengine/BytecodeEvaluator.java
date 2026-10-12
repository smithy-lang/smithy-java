/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.endpoints.Endpoint;
import software.amazon.smithy.java.io.uri.SmithyUri;
import software.amazon.smithy.rulesengine.logic.ConditionEvaluator;
import software.amazon.smithy.rulesengine.logic.bdd.Bdd;

/**
 * Walks a program's BDD and evaluates the one condition path and one result it reaches.
 *
 * <p>Holds the per-resolution state — registers, operand scratch, the URI hot-slot cache — that the
 * shared, immutable {@link CompiledNode} trees read through. One evaluator is used by one thread at a
 * time; {@link BytecodeEndpointResolver} pools them.
 */
final class BytecodeEvaluator implements ConditionEvaluator {

    final ContextProvider.RegisterSink registerSink;

    private final Bytecode bytecode;
    // Package-private so the CompiledNode trees can reach the per-resolution state they need. The trees
    // are shared across evaluators and threads, so they hold no mutable state of their own.
    final Object[] registers;
    // Reused, zero-allocation live view over named registers, handed to a trace sink (one per evaluator).
    private final RegisterView registerView;
    final RulesExtension[] extensions;
    Object[] stack = new Object[64]; // 64 is more than enough for virtual any ruleset
    int stackPosition = 0;
    private char[] charBuffer = new char[128];
    final UriFactory uriFactory = new UriFactory();
    private final RegisterFiller registerFiller;
    Context context;
    // Hot-slot cache for BUILD_URI
    String cachedUriHost;
    String cachedUriPath;
    SmithyUri cachedUri;

    BytecodeEvaluator(Bytecode bytecode, RulesExtension[] extensions, RegisterFiller registerFiller) {
        this.bytecode = bytecode;
        this.extensions = extensions;
        this.registers = new Object[bytecode.getRegisterDefinitions().length];
        this.registerView = new RegisterView(registers, bytecode.getRegisterNameMap());
        this.registerFiller = registerFiller;
        this.registerSink = new ContextProvider.RegisterSink(registers.length, bytecode.getInputRegisterMap());
    }

    /**
     * Reset the evaluator and registers of the evaluator so it can be reused, using the given context and
     * input parameters.
     *
     * @param context Context to get context from.
     * @param parameters Parameters to get input from.
     */
    void reset(Context context, Map<String, Object> parameters) {
        this.context = context;
        this.stackPosition = 0;
        registerFiller.fillRegisters(registers, context, parameters);
    }

    void resetFromSink(Context context) {
        this.context = context;
        this.stackPosition = 0;
        // Copy template to set defaults and clear old state
        System.arraycopy(registerFiller.registerTemplate, 0, registers, 0, registers.length);
        // Overlay sink values and capture filled mask
        long prefilled = registerSink.filled;
        registerSink.drainTo(registers);
        // Fill builtins and validate
        registerFiller.fillRegisters(registers, context, prefilled);
    }

    @Override
    public boolean test(int conditionIndex) {
        // Reset stack position for fresh evaluation
        stackPosition = 0;
        try {
            return bytecode.conditionNode(conditionIndex).test(this);
        } catch (ArrayIndexOutOfBoundsException | ClassCastException | NullPointerException e) {
            throw new RulesEvaluationError("Rules evaluation error: " + e.getMessage(), -1);
        }
    }

    /**
     * Evaluates the BDD with inline condition optimization.
     * Trivial conditions (register checks, string equality) are evaluated directly
     * without entering the full bytecode dispatch loop.
     */
    Endpoint evaluateBdd() {
        int ref = bytecode.getBddRootRef();
        int[] nodes = bytecode.getBddNodes();
        byte[] condTypes = bytecode.conditionTypes;
        int[] condOps = bytecode.conditionOperands;
        Object[] regs = this.registers;
        Object[] cpool = bytecode.getConstantPool();

        while (Bdd.isNodeReference(ref)) {
            int base = (ref > 0 ? ref - 1 : -ref - 1) * 3;
            int condIdx = nodes[base];

            boolean result = switch (condTypes[condIdx]) {
                case Bytecode.COND_ISSET -> regs[condOps[condIdx]] != null;
                case Bytecode.COND_IS_TRUE -> regs[condOps[condIdx]] == Boolean.TRUE;
                case Bytecode.COND_IS_FALSE -> regs[condOps[condIdx]] == Boolean.FALSE;
                case Bytecode.COND_NOT_SET -> regs[condOps[condIdx]] == null;
                case Bytecode.COND_STRING_EQ_REG_CONST -> {
                    int packed = condOps[condIdx];
                    String s = (String) regs[packed & 0xFF];
                    String expected = (String) cpool[packed >>> 8];
                    yield s != null && s.equals(expected);
                }
                default -> test(condIdx);
            };

            // `branch` applies the node's complement bit to the condition result to pick high/low.
            boolean branch = result ^ (ref < 0);
            ref = branch ? nodes[base + 1] : nodes[base + 2];
        }

        if (Bdd.isTerminal(ref)) {
            return null;
        }
        return resolveResult(ref - Bdd.RESULT_OFFSET);
    }

    /**
     * Traced twin of {@link #evaluateBdd()}. The traversal loop is intentionally kept line-for-line
     * identical to the fast path, differing only by the {@link BddTrace#node} / {@link BddTrace#result}
     * callbacks: keep the two in sync if either changes. It is a separate method (rather than a flag in
     * the fast loop) so the hot path carries no per-node trace branch; the resolver calls this only when
     * a sink is present, and it falls back to {@link #evaluateBdd()} if the sink declines to trace.
     */
    Endpoint evaluateBddTraced(BddTraceSink sink) {
        BddTrace trace = sink.begin(bytecode, registerView);
        if (trace == null) {
            // Sampled out by the sink: run the untraced fast path.
            return evaluateBdd();
        }

        int ref = bytecode.getBddRootRef();
        int[] nodes = bytecode.getBddNodes();
        byte[] condTypes = bytecode.conditionTypes;
        int[] condOps = bytecode.conditionOperands;
        Object[] regs = this.registers;
        Object[] cpool = bytecode.getConstantPool();

        while (Bdd.isNodeReference(ref)) {
            int base = (ref > 0 ? ref - 1 : -ref - 1) * 3;
            int condIdx = nodes[base];

            boolean result = switch (condTypes[condIdx]) {
                case Bytecode.COND_ISSET -> regs[condOps[condIdx]] != null;
                case Bytecode.COND_IS_TRUE -> regs[condOps[condIdx]] == Boolean.TRUE;
                case Bytecode.COND_IS_FALSE -> regs[condOps[condIdx]] == Boolean.FALSE;
                case Bytecode.COND_NOT_SET -> regs[condOps[condIdx]] == null;
                case Bytecode.COND_STRING_EQ_REG_CONST -> {
                    int packed = condOps[condIdx];
                    String s = (String) regs[packed & 0xFF];
                    String expected = (String) cpool[packed >>> 8];
                    yield s != null && s.equals(expected);
                }
                default -> test(condIdx);
            };

            // `branch` applies the node's complement bit to the condition result to pick high/low.
            // Reported to the trace alongside `result` (they differ only on a complemented node ref).
            boolean branch = result ^ (ref < 0);
            trace.node(ref, condIdx, result, branch);
            ref = branch ? nodes[base + 1] : nodes[base + 2];
        }

        if (Bdd.isTerminal(ref)) {
            trace.result(-1, null);
            return null;
        }

        int resultId = ref - Bdd.RESULT_OFFSET;
        Endpoint endpoint = resolveResult(resultId);
        trace.result(resultId, endpoint);
        return endpoint;
    }

    public Endpoint resolveResult(int resultIndex) {
        if (resultIndex <= -1) {
            return null;
        }
        stackPosition = 0;
        try {
            return (Endpoint) bytecode.resultNode(resultIndex).eval(this);
        } catch (ArrayIndexOutOfBoundsException | ClassCastException | NullPointerException e) {
            throw new RulesEvaluationError("Rules evaluation error: " + e.getMessage(), -1);
        }
    }

    char[] getCharBuffer(int requiredSize) {
        if (charBuffer.length >= requiredSize) {
            return charBuffer;
        }
        charBuffer = new char[Math.max(requiredSize, charBuffer.length * 2)];
        return charBuffer;
    }

    /**
     * Live, read-only {@link Map} view over the named (non-temp) registers, handed to a
     * {@link BddTraceSink} without copying. {@code get(name)} is an O(1) index into the register array;
     * the view instance is reused, so nothing is allocated per resolution. Unmapped names and null
     * registers read as not-present, matching the original snapshot's non-null-entries semantics.
     */
    private static final class RegisterView extends AbstractMap<String, Object> {
        private final Object[] registers;
        private final Map<String, Integer> nameToIndex;
        private Set<Entry<String, Object>> entrySet;

        RegisterView(Object[] registers, Map<String, Integer> nameToIndex) {
            this.registers = registers;
            this.nameToIndex = nameToIndex;
        }

        @Override
        public Object get(Object key) {
            Integer idx = nameToIndex.get(key);
            return idx == null ? null : registers[idx];
        }

        @Override
        public boolean containsKey(Object key) {
            Integer idx = nameToIndex.get(key);
            return idx != null && registers[idx] != null;
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            Set<Entry<String, Object>> es = entrySet;
            if (es == null) {
                es = entrySet = new AbstractSet<>() {
                    @Override
                    public Iterator<Entry<String, Object>> iterator() {
                        return new Iterator<>() {
                            private final Iterator<Map.Entry<String, Integer>> it = nameToIndex.entrySet().iterator();
                            private Entry<String, Object> next;

                            @Override
                            public boolean hasNext() {
                                while (next == null && it.hasNext()) {
                                    var e = it.next();
                                    Object value = registers[e.getValue()];
                                    if (value != null) {
                                        next = new SimpleImmutableEntry<>(e.getKey(), value);
                                    }
                                }
                                return next != null;
                            }

                            @Override
                            public Entry<String, Object> next() {
                                if (!hasNext()) {
                                    throw new NoSuchElementException();
                                }
                                var result = next;
                                next = null;
                                return result;
                            }
                        };
                    }

                    @Override
                    public int size() {
                        int count = 0;
                        for (int idx : nameToIndex.values()) {
                            if (registers[idx] != null) {
                                count++;
                            }
                        }
                        return count;
                    }
                };
            }
            return es;
        }
    }
}
