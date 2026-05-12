/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

/**
 * Describes where a {@link ChainIdentityProvider} sits in the credential chain.
 *
 * <p>Three forms:
 * <ul>
 *   <li>{@link Standard} — claims a standard slot. At most one provider may claim each slot;
 *       a conflict at assembly time is a fatal error.</li>
 *   <li>{@link Before} — positions the provider immediately before a standard slot.</li>
 *   <li>{@link After} — positions the provider immediately after a standard slot.</li>
 * </ul>
 *
 * <p>{@link Before} and {@link After} reference {@link StandardProvider} enum values only, not
 * arbitrary provider names. This eliminates the possibility of cycles in ordering constraints.
 */
public sealed interface OrderingConstraint {
    /**
     * Claims a standard slot in the default chain. Only one provider may claim each slot.
     *
     * @param slot the standard slot to claim.
     */
    record Standard(StandardProvider slot) implements OrderingConstraint {
        public Standard {
            if (slot == null) {
                throw new IllegalArgumentException("slot must not be null");
            }
        }
    }

    /**
     * Positions a provider immediately before the given standard slot.
     *
     * @param slot the standard slot this provider must come before.
     */
    record Before(StandardProvider slot) implements OrderingConstraint {
        public Before {
            if (slot == null) {
                throw new IllegalArgumentException("slot must not be null");
            }
        }
    }

    /**
     * Positions a provider immediately after the given standard slot.
     *
     * @param slot the standard slot this provider must come after.
     */
    record After(StandardProvider slot) implements OrderingConstraint {
        public After {
            if (slot == null) {
                throw new IllegalArgumentException("slot must not be null");
            }
        }
    }
}
