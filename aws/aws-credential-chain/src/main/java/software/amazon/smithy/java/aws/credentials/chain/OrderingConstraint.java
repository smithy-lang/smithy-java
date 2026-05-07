/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

/**
 * Describes where an {@link AwsCredentialProvider} sits in the credential chain.
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
     * Positions a provider immediately before the named provider.
     *
     * @param provider the name of the provider this one must come before.
     */
    record Before(String provider) implements OrderingConstraint {
        public Before {
            if (provider == null || provider.isEmpty()) {
                throw new IllegalArgumentException("provider must not be null or empty");
            }
        }
    }

    /**
     * Positions a provider immediately after the named provider.
     *
     * @param provider the name of the provider this one must come after.
     */
    record After(String provider) implements OrderingConstraint {
        public After {
            if (provider == null || provider.isEmpty()) {
                throw new IllegalArgumentException("provider must not be null or empty");
            }
        }
    }
}
