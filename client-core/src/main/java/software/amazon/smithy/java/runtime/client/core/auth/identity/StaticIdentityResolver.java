/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.auth.identity;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.identity.Identity;

final class StaticIdentityResolver<IdentityT extends Identity> implements IdentityResolver<IdentityT> {
    private final IdentityT identity;

    public StaticIdentityResolver(IdentityT identity) {
        this.identity = Objects.requireNonNull(identity);
    }

    @Override
    public CompletableFuture<IdentityT> resolveIdentity(AuthProperties requestProperties) {
        return CompletableFuture.completedFuture(identity);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<IdentityT> identityType() {
        return (Class<IdentityT>) identity.getClass();
    }
}
