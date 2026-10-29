/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;

/**
 * Eagerly materializes the dynamic spine of an interned {@link PropertyPlan}.
 *
 * <p>Every dynamic container is created exactly once. Zero-through-three dynamic leaves stay in Java
 * locals; the materialized property graph itself contains only the map, list, and struct objects the
 * collection SPI requires.
 */
final class PropertyViews {

    private PropertyViews() {}

    static Object materialize(PropertyPlan plan, Object value0) {
        return materialize(plan, 0, value0, null, null, null);
    }

    static Object materialize(PropertyPlan plan, Object value0, Object value1) {
        return materialize(plan, 0, value0, value1, null, null);
    }

    static Object materialize(PropertyPlan plan, Object value0, Object value1, Object value2) {
        return materialize(plan, 0, value0, value1, value2, null);
    }

    static Object materialize(PropertyPlan plan, Object[] values) {
        return materialize(plan, 0, null, null, null, values);
    }

    static Object materialize(PropertyPlan plan, int slotOffset) {
        return materialize(plan, slotOffset, null, null, null, null);
    }

    static Object materialize(PropertyPlan plan, int slotOffset, Object value0) {
        return materialize(plan, slotOffset, value0, null, null, null);
    }

    static Object materialize(PropertyPlan plan, int slotOffset, Object value0, Object value1) {
        return materialize(plan, slotOffset, value0, value1, null, null);
    }

    static Object materialize(
            PropertyPlan plan,
            int slotOffset,
            Object value0,
            Object value1,
            Object value2
    ) {
        return materialize(plan, slotOffset, value0, value1, value2, null);
    }

    static Object materialize(PropertyPlan plan, int slotOffset, Object[] values) {
        return materialize(plan, slotOffset, null, null, null, values);
    }

    private static Object materialize(
            PropertyPlan plan,
            int slotOffset,
            Object value0,
            Object value1,
            Object value2,
            Object[] values
    ) {
        return switch (plan) {
            case PropertyPlan.Constant constant -> constant.value();
            case PropertyPlan.DynamicSlot slot -> switch (slotOffset + slot.slot()) {
                case 0 -> values == null ? value0 : values[0];
                case 1 -> values == null ? value1 : values[1];
                case 2 -> values == null ? value2 : values[2];
                default -> values[slotOffset + slot.slot()];
            };
            case PropertyPlan.MapShape map ->
                materializeMap(map, slotOffset, value0, value1, value2, values);
            case PropertyPlan.ListShape list ->
                materializeList(list, slotOffset, value0, value1, value2, values);
            case PropertyPlan.StructShape struct ->
                materializeStruct(struct, slotOffset, value0, value1, value2, values);
        };
    }

    private static Map<String, Object> materializeMap(
            PropertyPlan.MapShape shape,
            int slotOffset,
            Object value0,
            Object value1,
            Object value2,
            Object[] values
    ) {
        PropertyPlan[] plans = shape.values();
        return switch (plans.length) {
            case 1 -> new Map1Value(
                    shape,
                    materialize(plans[0], slotOffset, value0, value1, value2, values));
            case 2 -> new Map2Value(
                    shape,
                    materialize(plans[0], slotOffset, value0, value1, value2, values),
                    materialize(plans[1], slotOffset, value0, value1, value2, values));
            default -> {
                var result = new HashMap<String, Object>(plans.length + 1, 1.0f);
                String[] keys = shape.keys();
                for (int i = 0; i < plans.length; i++) {
                    result.put(
                            keys[i],
                            materialize(plans[i], slotOffset, value0, value1, value2, values));
                }
                yield result;
            }
        };
    }

    private static List<Object> materializeList(
            PropertyPlan.ListShape shape,
            int slotOffset,
            Object value0,
            Object value1,
            Object value2,
            Object[] values
    ) {
        PropertyPlan[] plans = shape.elements();
        return switch (plans.length) {
            case 1 -> new List1Value(
                    materialize(plans[0], slotOffset, value0, value1, value2, values));
            case 2 -> new List2Value(
                    materialize(plans[0], slotOffset, value0, value1, value2, values),
                    materialize(plans[1], slotOffset, value0, value1, value2, values));
            default -> {
                var result = new Object[plans.length];
                for (int i = 0; i < plans.length; i++) {
                    result[i] =
                            materialize(plans[i], slotOffset, value0, value1, value2, values);
                }
                yield Arrays.asList(result);
            }
        };
    }

