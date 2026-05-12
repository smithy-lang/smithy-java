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
 *   <li>{@link Builtin} — claims a builtin slot. At most one provider may claim each slot;
 *       a conflict at assembly time is a fatal error.</li>
 *   <li>{@link Before} — positions the provider immediately before a builtin slot.</li>
 *   <li>{@link After} — positions the provider immediately after a builtin slot.</li>
 * </ul>
 *
 * <p>{@link Before} and {@link After} reference {@link BuiltinProvider} enum values only, not
 * arbitrary provider names. This eliminates the possibility of cycles in ordering constraints.
 */
public sealed interface OrderingConstraint {
    /**
     * Claims a builtin slot in the default chain. Only one provider may claim each slot.
     *
     * @param slot the builtin slot to claim.
     */
    record Builtin(BuiltinProvider slot) implements OrderingConstraint {
        public Builtin {
            if (slot == null) {
                throw new IllegalArgumentException("slot must not be null");
            }
        }
    }

    /**
     * Positions a provider immediately before the given builtin slot.
     *
     * @param slot the builtin slot this provider must come before.
     */
    record Before(BuiltinProvider slot) implements OrderingConstraint {
        public Before {
            if (slot == null) {
                throw new IllegalArgumentException("slot must not be null");
            }
        }
    }

    /**
     * Positions a provider immediately after the given builtin slot.
     *
     * @param slot the builtin slot this provider must come after.
     */
    record After(BuiltinProvider slot) implements OrderingConstraint {
        public After {
            if (slot == null) {
                throw new IllegalArgumentException("slot must not be null");
            }
        }
    }
}
