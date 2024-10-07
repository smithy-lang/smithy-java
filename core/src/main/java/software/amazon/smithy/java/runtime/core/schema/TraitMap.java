/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.HashMap;
import java.util.Map;
import software.amazon.smithy.model.traits.Trait;

/**
 * Provides Trait class-based access to traits.
 *
 * <p>TODO: Add enum based getters for prelude traits.
 */
sealed interface TraitMap {

    static TraitMap create(Trait[] traits) {
        if (traits == null) {
            return EMPTY;
        }
        return switch (traits.length) {
            case 0 -> EMPTY;
            case 1 -> new SingleValueTraitMap(traits[0]);
            case 2, 3, 4, 5 -> new ArrayBasedTraitMap(traits);
            default -> new MapBasedTraitMap(traits);
        };
    }

    TraitMap EMPTY = new EmptyTraitMap();

    <T extends Trait> T get(Class<T> trait);

    boolean isEmpty();

    boolean contains(Class<? extends Trait> trait);

    TraitMap prepend(Trait[] traits);

    final class EmptyTraitMap implements TraitMap {
        @Override
        public boolean contains(Class<? extends Trait> trait) {
            return false;
        }

        @Override
        public <T extends Trait> T get(Class<T> trait) {
            return null;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public TraitMap prepend(Trait[] traits) {
            if (traits.length == 0) {
                return this;
            } else {
                return TraitMap.create(traits);
            }
        }
    }

    final class SingleValueTraitMap implements TraitMap {

        private final Trait value;

        private SingleValueTraitMap(Trait value) {
            this.value = value;
        }

        @Override
        public boolean contains(Class<? extends Trait> trait) {
            return trait == value.getClass();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends Trait> T get(Class<T> trait) {
            return trait == value.getClass() ? (T) value : null;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public TraitMap prepend(Trait[] traits) {
            if (traits.length == 0) {
                return this;
            } else {
                Trait[] result = new Trait[traits.length + 1];
                System.arraycopy(traits, 0, result, 0, traits.length);
                result[traits.length] = value;
                return TraitMap.create(result);
            }
        }
    }

    final class ArrayBasedTraitMap implements TraitMap {

        private final Object[] types;
        private final Object[] values;

        private ArrayBasedTraitMap(Trait[] t) {
            types = new Object[t.length];
            values = new Object[t.length];
            for (int i = 0; i < t.length; i++) {
                var v = t[i];
                types[i] = v.getClass();
                values[i] = v;
            }
        }

        @Override
        public boolean contains(Class<? extends Trait> trait) {
            for (Object type : types) {
                if (type == trait) {
                    return true;
                }
            }
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Trait> T get(Class<T> trait) {
            for (int i = 0; i < types.length; i++) {
                if (types[i] == trait) {
                    return (T) values[i];
                }
            }
            return null;
        }

        @Override
        public boolean isEmpty() {
            return values.length == 0;
        }

        @Override
        public TraitMap prepend(Trait[] traits) {
            if (traits.length == 0) {
                return this;
            } else if (values.length == 0) {
                return TraitMap.create(traits);
            } else {
                Trait[] result = new Trait[traits.length + this.values.length];
                System.arraycopy(traits, 0, result, 0, traits.length);
                System.arraycopy(this.values, 0, result, traits.length, this.values.length);
                return TraitMap.create(result);
            }
        }
    }

    final class MapBasedTraitMap implements TraitMap {

        private final Map<Class<?>, Trait> map;

        private MapBasedTraitMap(Trait[] traits) {
            this(new HashMap<>(traits.length));
            for (var t : traits) {
                this.map.put(t.getClass(), t);
            }
        }

        private MapBasedTraitMap(Map<Class<?>, Trait> map) {
            this.map = map;
        }

        @Override
        public boolean contains(Class<? extends Trait> trait) {
            return map.containsKey(trait);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Trait> T get(Class<T> trait) {
            return (T) map.get(trait);
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public TraitMap prepend(Trait[] traits) {
            if (traits.length == 0) {
                return this;
            } else {
                Map<Class<?>, Trait> result = new HashMap<>(map.size() + traits.length);
                result.putAll(map);
                for (var t : traits) {
                    result.put(t.getClass(), t);
                }
                return new MapBasedTraitMap(result);
            }
        }
    }
}
