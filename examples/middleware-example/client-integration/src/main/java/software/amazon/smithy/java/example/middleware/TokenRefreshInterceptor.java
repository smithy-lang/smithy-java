/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.example.middleware;

import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.OutputHook;
import software.amazon.smithy.java.example.middleware.model.RefreshCredentialsException;

/**
 * Refreshes token credentials for {@code CustomAuthScheme} on a {@link RefreshCredentialsException}.
 */
final class TokenRefreshInterceptor implements ClientInterceptor {
    private static final String TOKEN_PROPERTY = "smithy.java.example.token";
    private static final String UPDATED_TOKEN_PROPERTY = "smithy.java.example.update";

    @Override
    public void readAfterDeserialization(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
        var attempt = hook.context().expect(CallContext.RETRY_ATTEMPT);
        // Only attempt to refresh on the first retry.
        if (attempt == 1 && hook.output() instanceof RefreshCredentialsException) {
            var newToken = System.getProperty(UPDATED_TOKEN_PROPERTY);
            if (newToken == null) {
                return;
            }
            System.setProperty(TOKEN_PROPERTY, newToken);
        }
    }
}
