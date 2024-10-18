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

    // The empty map has a length of 1 for null padding to allow for unconditional array indexing.
    private static final TraitMap EMPTY = new TraitMap(new Trait[1], Integer.MAX_VALUE, Integer.MIN_VALUE);

    private final Trait[] values;

    // minIndex and maxIndex are the actually observed minimum Key indices and are not adjusted for padding.
    private final int minIndex;
    private final int maxIndex;

    // Cache these precomputed values for a slight performance boost in get().
    private final int adjustedMinIndex;
    private final int adjustedMaxLength;

    private TraitMap(Trait[] values, int minIndex, int maxIndex) {
        this.values = values;
        this.minIndex = minIndex;
        this.maxIndex = maxIndex;
        this.adjustedMinIndex = minIndex - 1;
        this.adjustedMaxLength = values.length - 1;
    }

    static TraitMap create(Trait... traits) {
        return traits.length == 0 ? EMPTY : createFromTraits(traits);
    }

    private static TraitMap createFromTraits(Trait... traits) {
        assert traits.length > 0;

        // Ensure each trait is resolved to a key and find the largest and smallest key indices to store in the map.
        int smallestId = Integer.MAX_VALUE;
        int largestId = Integer.MIN_VALUE;
        for (Trait trait : traits) {
            var id = TraitKey.get(trait.getClass()).id;
            smallestId = Math.min(smallestId, id);
            largestId = Math.max(largestId, id);
        }

        // Allocate space for values with 2 padding slots: 1 at the start and one at the end.
        var values = new Trait[(largestId - smallestId) + 3];

        // Insert traits into the correct positions
        for (Trait trait : traits) {
            var key = TraitKey.get(trait.getClass());
            values[(key.id - smallestId) + 1] = trait; // +1 to account for the null padding
        }

        return new TraitMap(values, smallestId, largestId);
    }

    @SuppressWarnings("unchecked")
    <T extends Trait> T get(TraitKey<T> key) {
        // Clamp the index so that if the index is out of bounds, it will either land at the beginning or end of the
        // array and correctly return a null value.
        return (T) values[Math.max(0, Math.min(key.id - adjustedMinIndex, adjustedMaxLength))];
    }

    boolean isEmpty() {
        // The only empty TraitMap that can be created is TraitMap#EMPTY, which sets maxIndex to Integer.MIN_VALUE.
        return maxIndex == Integer.MIN_VALUE;
    }

    boolean contains(TraitKey<? extends Trait> trait) {
        return get(trait) != null;
    }

    TraitMap prepend(Trait... traits) {
        if (traits.length == 0) {
            return this;
        } else if (isEmpty()) {
            return createFromTraits(traits);
        }

        int smallestId = this.minIndex;
        int largestId = this.maxIndex;
        for (Trait trait : traits) {
            var id = TraitKey.get(trait.getClass()).id;
            smallestId = Math.min(smallestId, id);
            largestId = Math.max(largestId, id);
        }

        // Allocate new storage with the leading and trailing null slot padding.
        var newValues = new Trait[(largestId - smallestId) + 3];

        // Copy existing values into the new array, adjusted for new bounds and null padding.
        System.arraycopy(
            this.values,
            1,
            newValues,
            Math.max(1, this.minIndex - smallestId + 1),
            this.values.length - 2
        );

        // Insert new traits into the correct positions
        for (Trait trait : traits) {
            int id = TraitKey.get(trait.getClass()).id;
            newValues[(id - smallestId) + 1] = trait; // +1 for padding
        }

        return new TraitMap(newValues, smallestId, largestId);
    }
}
