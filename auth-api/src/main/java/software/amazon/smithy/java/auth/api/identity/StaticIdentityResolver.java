/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.auth.api.identity;

import java.util.Objects;
import software.amazon.smithy.java.context.Context;

final class StaticIdentityResolver<IdentityT extends Identity> implements IdentityResolver<IdentityT> {

    private final Class<IdentityT> identityType;
    private final IdentityResult<IdentityT> result;

    StaticIdentityResolver(IdentityT identity) {
        this(runtimeType(identity), identity);
    }

    StaticIdentityResolver(Class<IdentityT> identityType, IdentityT identity) {
        this.identityType = Objects.requireNonNull(identityType, "identityType is null");
        Objects.requireNonNull(identity, "identity is null");
        if (!identityType.isInstance(identity)) {
            throw new IllegalArgumentException(
                    "Identity of type " + identity.getClass().getName() + " is not an instance of "
                            + identityType.getName());
        }
        this.result = IdentityResult.of(identity);
    }

    @SuppressWarnings("unchecked")
    private static <I extends Identity> Class<I> runtimeType(I identity) {
        return (Class<I>) Objects.requireNonNull(identity, "identity is null").getClass();
    }

    @Override
    public IdentityResult<IdentityT> resolveIdentity(Context requestProperties) {
        return result;
    }

    @Override
    public Class<IdentityT> identityType() {
        return identityType;
    }
}
