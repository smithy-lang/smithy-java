/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core;

import java.util.Set;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.OutputHook;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.error.CallException;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.retries.api.RetrySafety;

/**
 * Interceptor that notifies the request identity resolver when a service returns an expired- or
 * invalid-credential error.
 */
final class InvalidateCredentialsInterceptor implements ClientInterceptor {

    static final InvalidateCredentialsInterceptor INSTANCE = new InvalidateCredentialsInterceptor();

    // Well-known error names. Ideally the wire would have a signal so we do not need this.
    private static final Set<String> EXPIRED_NAMES = Set.of("ExpiredToken", "InvalidToken");

    private InvalidateCredentialsInterceptor() {}

    @Override
    public void readAfterAttempt(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
        invalidate(hook.context(), error);
    }

    void invalidate(Context context, RuntimeException error) {
        if (!shouldInvalidate(context, error)) {
            return;
        }

        if (error instanceof CallException callException) {
            callException.isRetrySafe(RetrySafety.NO);
        }

        var identity = context.get(CallContext.IDENTITY);
        var resolver = context.get(CallContext.IDENTITY_RESOLVER);
        if (identity == null || resolver == null || !resolver.identityType().isInstance(identity)) {
            return;
        }
        invalidate(resolver, identity);
    }

    private static <I extends Identity> void invalidate(IdentityResolver<I> resolver, Identity rejectedIdentity) {
        resolver.invalidate(resolver.identityType().cast(rejectedIdentity));
    }

    private static boolean shouldInvalidate(Context context, RuntimeException error) {
        String errorCode = context.get(CallContext.RESPONSE_ERROR_CODE);
        if (errorCode != null && EXPIRED_NAMES.contains(errorCode)) {
            return true;
        }
        if (error instanceof ModeledException me) {
            var name = me.schema().id().getName();
            return EXPIRED_NAMES.contains(name);
        }
        return false;
    }
}
