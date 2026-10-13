/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.endpoints.Endpoint;
import software.amazon.smithy.java.endpoints.EndpointContext;
import software.amazon.smithy.java.io.uri.SmithyUri;
import software.amazon.smithy.java.io.uri.URLEncoding;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.Split;

/**
 * A condition or result compiled out of the stack bytecode into a tree of immutable nodes.
 *
 * <p>This is how a rules program is evaluated. The bytecode is the serialization format — what
 * {@link BytecodeWriter} emits and {@link BytecodeReader} parses — and {@link NodeCompiler} decodes it
 * into these trees on first use.
 *
 * <p>It used to be executed directly by a stack interpreter, which paid a mispredicted indirect branch
 * for every instruction because all ~55 opcodes shared one {@code tableswitch} dispatch site. A typical
 * S3 condition is 3-7 instructions and a typical result is ~22, so dispatch dominated: a resolve spent
 * most of its time deciding what to do next rather than doing it. These trees have their operands already
 * decoded into final fields and carry no operand stack except where a node genuinely needs scratch space,
 * which measured 1.24x to 1.59x faster end to end.
 *
 * <p>Nodes are shared across every {@link BytecodeEvaluator} built from the same {@code Bytecode}, and
 * therefore across threads, so they must stay immutable — {@link Bytecode} relies on every field of
 * every node being final to publish lazily compiled trees by a plain array write, with no
 * synchronization. Anything per-resolution (registers, context, the URI hot-slot cache, scratch buffers)
 * is reached through the {@link BytecodeEvaluator} passed to {@link #eval}.
 *
 * <p>That is why the hierarchy is sealed: it confines the set of node types to this file, so the
 * all-fields-final invariant the publication relies on can be checked by reading one compilation unit. A
 * subclass added elsewhere with a mutable field would make the race in {@code Bytecode} unsafe in a way
 * that is very unlikely to show up in a test.
 */
abstract sealed class CompiledNode {

    /**
     * Evaluates this node to its value.
     *
     * @param ev evaluator holding the per-resolution state.
     * @return the value, which may be null.
     */
    abstract Object eval(BytecodeEvaluator ev);

    /**
     * Evaluates this node as a condition: anything but null or {@link Boolean#FALSE} is true.
     *
     * <p>Boolean-valued nodes override this to test without boxing.
     *
     * @param ev evaluator holding the per-resolution state.
     * @return true if the value is neither null nor {@link Boolean#FALSE}.
     */
    boolean test(BytecodeEvaluator ev) {
        Object v = eval(ev);
        return v != null && v != Boolean.FALSE;
    }

