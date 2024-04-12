/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api.identity;

import java.util.Objects;

/**
 * A token-based identity used to securely authorize requests to services that use token-based auth like OAuth.
 *
 * <p>For more details on OAuth tokens, see:
 * <a href="https://oauth.net/2/access-tokens">
 * https://oauth.net/2/access-tokens</a></p>
 */
public interface TokenIdentity extends Identity {
    /**
     * Retrieves string field representing the literal token string.
     *
     * @return the token.
     */
    String token();

    /**
     * Constructs a new token object, which can be used to authorize requests to services that use token-based auth.
     *
     * @param token The token used to authorize requests.
     */
    static TokenIdentity create(String token) {
        return new TokenIdentityRecord(Objects.requireNonNull(token, "token is null"));
    }
}