    private static PropertyGetter materializeStruct(
            PropertyPlan.StructShape shape,
            int slotOffset,
            Object value0,
            Object value1,
            Object value2,
            Object[] values
    ) {
        PropertyPlan[] plans = shape.values();
        return switch (plans.length) {
            case 1 -> new Struct1Value(
                    shape,
                    materialize(plans[0], slotOffset, value0, value1, value2, values));
            case 2 -> new Struct2Value(
                    shape,
                    materialize(plans[0], slotOffset, value0, value1, value2, values),
                    materialize(plans[1], slotOffset, value0, value1, value2, values));
            case 3 -> new Struct3Value(
                    shape,
                    materialize(plans[0], slotOffset, value0, value1, value2, values),
                    materialize(plans[1], slotOffset, value0, value1, value2, values),
                    materialize(plans[2], slotOffset, value0, value1, value2, values));
            case 4 -> new Struct4Value(
                    shape,
                    materialize(plans[0], slotOffset, value0, value1, value2, values),
                    materialize(plans[1], slotOffset, value0, value1, value2, values),
                    materialize(plans[2], slotOffset, value0, value1, value2, values),
                    materialize(plans[3], slotOffset, value0, value1, value2, values));
            default -> {
                var data = new Object[plans.length * 2];
                String[] keys = shape.keys();
                for (int i = 0; i < plans.length; i++) {
                    data[i * 2] =
                            materialize(plans[i], slotOffset, value0, value1, value2, values);
                    data[i * 2 + 1] = keys[i];
                }
                yield new ArrayPropertyGetter(data);
            }
        };
    }

    private static final class Map1Value extends AbstractMap<String, Object> {
        private final PropertyPlan.MapShape shape;
        private final Object value0;

        Map1Value(PropertyPlan.MapShape shape, Object value0) {
            this.shape = shape;
            this.value0 = Objects.requireNonNull(value0);
        }

        @Override
        public Object get(Object key) {
            return shape.keys()[0].equals(Objects.requireNonNull(key)) ? value0 : null;
        }

        @Override
        public boolean containsKey(Object key) {
            return shape.keys()[0].equals(Objects.requireNonNull(key));
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            return new EntrySet(this);
        }

        @Override
        public int hashCode() {
            return shape.keys()[0].hashCode() ^ value0.hashCode();
        }
    }

    private static final class Map2Value extends AbstractMap<String, Object> {
        private final PropertyPlan.MapShape shape;
        private final Object value0;
        private final Object value1;

        Map2Value(PropertyPlan.MapShape shape, Object value0, Object value1) {
            this.shape = shape;
            this.value0 = Objects.requireNonNull(value0);
            this.value1 = Objects.requireNonNull(value1);
        }

        @Override
        public Object get(Object key) {
            Objects.requireNonNull(key);
            String[] keys = shape.keys();
            return keys[0].equals(key) ? value0 : keys[1].equals(key) ? value1 : null;
        }

        @Override
        public boolean containsKey(Object key) {
            Objects.requireNonNull(key);
            String[] keys = shape.keys();
            return keys[0].equals(key) || keys[1].equals(key);
        }

        @Override
        public int size() {
            return 2;
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            return new EntrySet(this);
        }

        @Override
        public int hashCode() {
            String[] keys = shape.keys();
            return (keys[0].hashCode() ^ value0.hashCode()) + (keys[1].hashCode() ^ value1.hashCode());
        }
    }

    /** Entry set used only by uncommon generic Map operations; direct get remains allocation-free. */
    private static final class EntrySet extends AbstractSet<Map.Entry<String, Object>> {
        private final Map<String, Object> map;

        EntrySet(Map<String, Object> map) {
            this.map = map;
        }

        @Override
        public Iterator<Map.Entry<String, Object>> iterator() {
            return new Iterator<>() {
                private int index;

                @Override
                public boolean hasNext() {
                    return index < map.size();
                }

                @Override
                public Map.Entry<String, Object> next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    int at = index++;
                    if (map instanceof Map1Value value) {
                        return Map.entry(value.shape.keys()[0], value.value0);
                    }
                    var value = (Map2Value) map;
                    return Map.entry(value.shape.keys()[at], at == 0 ? value.value0 : value.value1);
                }
            };
        }

