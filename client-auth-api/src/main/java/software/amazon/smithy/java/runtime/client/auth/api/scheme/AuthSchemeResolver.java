/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.auth.api.scheme;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the authentication scheme that should be used to sign a request.
 */
@FunctionalInterface
public interface AuthSchemeResolver {
    /**
     * Default auth scheme resolver used by clients.
     *
     * <p>This resolver always returns a list of auth scheme options consisting of
     * the effective auth schemes for the operation being called. No signer or identity property
     * overrides are provided by the {@link AuthSchemeOption}'s returned by this resolver.
     *
     * @see <a href="https://smithy.io/2.0/spec/authentication-traits.html#smithy-api-auth-trait">Smithy auth trait</a>
     * for more information on how the effective auth schemes for an operation are resolved.
     */
    AuthSchemeResolver DEFAULT = new DefaultAuthSchemeResolver();
    /**
     * Auth scheme resolver that returns only the {@code NoAuth} auth scheme.
     *
     * <p>This resolver can be used to bypass auth logic when testing clients.
     */
    AuthSchemeResolver NO_AUTH = (param) -> List.of(new AuthSchemeOption(NoAuthAuthScheme.INSTANCE.schemeId()));

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

    /**
     * Default auth scheme resolver used by clients.
     *
     * <p>This auth scheme resolver returns the list of {@link AuthSchemeResolverParams#operationAuthSchemes()} passed
     * into the resolver as a list of auth scheme options. This list of auth schemes represents the effective
     * auth schemes resolved for a given operation.
     *
     * <p><strong>NOTE:</strong></string>This resolver does not override any signer or identity properties.
     *
     * @see <a href="https://smithy.io/2.0/spec/authentication-traits.html#smithy-api-auth-trait">Smithy auth trait</a>
     * for more information on how the effective auth schemes for an operation are resolved.
     */
    final class DefaultAuthSchemeResolver implements AuthSchemeResolver {

        @Override
        public List<AuthSchemeOption> resolveAuthScheme(AuthSchemeResolverParams params) {
            var result = new ArrayList<AuthSchemeOption>();
            for (var schemeId : params.operationAuthSchemes()) {
                result.add(new AuthSchemeOption(schemeId));
            }
            return result;
        }
    }
}
