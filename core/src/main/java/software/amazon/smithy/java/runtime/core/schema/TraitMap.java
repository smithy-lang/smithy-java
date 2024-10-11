/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.IdentityHashMap;
import java.util.Map;
import software.amazon.smithy.model.traits.Trait;

/**
 * Provides Trait class-based access to traits.
 */
sealed interface TraitMap {

    static TraitMap create(Trait[] traits) {
        // Most schemas have 0-3 traits, and very few schemas have more than 5.
        return switch (traits == null ? 0 : traits.length) {
            case 0 -> EMPTY;
            case 1 -> new TraitMap1(traits[0]);
            case 2 -> new TraitMap2(traits[0], traits[1]);
            case 3 -> new TraitMap3(traits[0], traits[1], traits[2]);
            case 4 -> new TraitMap4(traits[0], traits[1], traits[2], traits[3]);
            case 5 -> new TraitMap5(traits[0], traits[1], traits[2], traits[3], traits[4]);
            default -> new MapBasedTraitMap(traits);
        };
    }

    TraitMap EMPTY = new TraitMap0();

    <T extends Trait> T get(Class<T> trait);

    boolean isEmpty();

    boolean contains(Class<? extends Trait> trait);

    TraitMap prepend(Trait[] traits);

    final class TraitMap0 implements TraitMap {
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
            return TraitMap.create(traits);
        }
    }

    final class TraitMap1 implements TraitMap {

        private final Trait value;
        private final Class<?> c1;

        private TraitMap1(Trait value) {
            this.value = value;
            this.c1 = value.getClass();
        }

        @Override
        public boolean contains(Class<? extends Trait> trait) {
            return trait == c1;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends Trait> T get(Class<T> trait) {
            return trait == c1 ? (T) value : null;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public TraitMap prepend(Trait[] traits) {
            Trait[] result = copyArray(traits, 1);
            result[traits.length] = value;
            return TraitMap.create(result);
        }
    }

    private static Trait[] copyArray(Trait[] traits, int addSize) {
        Trait[] result = new Trait[traits.length + addSize];
        System.arraycopy(traits, 0, result, 0, traits.length);
        return result;
    }

    final class TraitMap2 implements TraitMap {

        private final Trait value1;
        private final Trait value2;
        private final Class<?> c1;
        private final Class<?> c2;

        private TraitMap2(Trait value1, Trait value2) {
            this.value1 = value1;
            this.value2 = value2;
            this.c1 = value1.getClass();
            this.c2 = value2.getClass();
        }

        @Override
        public boolean contains(Class<? extends Trait> trait) {
            return trait == c1 || trait == c2;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends Trait> T get(Class<T> trait) {
            if (trait == c1) {
                return (T) value1;
            } else if (trait == c2) {
                return (T) value2;
            } else {
                return null;
            }
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public TraitMap prepend(Trait[] traits) {
            Trait[] result = copyArray(traits, 2);
            result[traits.length] = value1;
            result[traits.length + 1] = value2;
            return TraitMap.create(result);
        }
    }

    final class TraitMap3 implements TraitMap {

        private final Trait value1;
        private final Trait value2;
        private final Trait value3;
        private final Class<?> c1;
        private final Class<?> c2;
        private final Class<?> c3;

        private TraitMap3(Trait value1, Trait value2, Trait value3) {
            this.value1 = value1;
            this.value2 = value2;
            this.value3 = value3;
            this.c1 = value1.getClass();
            this.c2 = value2.getClass();
            this.c3 = value3.getClass();
        }

        @Override
        public boolean contains(Class<? extends Trait> trait) {
            return trait == c1 || trait == c2 || trait == c3;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends Trait> T get(Class<T> trait) {
            if (trait == c1) {
                return (T) value1;
            } else if (trait == c2) {
                return (T) value2;
            } else if (trait == c3) {
                return (T) value3;
            } else {
                return null;
            }
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public TraitMap prepend(Trait[] traits) {
            Trait[] result = copyArray(traits, 3);
            result[traits.length] = value1;
            result[traits.length + 1] = value2;
            result[traits.length + 2] = value3;
            return TraitMap.create(result);
        }
    }

    final class TraitMap4 implements TraitMap {

        private final Trait value1;
        private final Trait value2;
        private final Trait value3;
        private final Trait value4;
        private final Class<?> c1;
        private final Class<?> c2;
        private final Class<?> c3;
        private final Class<?> c4;

        private TraitMap4(Trait value1, Trait value2, Trait value3, Trait value4) {
            this.value1 = value1;
            this.value2 = value2;
            this.value3 = value3;
            this.value4 = value4;
            this.c1 = value1.getClass();
            this.c2 = value2.getClass();
            this.c3 = value3.getClass();
            this.c4 = value4.getClass();
        }

        @Override
        public boolean contains(Class<? extends Trait> trait) {
            return trait == c1 || trait == c2 || trait == c3 || trait == c4;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends Trait> T get(Class<T> trait) {
            if (trait == c1) {
                return (T) value1;
            } else if (trait == c2) {
                return (T) value2;
            } else if (trait == c3) {
                return (T) value3;
            } else if (trait == c4) {
                return (T) value4;
            } else {
                return null;
            }
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public TraitMap prepend(Trait[] traits) {
            Trait[] result = copyArray(traits, 4);
            result[traits.length] = value1;
            result[traits.length + 1] = value2;
            result[traits.length + 2] = value3;
            result[traits.length + 3] = value4;
            return TraitMap.create(result);
        }
    }

    final class TraitMap5 implements TraitMap {

        private final Trait value1;
        private final Trait value2;
        private final Trait value3;
        private final Trait value4;
        private final Trait value5;
        private final Class<?> c1;
        private final Class<?> c2;
        private final Class<?> c3;
        private final Class<?> c4;
        private final Class<?> c5;

        private TraitMap5(Trait value1, Trait value2, Trait value3, Trait value4, Trait value5) {
            this.value1 = value1;
            this.value2 = value2;
            this.value3 = value3;
            this.value4 = value4;
            this.value5 = value5;
            this.c1 = value1.getClass();
            this.c2 = value2.getClass();
            this.c3 = value3.getClass();
            this.c4 = value4.getClass();
            this.c5 = value5.getClass();
        }

        @Override
        public boolean contains(Class<? extends Trait> trait) {
            return trait == c1 || trait == c2 || trait == c3 || trait == c4 || trait == c5;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends Trait> T get(Class<T> trait) {
            if (trait == c1) {
                return (T) value1;
            } else if (trait == c2) {
                return (T) value2;
            } else if (trait == c3) {
                return (T) value3;
            } else if (trait == c4) {
                return (T) value4;
            } else if (trait == c5) {
                return (T) value5;
            } else {
                return null;
            }
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public TraitMap prepend(Trait[] traits) {
            Trait[] result = copyArray(traits, 5);
            result[traits.length] = value1;
            result[traits.length + 1] = value2;
            result[traits.length + 2] = value3;
            result[traits.length + 3] = value4;
            result[traits.length + 4] = value5;
            return TraitMap.create(result);
        }
    }

    final class MapBasedTraitMap implements TraitMap {

        private final Map<Class<?>, Trait> map;

        private MapBasedTraitMap(Trait[] traits) {
            this(new IdentityHashMap<>(traits.length));
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
            Map<Class<?>, Trait> result = new IdentityHashMap<>(map.size() + traits.length);
            result.putAll(map);
            for (var t : traits) {
                result.put(t.getClass(), t);
            }
            return new MapBasedTraitMap(result);
        }
    }
}
