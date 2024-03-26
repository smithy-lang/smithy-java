/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api.identity;

import java.util.Objects;

/**
 * Username and password-based credentials.
 */
public interface LoginIdentity extends Identity {
    /**
     * Get the identity username.
     *
     * @return the username.
     */
    String username();

    /**
     * Get the password.
     *
     * @return the password.
     */
    String password();

    /**
     * Create a login identity.
     *
     * @param username Username.
     * @param password Password.
     * @return the login identity.
     */
    static LoginIdentity create(String username, String password) {
        return new LoginIdentityRecord(Objects.requireNonNull(username, "username is null"),
                Objects.requireNonNull(password, "password is null"));
    }
}
