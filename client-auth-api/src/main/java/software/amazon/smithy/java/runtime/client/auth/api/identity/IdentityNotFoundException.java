/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.auth.api.identity;

import software.amazon.smithy.java.runtime.auth.api.identity.Identity;
import software.amazon.smithy.java.runtime.client.auth.api.AuthException;

/**
 * Thrown when an {@link IdentityResolver} is unable to resolve an identity.
 */
public class IdentityNotFoundException extends AuthException {

    private final Class<? extends IdentityResolver<?>> identityResolverClass;
    private final Class<? extends Identity> identityClass;

    public IdentityNotFoundException(
        String message,
        Class<? extends IdentityResolver<?>> identityResolverClass,
        Class<? extends Identity> identityClass
    ) {
        super(message);
        this.identityClass = identityClass;
        this.identityResolverClass = identityResolverClass;
    }

    public Class<? extends Identity> identityClass() {
        return identityClass;
    }

    public Class<? extends IdentityResolver<?>> identityResolverClass() {
        return identityResolverClass;
    }
}
