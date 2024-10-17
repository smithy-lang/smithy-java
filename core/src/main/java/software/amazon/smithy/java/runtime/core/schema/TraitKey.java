/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.concurrent.atomic.AtomicInteger;
import software.amazon.smithy.model.traits.Trait;

/**
 * Identity-based access to a specific trait used with methods like {@link Schema#getTrait}.
 *
 * @param <T> Trait to access with the key.
 */
public final class TraitKey<T extends Trait> {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final ClassValue<TraitKey<? extends Trait>> KEY_POOL = new ClassValue<>() {
        @Override
        @SuppressWarnings("unchecked")
        protected TraitKey<?> computeValue(Class<?> clazz) {
            return new TraitKey<>((Class<Trait>) clazz, COUNTER.getAndIncrement());
        }
    };

    private final Class<T> traitClass;
    private final int id;

    /**
     * Gets the key for a trait for use with methods like {@link Schema#getTrait}.
     *
     * @param traitClass Trait to get the key of.
     * @return the key for the trait.
     * @param <T> Trait class.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Trait> TraitKey<T> get(Class<T> traitClass) {
        return (TraitKey<T>) KEY_POOL.get(traitClass);
    }

    /**
     * Give an array of traits, ensures each has an assigned key and finds the largest trait index from each trait.
     *
     * @param traits Traits to check.
     * @return the largest required index.
     */
    public static int getLargestTraitId(Trait[] traits) {
        int largestId = 0;
        for (Trait trait : traits) {
            largestId = Math.max(largestId, get(trait.getClass()).id());
        }
        return largestId;
    }

    private TraitKey(Class<T> traitClass, int id) {
        this.traitClass = traitClass;
        this.id = id;
    }

    /**
     * Get the class this trait key wraps.
     *
     * @return the class of the trait provided by this key.
     */
    public Class<T> traitClass() {
        return traitClass;
    }

    int id() {
        return id;
    }
}
