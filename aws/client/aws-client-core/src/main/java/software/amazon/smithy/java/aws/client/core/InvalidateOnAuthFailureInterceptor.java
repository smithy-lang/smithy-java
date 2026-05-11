/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core;

import java.util.Set;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.OutputHook;
import software.amazon.smithy.java.core.error.ModeledException;

/**
 * Interceptor that invalidates cached credentials when a service returns an authentication error
 * (HTTP 401 or 403), forcing the next retry attempt to resolve fresh credentials.
 */
final class InvalidateOnAuthFailureInterceptor implements ClientInterceptor {

    private final IdentityResolver<?> resolver;

    // well-known error names. Ideally the wire would have signal so we don't need this.
    private static final Set<String> EXPIRED_NAMES = Set.of(
            "ExpiredToken",
            "InvalidToken",
            "AuthFailure");

    InvalidateOnAuthFailureInterceptor(IdentityResolver<?> resolver) {
        this.resolver = resolver;
    }

    @Override
    public void readAfterAttempt(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
        if (isAuthError(error)) {
            resolver.invalidate();
        }
    }

    private static boolean isAuthError(RuntimeException error) {
        // Check for common auth failure error names.
        if (error instanceof ModeledException me) {
            var name = me.schema().id().getName();
            return EXPIRED_NAMES.contains(name);
        }
        return false;
    }
}
