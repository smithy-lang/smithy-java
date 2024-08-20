/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api.scheme;

import java.util.Objects;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;

/**
 * An authentication scheme option, composed of the scheme ID and property overrides for use when resolving the
 * identity and signing the request.
 *
 * <p>This is used in the output from the {@link AuthSchemeResolver}. The resolver returns a list of these, in the order
 * the auth scheme resolver wishes to use them.
 *
 * @param schemeId The authentication scheme ID, a unique identifier for the authentication scheme
 *                 (aws.auth#sigv4, smithy.api#httpBearerAuth).
 * @param identityPropertyOverrides The identity property overrides.
 * @param signerPropertyOverrides The signer property overrides.
 *
 * @see AuthScheme
 */
public record AuthSchemeOption(
    String schemeId,
    AuthProperties identityPropertyOverrides,
    AuthProperties signerPropertyOverrides
) {
    public AuthSchemeOption {
        Objects.requireNonNull(schemeId, "schemeId cannot be null.");
        Objects.requireNonNull(identityPropertyOverrides, "identityPropertyOverrides cannot be null.");
        Objects.requireNonNull(signerPropertyOverrides, "signerPropertyOverrides cannot be null.");
    }

    /**
     * Creates an {@code AuthSchemeOption} for a given auth scheme ID.
     *
     * <p><strong>NOTE:</strong>The resulting AuthSchemeOption will have no
     * identity or signer property overrides.
     *
     * @param schemeId id of auth scheme to create an option for.
     * @return AuthSchemeOption instance with no identity or signer property overrides.
     */
    public static AuthSchemeOption forId(String schemeId) {
        return new AuthSchemeOption(schemeId, AuthProperties.empty(), AuthProperties.empty());
    }
}
