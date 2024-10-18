/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import software.amazon.smithy.model.traits.Trait;

/**
 * Provides Trait class-based access to traits.
 */
final class TraitMap {

    private static final TraitMap EMPTY = new TraitMap(new Trait[0], Integer.MAX_VALUE, Integer.MIN_VALUE);

    private final Trait[] values;
    private final int minIndex;
    private final int maxIndex;

    private TraitMap(Trait[] values, int minIndex, int maxIndex) {
        this.values = values;
        this.minIndex = minIndex;
        this.maxIndex = maxIndex;
    }

    private TraitMap(Trait[] traits) {
        assert traits.length > 0;

        // The `traits` array is just an array of Traits. We need to ensure an ID is assigned to each trait.
        // Since we're already doing a pass over the traits, we can also allocate exact-sized storage.
        int smallestId = Integer.MAX_VALUE;
        int largestId = Integer.MIN_VALUE;
        for (Trait trait : traits) {
            var id = TraitKey.get(trait.getClass()).id();
            smallestId = Math.min(smallestId, id);
            largestId = Math.max(largestId, id);
        }

        this.minIndex = smallestId;
        this.maxIndex = largestId;
        this.values = new Trait[(largestId - smallestId) + 1];

        for (Trait trait : traits) {
            var key = TraitKey.get(trait.getClass());
            values[key.id() - minIndex] = trait;
        }
    }

    static TraitMap create(Trait[] traits) {
        if (traits == null || traits.length == 0) {
            return EMPTY;
        } else {
            return new TraitMap(traits);
        }
    }

    @SuppressWarnings("unchecked")
    <T extends Trait> T get(TraitKey<T> key) {
        int idx = key.id();
        if (idx < minIndex || idx > maxIndex) {
            return null;
        }
        return (T) values[idx - minIndex];
    }

    boolean isEmpty() {
        return values.length == 0;
    }

    boolean contains(TraitKey<? extends Trait> trait) {
        return get(trait) != null;
    }

    TraitMap prepend(Trait[] traits) {
        if (traits == null || traits.length == 0) {
            return this;
        } else if (this.values.length == 0) {
            return new TraitMap(traits);
        }

        int smallestId = this.minIndex;
        int largestId = this.maxIndex;
        for (Trait trait : traits) {
            var id = TraitKey.get(trait.getClass()).id();
            smallestId = Math.min(smallestId, id);
            largestId = Math.max(largestId, id);
        }

        // Allocate new storage based on the expanded bounds.
        var newValues = new Trait[(largestId - smallestId) + 1];

        // Copy existing values from the current map, adjusting for the new bounds.
        System.arraycopy(this.values, 0, newValues, Math.max(0, this.minIndex - smallestId), this.values.length);

        // Overwrite the current values with the new traits.
        for (Trait trait : traits) {
            newValues[TraitKey.get(trait.getClass()).id() - smallestId] = trait;
        }

        return new TraitMap(newValues, smallestId, largestId);
    }
}
