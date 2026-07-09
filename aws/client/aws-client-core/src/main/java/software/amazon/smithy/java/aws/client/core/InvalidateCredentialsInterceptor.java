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
 * Interceptor that invalidates cached credentials when a service returns an expired- or
 * invalid-credential error, so the stale credentials are cleared and the next request resolves
 * fresh ones.
 */
final class InvalidateCredentialsInterceptor implements ClientInterceptor {

    private final IdentityResolver<?> resolver;

    // well-known error names. Ideally the wire would have signal so we don't need this.
    private static final Set<String> EXPIRED_NAMES = Set.of("ExpiredToken", "InvalidToken");

    InvalidateCredentialsInterceptor(IdentityResolver<?> resolver) {
        this.resolver = resolver;
    }

    @Override
    public void readAfterAttempt(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
        if (shouldInvalidate(error)) {
            resolver.invalidate();
        }
    }

    private static boolean shouldInvalidate(RuntimeException error) {
        // Check for well-known expired- or invalid-credential error names.
        if (error instanceof ModeledException me) {
            var name = me.schema().id().getName();
            return EXPIRED_NAMES.contains(name);
        }
        return false;
    }
}