    /** Base for nodes that are naturally boolean, so {@link #test} avoids boxing. */
    private abstract static sealed class BoolNode extends CompiledNode {
        @Override
        final Object eval(BytecodeEvaluator ev) {
            return test(ev) ? Boolean.TRUE : Boolean.FALSE;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Leaves
    // ---------------------------------------------------------------------------------------------

    static final class Const extends CompiledNode {
        private final Object value;

        Const(Object value) {
            this.value = value;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            return value;
        }
    }

    static final class Reg extends CompiledNode {
        private final int index;

        Reg(int index) {
            this.index = index;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            return ev.registers[index];
        }
    }

    static final class RegIsSet extends BoolNode {
        private final int index;

        RegIsSet(int index) {
            this.index = index;
        }

        @Override
        boolean test(BytecodeEvaluator ev) {
            return ev.registers[index] != null;
        }
    }

    static final class RegNotSet extends BoolNode {
        private final int index;

        RegNotSet(int index) {
            this.index = index;
        }

        @Override
        boolean test(BytecodeEvaluator ev) {
            return ev.registers[index] == null;
        }
    }

    static final class RegIsTrue extends BoolNode {
        private final int index;

        RegIsTrue(int index) {
            this.index = index;
        }

        @Override
        boolean test(BytecodeEvaluator ev) {
            return ev.registers[index] == Boolean.TRUE;
        }
    }

    static final class RegIsFalse extends BoolNode {
        private final int index;

        RegIsFalse(int index) {
            this.index = index;
        }

        @Override
        boolean test(BytecodeEvaluator ev) {
            return ev.registers[index] == Boolean.FALSE;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Register writes
    // ---------------------------------------------------------------------------------------------

    /** {@code SET_REG_RETURN}: store the value in a register and yield it. */
    static final class SetReg extends CompiledNode {
        private final int index;
        private final CompiledNode value;

        SetReg(int index, CompiledNode value) {
            this.index = index;
            this.value = value;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            Object v = value.eval(ev);
            ev.registers[index] = v;
            return v;
        }

        @Override
        boolean test(BytecodeEvaluator ev) {
            Object v = value.eval(ev);
            ev.registers[index] = v;
            return v != null && v != Boolean.FALSE;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Property and index access
    // ---------------------------------------------------------------------------------------------

    static final class Prop extends CompiledNode {
        private final CompiledNode target;
        private final String name;

        Prop(CompiledNode target, String name) {
            this.target = target;
            this.name = name;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            return EndpointUtils.getProperty(target.eval(ev), name);
        }
    }

    /** {@code GET_PROPERTY_REG}, and the fused {@code LOAD_REGISTER; GET_PROPERTY} pair. */
    static final class PropReg extends CompiledNode {
        private final int index;
        private final String name;

        PropReg(int index, String name) {
            this.index = index;
            this.name = name;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            return EndpointUtils.getProperty(ev.registers[index], name);
        }
    }

    static final class Index extends CompiledNode {
        private final CompiledNode target;
        private final int index;

        Index(CompiledNode target, int index) {
            this.target = target;
            this.index = index;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            return EndpointUtils.getIndex(target.eval(ev), index);
        }
    }

    static final class NegIndex extends CompiledNode {
        private final CompiledNode target;
        private final int index;

        NegIndex(CompiledNode target, int index) {
            this.target = target;
            this.index = index;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            return EndpointUtils.getNegativeIndex(target.eval(ev), index);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Strings
    // ---------------------------------------------------------------------------------------------

    static final class Substring extends CompiledNode {
        private final CompiledNode target;
        private final int start;
        private final int end;
        private final boolean reverse;

        Substring(CompiledNode target, int start, int end, boolean reverse) {
            this.target = target;
            this.start = start;
            this.end = end;
            this.reverse = reverse;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            return EndpointUtils.getSubstring((String) target.eval(ev), start, end, reverse);
        }
    }

    /**
     * {@code stringEquals(coalesce(substring(reg, s, e, rev), ""), "literal")}, which the rules compiler
     * emits as six instructions and which collapses to a single bounded {@code regionMatches}. This is the
     * most common non-trivial condition shape in the S3 ruleset.
     */
    static final class SubstringEqReg extends BoolNode {
        private final int index;
        private final int start;
        private final int end;
        private final boolean reverse;
        private final String expected;

        SubstringEqReg(int index, int start, int end, boolean reverse, String expected) {
            this.index = index;
            this.start = start;
            this.end = end;
            this.reverse = reverse;
            this.expected = expected;
        }

        @Override
        boolean test(BytecodeEvaluator ev) {
            return EndpointUtils.substringEquals((String) ev.registers[index], start, end, reverse, expected);
        }
    }

    static final class StringEq extends BoolNode {
        private final CompiledNode left;
        private final CompiledNode right;

        StringEq(CompiledNode left, CompiledNode right) {
            this.left = left;
            this.right = right;
        }

        @Override
        boolean test(BytecodeEvaluator ev) {
            var a = (String) left.eval(ev);
            return a != null && a.equals(right.eval(ev));
        }
    }

    /** {@code STRING_EQUALS} against a constant, which covers most property and register comparisons. */
    static final class StringEqConst extends BoolNode {
        private final CompiledNode left;
        private final String expected;

        StringEqConst(CompiledNode left, String expected) {
            this.left = left;
            this.expected = expected;
        }

        @Override
        boolean test(BytecodeEvaluator ev) {
            var a = (String) left.eval(ev);
            return a != null && a.equals(expected);
        }
    }

    static final class StringEqRegConst extends BoolNode {
        private final int index;
        private final String expected;

        StringEqRegConst(int index, String expected) {
            this.index = index;
            this.expected = expected;
        }

        @Override
        boolean test(BytecodeEvaluator ev) {
            var a = (String) ev.registers[index];
            return a != null && a.equals(expected);
        }
    }

    static final class UriEncode extends CompiledNode {
        private final CompiledNode target;

        UriEncode(CompiledNode target) {
            this.target = target;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            return URLEncoding.encodeUnreserved((String) target.eval(ev), false);
        }
    }

    static final class SplitNode extends CompiledNode {
        private final CompiledNode value;
        private final CompiledNode delimiter;
        private final CompiledNode limit;

        SplitNode(CompiledNode value, CompiledNode delimiter, CompiledNode limit) {
            this.value = value;
            this.delimiter = delimiter;
            this.limit = limit;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            var s = (String) value.eval(ev);
            var d = (String) delimiter.eval(ev);
            return Split.split(s, d, ((Number) limit.eval(ev)).intValue());
        }
    }

    static final class SplitGetReg extends CompiledNode {
        private final int index;
        private final String delimiter;
        private final int element;

        SplitGetReg(int index, String delimiter, int element) {
            this.index = index;
            this.delimiter = delimiter;
            this.element = element;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            return EndpointUtils.splitGet((String) ev.registers[index], delimiter, element);
        }
    }

    /**
     * {@code RESOLVE_TEMPLATE}: concatenates alternating literal and dynamic parts as
     * {@code literals[0] + dynamics[0] + literals[1] + ... + literals[n]}. Adjacent constant parts are
     * merged at compile time and their total length is precomputed, so a template of mostly-constant
     * parts costs one node call per genuinely dynamic part.
     *
     * <p>Evaluated dynamic parts are parked on the evaluator's operand stack rather than in a field,
     * since nodes are shared across threads; the stack position is bumped as each part is evaluated so a
     * nested template cannot clobber an outer one, and restored before returning.
     */
    static final class Template extends CompiledNode {
        private final String[] literals;
        private final CompiledNode[] dynamics;
        private final int literalLength;

        Template(String[] literals, CompiledNode[] dynamics) {
            this.literals = literals;
            this.dynamics = dynamics;
            int len = 0;
            for (String literal : literals) {
                len += literal.length();
            }
            this.literalLength = len;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            Object[] stack = ev.stack;
            int base = ev.stackPosition;
            int total = literalLength;
            for (CompiledNode dynamic : dynamics) {
                var s = (String) dynamic.eval(ev);
                stack[ev.stackPosition++] = s;
                total += s.length();
            }
            ev.stackPosition = base;

            char[] buf = ev.getCharBuffer(total);
            int pos = 0;
            for (int i = 0; i < dynamics.length; i++) {
                pos = append(buf, pos, literals[i]);
                pos = append(buf, pos, (String) stack[base + i]);
            }
            append(buf, pos, literals[literals.length - 1]);
            return new String(buf, 0, total);
        }

        private static int append(char[] buf, int pos, String s) {
            int len = s.length();
            if (len != 0) {
                s.getChars(0, len, buf, pos);
            }
            return pos + len;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Booleans and control flow
    // ---------------------------------------------------------------------------------------------

    static final class IsTrue extends BoolNode {
        private final CompiledNode target;

        IsTrue(CompiledNode target) {
            this.target = target;
        }

        @Override
        boolean test(BytecodeEvaluator ev) {
            return target.eval(ev) == Boolean.TRUE;
        }
    }

    static final class IsSet extends BoolNode {
        private final CompiledNode target;

        IsSet(CompiledNode target) {
            this.target = target;
        }

        @Override
        boolean test(BytecodeEvaluator ev) {
            return target.eval(ev) != null;
        }
    }

    static final class Not extends BoolNode {
        private final CompiledNode target;

        Not(CompiledNode target) {
            this.target = target;
        }

        @Override
        boolean test(BytecodeEvaluator ev) {
            return target.eval(ev) == Boolean.FALSE;
        }
    }

    static final class EqualsNode extends BoolNode {
        private final CompiledNode left;
        private final CompiledNode right;

        EqualsNode(CompiledNode left, CompiledNode right) {
            this.left = left;
            this.right = right;
        }

        @Override
        boolean test(BytecodeEvaluator ev) {
            return Objects.equals(left.eval(ev), right.eval(ev));
        }
    }

    static final class BooleanEq extends BoolNode {
        private final CompiledNode left;
        private final CompiledNode right;

        BooleanEq(CompiledNode left, CompiledNode right) {
            this.left = left;
            this.right = right;
        }

        @Override
        boolean test(BytecodeEvaluator ev) {
            var a = (Boolean) left.eval(ev);
            return a != null && a.equals(right.eval(ev));
        }
    }

    static final class IsValidHostLabelNode extends BoolNode {
        private final CompiledNode target;
        private final CompiledNode allowDots;

        IsValidHostLabelNode(CompiledNode target, CompiledNode allowDots) {
            this.target = target;
            this.allowDots = allowDots;
        }

        @Override
        boolean test(BytecodeEvaluator ev) {
            var label = (String) target.eval(ev);
            return EndpointUtils.isValidHostLabel(label, Boolean.TRUE.equals(allowDots.eval(ev)));
        }
    }

    /** {@code IS_VALID_HOST_LABEL} where the allow-dots flag is a constant, as it always is in practice. */
    static final class IsValidHostLabelConst extends BoolNode {
        private final CompiledNode target;
        private final boolean allowDots;

        IsValidHostLabelConst(CompiledNode target, boolean allowDots) {
            this.target = target;
            this.allowDots = allowDots;
        }

        @Override
        boolean test(BytecodeEvaluator ev) {
            return EndpointUtils.isValidHostLabel((String) target.eval(ev), allowDots);
        }
    }

    static final class ParseUrlNode extends CompiledNode {
        private final CompiledNode target;

        ParseUrlNode(CompiledNode target) {
            this.target = target;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            var s = (String) target.eval(ev);
            return s == null ? null : ev.uriFactory.createUri(s);
        }
    }

    /** {@code JNN_OR_POP} chains, i.e. {@code coalesce}: the first argument that is not null wins. */
    static final class Coalesce extends CompiledNode {
        private final CompiledNode[] options;

        Coalesce(CompiledNode[] options) {
            this.options = options;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            for (int i = 0; i < options.length - 1; i++) {
                Object v = options[i].eval(ev);
                if (v != null) {
                    return v;
                }
            }
            return options[options.length - 1].eval(ev);
        }
    }

    /** {@code JMP_IF_FALSE}/{@code JUMP} pairs, i.e. {@code ite}. */
    static final class Ite extends CompiledNode {
        private final CompiledNode condition;
        private final CompiledNode whenTrue;
        private final CompiledNode whenFalse;

        Ite(CompiledNode condition, CompiledNode whenTrue, CompiledNode whenFalse) {
            this.condition = condition;
            this.whenTrue = whenTrue;
            this.whenFalse = whenFalse;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            return condition.eval(ev) == Boolean.TRUE ? whenTrue.eval(ev) : whenFalse.eval(ev);
        }
    }

    /** {@code SELECT_BOOL_REG}: pick one of two constants based on a boolean register. */
    static final class SelectBoolReg extends CompiledNode {
        private final int index;
        private final Object whenTrue;
        private final Object whenFalse;

        SelectBoolReg(int index, Object whenTrue, Object whenFalse) {
            this.index = index;
            this.whenTrue = whenTrue;
            this.whenFalse = whenFalse;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            Object v = ev.registers[index];
            return v != null && v != Boolean.FALSE ? whenTrue : whenFalse;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Functions
    // ---------------------------------------------------------------------------------------------

    static final class Fn0 extends CompiledNode {
        private final RulesFunction fn;

        Fn0(RulesFunction fn) {
            this.fn = fn;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            return fn.apply0();
        }
    }

    static final class Fn1 extends CompiledNode {
        private final RulesFunction fn;
        private final CompiledNode arg1;

        Fn1(RulesFunction fn, CompiledNode arg1) {
            this.fn = fn;
            this.arg1 = arg1;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            return fn.apply1(arg1.eval(ev));
        }
    }

    static final class Fn2 extends CompiledNode {
        private final RulesFunction fn;
        private final CompiledNode arg1;
        private final CompiledNode arg2;

        Fn2(RulesFunction fn, CompiledNode arg1, CompiledNode arg2) {
            this.fn = fn;
            this.arg1 = arg1;
            this.arg2 = arg2;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            return fn.apply2(arg1.eval(ev), arg2.eval(ev));
        }
    }

    static final class Fn3 extends CompiledNode {
        private final RulesFunction fn;
        private final CompiledNode arg1;
        private final CompiledNode arg2;
        private final CompiledNode arg3;

        Fn3(RulesFunction fn, CompiledNode arg1, CompiledNode arg2, CompiledNode arg3) {
            this.fn = fn;
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.arg3 = arg3;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            return fn.apply(arg1.eval(ev), arg2.eval(ev), arg3.eval(ev));
        }
    }

    static final class FnN extends CompiledNode {
        private final RulesFunction fn;
        private final CompiledNode[] args;

        FnN(RulesFunction fn, CompiledNode[] args) {
            this.fn = fn;
            this.args = args;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            var values = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                values[i] = args[i].eval(ev);
            }
            return fn.apply(values);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Collections
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code LIST1}. Arity is fixed when the tree is built, so each arity gets its own node rather than
     * re-deciding on every evaluation; a shared node body cannot have its array length constant-folded.
     *
     * <p>Note the arity-dependent null handling, which matches what the opcodes have always done:
     * {@code List.of} rejects nulls while {@code Arrays.asList} in {@link ListN} permits them.
     */
    static final class List1 extends CompiledNode {
        private final CompiledNode element0;

        List1(CompiledNode element0) {
            this.element0 = element0;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            return List.of(element0.eval(ev));
        }
    }

    /** {@code LIST2}. */
    static final class List2 extends CompiledNode {
        private final CompiledNode element0;
        private final CompiledNode element1;

        List2(CompiledNode element0, CompiledNode element1) {
            this.element0 = element0;
            this.element1 = element1;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            return List.of(element0.eval(ev), element1.eval(ev));
        }
    }

    /** {@code LISTN}, which unlike {@link List1} and {@link List2} tolerates null elements. */
    static final class ListN extends CompiledNode {
        private final CompiledNode[] elements;

        ListN(CompiledNode[] elements) {
            this.elements = elements;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            var values = new Object[elements.length];
            for (int i = 0; i < elements.length; i++) {
                values[i] = elements[i].eval(ev);
            }
            return Arrays.asList(values);
        }
    }

    /**
     * {@code MAP1}. Keys are constants, so they are held directly rather than in an array, and the arity
     * picks the node class at build time instead of being switched on per evaluation. Every S3 result
     * builds its {@code authSchemes} property through this node or {@link Map2}.
     *
     * <p>As with the list nodes, the arity-dependent null handling is preserved: {@code Map.of} rejects
     * null values while the {@link MapN} {@link HashMap} permits them.
     */
    static final class Map1 extends CompiledNode {
        private final String key0;
        private final CompiledNode value0;

        Map1(String key0, CompiledNode value0) {
            this.key0 = key0;
            this.value0 = value0;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            return Map.of(key0, value0.eval(ev));
        }
    }

    /** {@code MAP2}. */
    static final class Map2 extends CompiledNode {
        private final String key0;
        private final CompiledNode value0;
        private final String key1;
        private final CompiledNode value1;

        Map2(String key0, CompiledNode value0, String key1, CompiledNode value1) {
            this.key0 = key0;
            this.value0 = value0;
            this.key1 = key1;
            this.value1 = value1;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            return Map.of(key0, value0.eval(ev), key1, value1.eval(ev));
        }
    }

    /** {@code MAP3}, {@code MAP4} and {@code MAPN}, which tolerate null values. */
    static final class MapN extends CompiledNode {
        private final String[] keys;
        private final CompiledNode[] values;

        MapN(String[] keys, CompiledNode[] values) {
            this.keys = keys;
            this.values = values;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            Map<String, Object> map = new HashMap<>(keys.length + 1, 1.0f);
            for (int i = 0; i < keys.length; i++) {
                map.put(keys[i], values[i].eval(ev));
            }
            return map;
        }
    }

    /**
     * {@code STRUCTN}: builds an {@link ArrayPropertyGetter} over an interleaved value/key array. Keys
     * are always constants, so only the value slots are evaluated per resolution.
     */
    static final class StructNode extends CompiledNode {
        private final Object[] template;
        private final CompiledNode[] values;

        StructNode(Object[] template, CompiledNode[] values) {
            this.template = template;
            this.values = values;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            Object[] data = template.clone();
            for (int i = 0; i < values.length; i++) {
                data[i * 2] = values[i].eval(ev);
            }
            return new ArrayPropertyGetter(data);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Terminals
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code BUILD_URI}, including the host/path hot-slot cache. The cache lives on the evaluator because
     * nodes are shared across threads.
     */
    static final class BuildUri extends CompiledNode {
        private final String scheme;
        private final CompiledNode host;
        private final CompiledNode path;

        BuildUri(String scheme, CompiledNode host, CompiledNode path) {
            this.scheme = scheme;
            this.host = host;
            this.path = path;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            var h = (String) host.eval(ev);
            var p = (String) path.eval(ev);
            if (h != null && h.equals(ev.cachedUriHost) && p.equals(ev.cachedUriPath)) {
                return ev.cachedUri;
            }
            var uri = SmithyUri.of(scheme, h, -1, p, null);
            ev.cachedUriHost = h;
            ev.cachedUriPath = p;
            ev.cachedUri = uri;
            return uri;
        }
    }

    @SuppressWarnings("unchecked")
    static final class ReturnEndpoint extends CompiledNode {
        private final CompiledNode url;
        private final CompiledNode properties;
        private final CompiledNode headers;

        ReturnEndpoint(CompiledNode url, CompiledNode properties, CompiledNode headers) {
            this.url = url;
            this.properties = properties;
            this.headers = headers;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            Object urlValue = url.eval(ev);
            var props = (Map<String, Object>) (properties == null ? Map.of() : properties.eval(ev));
            var hdrs = (Map<String, List<String>>) (headers == null ? Map.of() : headers.eval(ev));
            SmithyUri uri = urlValue instanceof SmithyUri su
                    ? su
                    : ev.uriFactory.createUri((String) urlValue);
            var builder = Endpoint.builder().uri(uri);
            if (!hdrs.isEmpty()) {
                builder.putProperty(EndpointContext.HEADERS, hdrs);
            }
            for (var extension : ev.extensions) {
                extension.extractEndpointProperties(builder, ev.context, props, hdrs);
            }
            return builder.build();
        }
    }

    static final class ReturnError extends CompiledNode {
        private final CompiledNode message;
        private final int position;

        /**
         * @param message the error message expression.
         * @param position bytecode offset of the instruction, reported in the error so a rules-authoring
         *                 mistake can be traced back to the program.
         */
        ReturnError(CompiledNode message, int position) {
            this.message = message;
            this.position = position;
        }

        @Override
        Object eval(BytecodeEvaluator ev) {
            throw new RulesEvaluationError((String) message.eval(ev), position);
        }
    }

    /**
     * Reads a property, folding a register load into the read.
     *
     * @param target node producing the object to read from.
     * @param name property name.
     * @return the property node.
     */
    static CompiledNode property(CompiledNode target, String name) {
        return target instanceof Reg reg ? new PropReg(reg.index, name) : new Prop(target, name);
    }

    /**
     * Builds a string comparison, folding the {@code coalesce(substring(reg, ...), "")} idiom the rules
     * compiler emits for prefix and suffix tests into a single bounded {@code regionMatches}.
     *
     * <p>The fold only applies when it is exactly equivalent. {@code substringEquals} compares only as
     * many characters of the expected string as the range covers, and unlike {@code getSubstring} it does
     * not reject a non-ASCII range, so the expected value has to be ASCII, non-empty, and exactly as long
     * as the range. {@code NodeCompilerTest} pins each of those three cases.
     *
     * @param left left operand.
     * @param right right operand.
     * @return the comparison node.
     */
    static CompiledNode stringEquals(CompiledNode left, CompiledNode right) {
        String expected = constantString(right);
        if (expected != null) {
            CompiledNode fused = fuseSubstringEquals(left, expected);
            return fused != null ? fused : stringEqualsConstant(left, expected);
        }
        expected = constantString(left);
        return expected != null ? stringEqualsConstant(right, expected) : new StringEq(left, right);
    }

    private static CompiledNode stringEqualsConstant(CompiledNode value, String expected) {
        return value instanceof Reg reg
                ? new StringEqRegConst(reg.index, expected)
                : new StringEqConst(value, expected);
    }

    private static CompiledNode fuseSubstringEquals(CompiledNode left, String expected) {
        if (!(left instanceof Coalesce coalesce) || expected.isEmpty() || !isAscii(expected)) {
            return null;
        }
        CompiledNode[] options = coalesce.options;
        if (options.length != 2
                || !"".equals(constantString(options[1]))
                || !(options[0] instanceof Substring substring)
                || !(substring.target instanceof Reg reg)
                || (substring.end - substring.start) != expected.length()) {
            return null;
        }
        return new SubstringEqReg(reg.index, substring.start, substring.end, substring.reverse, expected);
    }

    private static boolean isAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

    /**
     * The string a constant node holds.
     *
     * @param node node to inspect.
     * @return the string, or null if the node is not a constant string.
     */
    static String constantString(CompiledNode node) {
        return node instanceof Const c && c.value instanceof String s ? s : null;
    }

    /**
     * Builds a host-label check, folding the allow-dots flag when it is a constant, as it always is in
     * the rulesets seen in practice.
     *
     * @param target node producing the label.
     * @param allowDots node producing the allow-dots flag.
     * @return the check node.
     */
    static CompiledNode isValidHostLabel(CompiledNode target, CompiledNode allowDots) {
        return allowDots instanceof Const c && c.value instanceof Boolean flag
                ? new IsValidHostLabelConst(target, flag)
                : new IsValidHostLabelNode(target, allowDots);
    }

    /**
     * Merges runs of adjacent constant nodes in a template's parts into the literal slots expected by
     * {@link Template}, returning alternating literals and dynamic nodes.
     *
     * @param parts the template parts in output order.
     * @return the template node, or null if a part is a constant that is not a string.
     */
    static CompiledNode template(List<CompiledNode> parts) {
        List<String> literals = new ArrayList<>();
        List<CompiledNode> dynamics = new ArrayList<>();
        var pending = new StringBuilder();
        for (CompiledNode part : parts) {
            if (part instanceof Const c) {
                if (!(c.value instanceof String s)) {
                    return null;
                }
                pending.append(s);
            } else {
                literals.add(pending.toString());
                pending.setLength(0);
                dynamics.add(part);
            }
        }
        literals.add(pending.toString());
        if (dynamics.isEmpty()) {
            return new Const(literals.get(0));
        }
        return new Template(literals.toArray(new String[0]), dynamics.toArray(new CompiledNode[0]));
    }
}
