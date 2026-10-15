/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The load-time shape of an endpoint's properties, with a slot for each value that varies per
 * resolution.
 *
 * <p>Shapes and folded constants are interned per {@link Bytecode}. Results with the same property
 * topology therefore share the same shape DAG while retaining their own compiled expressions for the
 * dynamic slots.
 */
abstract sealed class PropertyPlan {

    /** A subtree with no interpolated leaf, materialized once at load time. */
    static final class Constant extends PropertyPlan {
        private final Object value;

        Constant(Object value) {
            this.value = value;
        }

        Object value() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Constant constant && Objects.equals(value, constant.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }
    }

    /** An expression evaluated per resolution, held at {@code slot}. */
    static final class DynamicSlot extends PropertyPlan {
        private final int slot;

        DynamicSlot(int slot) {
            this.slot = slot;
        }

        int slot() {
            return slot;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof DynamicSlot dynamic && slot == dynamic.slot;
        }

        @Override
        public int hashCode() {
            return slot;
        }
    }

    /** A map whose keys are constants and at least one of whose values varies. */
    static final class MapShape extends PropertyPlan {
        private final String[] keys;
        private final PropertyPlan[] values;

        MapShape(String[] keys, PropertyPlan[] values) {
            this.keys = keys;
            this.values = values;
        }

        String[] keys() {
            return keys;
        }

        PropertyPlan[] values() {
            return values;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MapShape map && keys == map.keys && values == map.values;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(keys) + System.identityHashCode(values);
        }
    }

    /** A list at least one of whose elements varies. */
    static final class ListShape extends PropertyPlan {
        private final PropertyPlan[] elements;

        ListShape(PropertyPlan[] elements) {
            this.elements = elements;
        }

        PropertyPlan[] elements() {
            return elements;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ListShape list && elements == list.elements;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(elements);
        }
    }

    /** A {@code STRUCTN} block at least one of whose values varies. */
    static final class StructShape extends PropertyPlan {
        private final String[] keys;
        private final PropertyPlan[] values;

        StructShape(String[] keys, PropertyPlan[] values) {
            this.keys = keys;
            this.values = values;
        }

        String[] keys() {
            return keys;
        }

        PropertyPlan[] values() {
            return values;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof StructShape struct && keys == struct.keys && values == struct.values;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(keys) + System.identityHashCode(values);
        }
    }

    /**
     * A result-specific binding of dynamic expressions to an interned property shape.
     *
     * <p>The common zero-through-three-slot cases keep evaluated values in Java locals and hand them
     * directly to the materializer. Only the uncommon larger case allocates a staging array.
     */
    sealed interface Compiled permits Compiled0, Compiled1, Compiled2, Compiled3, CompiledN {
        PropertyPlan plan();

        int slotCount();

        Object resolve(BytecodeEvaluator evaluator);

        CompiledNode dynamic(int index);

        default boolean isConstantEmptyMap() {
            return this instanceof Compiled0 compiled
                    && compiled.value() instanceof Map<?, ?> map
                    && map.isEmpty();
        }
    }

    record Compiled0(PropertyPlan plan, Object value) implements Compiled {
        @Override
        public int slotCount() {
            return 0;
        }

        @Override
        public Object resolve(BytecodeEvaluator evaluator) {
            return value;
        }

        @Override
        public CompiledNode dynamic(int index) {
            throw new IndexOutOfBoundsException(index);
        }
    }

    record Compiled1(PropertyPlan plan, CompiledNode dynamic0) implements Compiled {
        @Override
        public int slotCount() {
            return 1;
        }

        @Override
        public Object resolve(BytecodeEvaluator evaluator) {
            return PropertyViews.materialize(plan, dynamic0.eval(evaluator));
        }

        @Override
        public CompiledNode dynamic(int index) {
            if (index == 0) {
                return dynamic0;
            }
            throw new IndexOutOfBoundsException(index);
        }
    }

    record Compiled2(
            PropertyPlan plan,
            CompiledNode dynamic0,
            CompiledNode dynamic1) implements Compiled {
        @Override
        public int slotCount() {
            return 2;
        }

        @Override
        public Object resolve(BytecodeEvaluator evaluator) {
            Object value0 = dynamic0.eval(evaluator);
            Object value1 = dynamic1.eval(evaluator);
            return PropertyViews.materialize(plan, value0, value1);
        }

        @Override
        public CompiledNode dynamic(int index) {
            return switch (index) {
                case 0 -> dynamic0;
                case 1 -> dynamic1;
                default -> throw new IndexOutOfBoundsException(index);
            };
        }
    }

