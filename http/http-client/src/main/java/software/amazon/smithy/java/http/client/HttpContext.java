/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.java.context.Context;

/**
 * Context keys for HTTP client operations.
 *
 * <p>These keys can be used with {@link RequestOptions#context()} to access
 * request-scoped information set by interceptors.
 */
public final class HttpContext {
    /**
     * Key for accessing the redirect chain during request processing.
     *
     * <p>Contains a list of URIs visited during redirect following.
     * Used by {@link RedirectInterceptor} to detect redirect loops.
     */
    public static final Context.Key<List<URI>> REDIRECT_CHAIN =
            Context.key("http.redirect_chain", ArrayList::new);

    private HttpContext() {}
}
