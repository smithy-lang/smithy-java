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

    private static final TraitMap EMPTY = new TraitMap(new Trait[0]);
    private final Trait[] values;

    private TraitMap(Trait[] values) {
        this.values = values;
    }

    static TraitMap create(Trait... traits) {
        if (traits.length == 0) {
            return EMPTY;
        }

        // The `traits` array is just an array of Traits. We need to ensure an ID is assigned to each trait.
        // Since we're already doing a pass over the traits, we can also allocate exact-sized storage.
        var values = new Trait[getLargestTraitId(traits) + 1];
        for (Trait trait : traits) {
            values[TraitKey.get(trait.getClass()).id] = trait;
        }

        return new TraitMap(values);
    }

    private static int getLargestTraitId(Trait[] traits) {
        int largestId = 0;
        for (Trait trait : traits) {
            largestId = Math.max(largestId, TraitKey.get(trait.getClass()).id);
        }
        return largestId;
    }

    @SuppressWarnings("unchecked")
    <T extends Trait> T get(TraitKey<T> key) {
        var idx = key.id;
        return idx < values.length ? (T) values[idx] : null;
    }

    boolean isEmpty() {
        return values.length == 0;
    }

    boolean contains(TraitKey<? extends Trait> key) {
        return get(key) != null;
    }

    TraitMap withMemberTraits(TraitMap memberTraits) {
        if (memberTraits == this || memberTraits.isEmpty()) {
            return this;
        } else if (isEmpty()) {
            return memberTraits;
        } else {
            int newSize = Math.max(this.values.length, memberTraits.values.length);
            var newValues = Arrays.copyOf(this.values, newSize);
            for (var i = 0; i < memberTraits.values.length; i++) {
                var v = memberTraits.values[i];
                if (v != null) {
                    newValues[i] = memberTraits.values[i];
                }
            }
            return new TraitMap(newValues);
        }
    }
}