    record Compiled3(
            PropertyPlan plan,
            CompiledNode dynamic0,
            CompiledNode dynamic1,
            CompiledNode dynamic2) implements Compiled {
        @Override
        public int slotCount() {
            return 3;
        }

        @Override
        public Object resolve(BytecodeEvaluator evaluator) {
            Object value0 = dynamic0.eval(evaluator);
            Object value1 = dynamic1.eval(evaluator);
            Object value2 = dynamic2.eval(evaluator);
            return PropertyViews.materialize(plan, value0, value1, value2);
        }

        @Override
        public CompiledNode dynamic(int index) {
            return switch (index) {
                case 0 -> dynamic0;
                case 1 -> dynamic1;
                case 2 -> dynamic2;
                default -> throw new IndexOutOfBoundsException(index);
            };
        }
    }

    static final class CompiledN implements Compiled {
        private final PropertyPlan plan;
        private final CompiledNode[] dynamics;

        CompiledN(PropertyPlan plan, CompiledNode[] dynamics) {
            this.plan = plan;
            this.dynamics = dynamics;
        }

        @Override
        public PropertyPlan plan() {
            return plan;
        }

        @Override
        public int slotCount() {
            return dynamics.length;
        }

        @Override
        public Object resolve(BytecodeEvaluator evaluator) {
            var values = new Object[dynamics.length];
            for (int i = 0; i < dynamics.length; i++) {
                values[i] = dynamics[i].eval(evaluator);
            }
            return PropertyViews.materialize(plan, values);
        }

        @Override
        public CompiledNode dynamic(int index) {
            return dynamics[index];
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof CompiledN compiled
                    && Objects.equals(plan, compiled.plan)
                    && dynamics == compiled.dynamics;
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hashCode(plan) + System.identityHashCode(dynamics);
        }
    }

    /**
     * Per-bytecode shape and constant interner. Compilation is lazy and can race, so factories are
     * synchronized; none of this synchronization is on the resolution path.
     */
    static final class Interner {
        private final IdentityHashMap<Object, Constant> literalConstants = new IdentityHashMap<>();
        private final Map<Object, Constant> foldedConstants = new HashMap<>();
        private final Map<Integer, DynamicSlot> slots = new HashMap<>();
        private final Map<ShapeKey, PropertyPlan> shapes = new HashMap<>();
        private final Constant nullConstant = new Constant(null);

        synchronized Constant literal(Object value) {
            if (value == null) {
                return nullConstant;
            }
            return literalConstants.computeIfAbsent(value, Constant::new);
        }

        synchronized Constant folded(Object value) {
            return foldedConstants.computeIfAbsent(value, Constant::new);
        }

        synchronized DynamicSlot slot(int slot) {
            return slots.computeIfAbsent(slot, DynamicSlot::new);
        }

        synchronized MapShape map(String[] keys, PropertyPlan[] values) {
            var key = new ShapeKey(1, keys, values);
            return (MapShape) shapes.computeIfAbsent(key, ignored -> new MapShape(keys, values));
        }

        synchronized ListShape list(PropertyPlan[] elements) {
            var key = new ShapeKey(2, null, elements);
            return (ListShape) shapes.computeIfAbsent(key, ignored -> new ListShape(elements));
        }

        synchronized StructShape struct(String[] keys, PropertyPlan[] values) {
            var key = new ShapeKey(3, keys, values);
            return (StructShape) shapes.computeIfAbsent(key, ignored -> new StructShape(keys, values));
        }
    }

    /** Structural key for array-backed shapes; record equality would compare the arrays by identity. */
    static final class ShapeKey {
        private final int kind;
        private final String[] keys;
        private final PropertyPlan[] values;
        private final int hash;

