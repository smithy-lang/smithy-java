/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;

/**
 * A chain provider that can also create a resolver when referenced by canonical name.
 *
 * <p>Named resolution is independent of top-level chain participation. For example, an explicitly selected
 * profile suppresses the top-level Environment provider, while {@code credential_source=Environment} can still
 * request an environment resolver as the source for an assume-role provider.
 */
public interface SourceIdentityProvider extends ChainIdentityProvider {
    /**
     * Creates a resolver for use as another provider's source.
     *
     * <p>This method is called only during chain assembly. Implementations must not retain the mutable
     * {@link ChainSetup}.
     *
     * @param identityType identity type to resolve.
     * @param setup current assembly context.
     * @return the resolver, or {@code null} when this provider cannot resolve the requested type or is disabled.
     */
    IdentityResolver<?> createResolver(Class<? extends Identity> identityType, ChainSetup setup);
}
