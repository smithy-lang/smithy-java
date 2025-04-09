/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.auth.identity;

import software.amazon.smithy.java.client.core.auth.AuthException;

/**
 * Thrown when an {@link IdentityResolver} is unable to resolve an identity.
 */
public class IdentityNotFoundException extends AuthException {
    public IdentityNotFoundException(String message) {
        super(message);
    }
}
