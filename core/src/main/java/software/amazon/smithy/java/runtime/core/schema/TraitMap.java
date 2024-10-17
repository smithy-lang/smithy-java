/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.Arrays;
import software.amazon.smithy.model.traits.Trait;

/**
 * Provides Trait class-based access to traits.
 */
final class TraitMap {

    private static final Trait[] NO_TRAITS = new Trait[0];
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
            this.values = new Trait[TraitKey.getLargestTraitId(traits) + 1];
            for (Trait trait : traits) {
                values[TraitKey.get(trait.getClass()).id()] = trait;
            }
        }
    }

    static TraitMap create(Trait[] traits) {
        return new TraitMap(traits, false);
    }

    @SuppressWarnings("unchecked")
    <T extends Trait> T get(TraitKey<T> key) {
        int idx = key.id();
        if (idx >= values.length) {
            return null;
        }
        return (T) values[idx];
    }

    boolean isEmpty() {
        return values.length == 0;
    }

    boolean contains(TraitKey<? extends Trait> trait) {
        int idx = trait.id();
        return idx < values.length && values[idx] != null;
    }

    TraitMap prepend(Trait[] traits) {
        // Allocate only enough storage required to hold the current traits and given traits.
        int largestId = Math.max(values.length - 1, TraitKey.getLargestTraitId(traits));

        var values = Arrays.copyOf(this.values, largestId + 1);

        // Overwrite the current values with passed in traits.
        for (Trait trait : traits) {
            values[TraitKey.get(trait.getClass()).id()] = trait;
        }

        return new TraitMap(values, true);
    }
}
