/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import software.amazon.smithy.model.traits.Trait;

/**
 * Provides Trait class-based access to traits.
 */
final class TraitMap {

    private static final Trait[] NO_TRAITS = new Trait[0];
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final ClassValue<Integer> ID = new ClassValue<>() {
        @Override
        protected Integer computeValue(Class<?> ignore) {
            return COUNTER.getAndIncrement();
        }
    };

    private final Trait[] values;

    private TraitMap(Trait[] traits, boolean initialized) {
        if (initialized) {
            // The `traits` array is an already allocated index-based trait array.
            this.values = traits;
        } else if (traits == null || traits.length == 0) {
            this.values = NO_TRAITS;
        } else {
            // The `traits` array is just an array of Traits. We need to ensure an ID is assigned to each trait.
            // Since we're already doing a pass over the traits, we can also allocate exact-sized storage.
            int largestId = 0;
            for (Trait trait : traits) {
                largestId = Math.max(largestId, ID.get(trait.getClass()));
            }
            this.values = new Trait[largestId + 1];
            for (Trait trait : traits) {
                values[ID.get(trait.getClass())] = trait;
            }
        }
    }

    static TraitMap create(Trait[] traits) {
        return new TraitMap(traits, false);
    }

    @SuppressWarnings("unchecked")
    <T extends Trait> T get(Class<T> trait) {
        int idx = ID.get(trait);
        if (idx >= values.length) {
            return null;
        }
        return (T) values[idx];
    }

    boolean isEmpty() {
        return values.length == 0;
    }

    boolean contains(Class<? extends Trait> trait) {
        int idx = ID.get(trait);
        return idx < values.length && values[idx] != null;
    }

    TraitMap prepend(Trait[] traits) {
        // Allocate only enough storage required to hold the current traits and given traits.
        int largestId = values.length - 1;

        // Ensure traits have a resolved class ID.
        for (Trait trait : traits) {
            largestId = Math.max(largestId, ID.get(trait.getClass()));
        }

        var values = Arrays.copyOf(this.values, largestId + 1);

        // Overwrite the current values with passed in traits.
        for (Trait trait : traits) {
            values[ID.get(trait.getClass())] = trait;
        }

        return new TraitMap(values, true);
    }
}
