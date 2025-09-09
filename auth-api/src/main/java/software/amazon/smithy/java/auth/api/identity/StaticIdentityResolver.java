/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.auth.api.identity;

import java.util.Objects;
import software.amazon.smithy.java.context.Context;

final class StaticIdentityResolver<IdentityT extends Identity> implements IdentityResolver<IdentityT> {

    private final IdentityT identity;
    private final IdentityResult<IdentityT> result;

    public StaticIdentityResolver(IdentityT identity) {
        this.identity = Objects.requireNonNull(identity);
        this.result = IdentityResult.of(identity);
    }

    @Override
    public IdentityResult<IdentityT> resolveIdentity(Context requestProperties) {
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<IdentityT> identityType() {
        return (Class<IdentityT>) identity.getClass();
    }
}