        @Override
        public int size() {
            return map.size();
        }
    }

    private static final class List1Value extends AbstractList<Object> implements RandomAccess {
        private final Object value0;

        List1Value(Object value0) {
            this.value0 = Objects.requireNonNull(value0);
        }

        @Override
        public Object get(int index) {
            Objects.checkIndex(index, 1);
            return value0;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public int hashCode() {
            return 31 + value0.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof List<?> list
                            && list.size() == 1
                            && Objects.equals(value0, list.get(0));
        }
    }

    private static final class List2Value extends AbstractList<Object> implements RandomAccess {
        private final Object value0;
        private final Object value1;

        List2Value(Object value0, Object value1) {
            this.value0 = Objects.requireNonNull(value0);
            this.value1 = Objects.requireNonNull(value1);
        }

        @Override
        public Object get(int index) {
            return switch (Objects.checkIndex(index, 2)) {
                case 0 -> value0;
                default -> value1;
            };
        }

        @Override
        public int size() {
            return 2;
        }

        @Override
        public int hashCode() {
            return 31 * (31 + value0.hashCode()) + value1.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof List<?> list
                            && list.size() == 2
                            && Objects.equals(value0, list.get(0))
                            && Objects.equals(value1, list.get(1));
        }
    }

    private abstract static class StructValue implements PropertyGetter {
        final PropertyPlan.StructShape shape;

        StructValue(PropertyPlan.StructShape shape) {
            this.shape = shape;
        }

        abstract Object value(int index);

        @Override
        public Object getProperty(String name) {
            String[] keys = shape.keys();
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].equals(name)) {
                    return value(i);
                }
            }
            return null;
        }

        @Override
        public final boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof StructValue value)
                    || !Arrays.equals(shape.keys(), value.shape.keys())) {
                return false;
            }
            for (int i = 0; i < shape.keys().length; i++) {
                if (!Objects.equals(value(i), value.value(i))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public final int hashCode() {
            String[] keys = shape.keys();
            int result = 1;
            for (int i = 0; i < keys.length; i++) {
                result = 31 * result + keys[i].hashCode();
                result = 31 * result + Objects.hashCode(value(i));
            }
            return result;
        }
    }

    private static final class Struct1Value extends StructValue {
        private final Object value0;

        Struct1Value(PropertyPlan.StructShape shape, Object value0) {
            super(shape);
            this.value0 = value0;
        }

        @Override
        Object value(int index) {
            Objects.checkIndex(index, 1);
            return value0;
        }
    }

    private static final class Struct2Value extends StructValue {
        private final Object value0;
        private final Object value1;

        Struct2Value(PropertyPlan.StructShape shape, Object value0, Object value1) {
            super(shape);
            this.value0 = value0;
            this.value1 = value1;
        }

        @Override
        Object value(int index) {
            return switch (Objects.checkIndex(index, 2)) {
                case 0 -> value0;
                default -> value1;
            };
        }
    }

    private static final class Struct3Value extends StructValue {
        private final Object value0;
        private final Object value1;
        private final Object value2;

        Struct3Value(PropertyPlan.StructShape shape, Object value0, Object value1, Object value2) {
            super(shape);
            this.value0 = value0;
            this.value1 = value1;
            this.value2 = value2;
        }

        @Override
        Object value(int index) {
            return switch (Objects.checkIndex(index, 3)) {
                case 0 -> value0;
                case 1 -> value1;
                default -> value2;
            };
        }
    }

    private static final class Struct4Value extends StructValue {
        private final Object value0;
        private final Object value1;
        private final Object value2;
        private final Object value3;

        Struct4Value(
                PropertyPlan.StructShape shape,
                Object value0,
                Object value1,
                Object value2,
                Object value3
        ) {
            super(shape);
            this.value0 = value0;
            this.value1 = value1;
            this.value2 = value2;
            this.value3 = value3;
        }

        @Override
        Object value(int index) {
            return switch (Objects.checkIndex(index, 4)) {
                case 0 -> value0;
                case 1 -> value1;
                case 2 -> value2;
                default -> value3;
            };
        }
    }
}
