/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api.identity;

import java.util.Objects;

/**
 * An API key identity used to securely authorize requests to services that use API key-based auth.
 */
public interface ApiKeyIdentity extends Identity {
    /**
     * Retrieves string field representing the literal api key string.
     *
     * @return the api Key.
     */
    String apiKey();

    /**
     * Constructs a new apiKey object, which can be used to authorize requests to services that use api key-based auth.
     *
     * @param apiKey The apiKey used to authorize requests.
     */
    static ApiKeyIdentity create(String apiKey) {
        return new ApiKeyIdentityRecord(Objects.requireNonNull(apiKey, "apiKey is null"));
    }
}
