/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api.scheme;

import java.util.List;

/**
 * Resolves the authentication scheme that should be used to sign a request.
 */
@FunctionalInterface
public interface AuthSchemeResolver {
    /**
     * Resolve the auth scheme options using the given parameters.
     *
     * <p>The returned list of options is priority ordered. Clients should use the first option they support in the
     * returned list.
     *
     * @param params The parameters used to resolve the auth scheme.
     * @return the resolved auth scheme options.
     */
    List<AuthSchemeOption> resolveAuthScheme(AuthSchemeResolverParams params);
}