        ShapeKey(int kind, String[] keys, PropertyPlan[] values) {
            this.kind = kind;
            this.keys = keys;
            this.values = values;
            this.hash = 31 * (31 * kind + Arrays.hashCode(keys)) + Arrays.hashCode(values);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ShapeKey key
                    && kind == key.kind
                    && Arrays.equals(keys, key.keys)
                    && Arrays.equals(values, key.values);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    static Compiled compile(CompiledNode node, Interner interner) {
        var dynamics = new ArrayList<CompiledNode>();
        PropertyPlan plan = node == null ? interner.folded(Map.of()) : plan(node, dynamics, interner);
        return switch (dynamics.size()) {
            case 0 -> new Compiled0(plan, ((Constant) plan).value());
            case 1 -> new Compiled1(plan, dynamics.get(0));
            case 2 -> new Compiled2(plan, dynamics.get(0), dynamics.get(1));
            case 3 -> new Compiled3(plan, dynamics.get(0), dynamics.get(1), dynamics.get(2));
            default -> new CompiledN(plan, dynamics.toArray(new CompiledNode[0]));
        };
    }

    private static PropertyPlan plan(
            CompiledNode node,
            List<CompiledNode> dynamics,
            Interner interner
    ) {
        return switch (node) {
            case CompiledNode.Const n -> interner.literal(n.constantValue());
            case CompiledNode.Map1 n ->
                mapPlan(new String[] {n.key()}, new CompiledNode[] {n.value()}, dynamics, interner);
            case CompiledNode.Map2 n -> mapPlan(n.keys(), n.values(), dynamics, interner);
            case CompiledNode.MapN n -> mapPlan(n.keys(), n.values(), dynamics, interner);
            case CompiledNode.List1 n ->
                listPlan(new CompiledNode[] {n.element()}, dynamics, interner);
            case CompiledNode.List2 n -> listPlan(n.elements(), dynamics, interner);
            case CompiledNode.ListN n -> listPlan(n.elements(), dynamics, interner);
            case CompiledNode.StructNode n -> structPlan(n.keys(), n.values(), dynamics, interner);
            default -> {
                dynamics.add(node);
                yield interner.slot(dynamics.size() - 1);
            }
        };
    }

    private static PropertyPlan mapPlan(
            String[] keys,
            CompiledNode[] values,
            List<CompiledNode> dynamics,
            Interner interner
    ) {
        PropertyPlan[] plans = plans(values, dynamics, interner);
        Object[] constants = constants(plans);
        if (constants == null) {
            return interner.map(keys, plans);
        }
        if (keys.length == 1) {
            return interner.folded(Map.of(keys[0], constants[0]));
        } else if (keys.length == 2) {
            return interner.folded(Map.of(keys[0], constants[0], keys[1], constants[1]));
        }
        Map<String, Object> map = new HashMap<>(keys.length + 1, 1.0f);
        for (int i = 0; i < keys.length; i++) {
            map.put(keys[i], constants[i]);
        }
        return interner.folded(Collections.unmodifiableMap(map));
    }

    private static PropertyPlan listPlan(
            CompiledNode[] elements,
            List<CompiledNode> dynamics,
            Interner interner
    ) {
        PropertyPlan[] plans = plans(elements, dynamics, interner);
        Object[] constants = constants(plans);
        if (constants == null) {
            return interner.list(plans);
        }
        Object result = elements.length <= 2
                ? List.of(constants)
                : Collections.unmodifiableList(Arrays.asList(constants));
        return interner.folded(result);
    }

    private static PropertyPlan structPlan(
            String[] keys,
            CompiledNode[] values,
            List<CompiledNode> dynamics,
            Interner interner
    ) {
        PropertyPlan[] plans = plans(values, dynamics, interner);
        Object[] constants = constants(plans);
        if (constants == null) {
            return interner.struct(keys, plans);
        }
        var data = new Object[keys.length * 2];
        for (int i = 0; i < keys.length; i++) {
            data[i * 2] = constants[i];
            data[i * 2 + 1] = keys[i];
        }
        return interner.folded(new ArrayPropertyGetter(data));
    }

    private static PropertyPlan[] plans(
            CompiledNode[] nodes,
            List<CompiledNode> dynamics,
            Interner interner
    ) {
        var plans = new PropertyPlan[nodes.length];
        for (int i = 0; i < plans.length; i++) {
            plans[i] = plan(nodes[i], dynamics, interner);
        }
        return plans;
    }

    private static Object[] constants(PropertyPlan[] plans) {
        var values = new Object[plans.length];
        for (int i = 0; i < plans.length; i++) {
            if (!(plans[i] instanceof Constant constant)) {
                return null;
            }
            values[i] = constant.value();
        }
        return values;
    }
}
