/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import java.util.HashSet;
import java.util.Set;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.endpoints.Endpoint;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.java.retries.api.RetryToken;

/**
 * Context parameters made available to underlying transports like HTTP clients.
 *
 * <p>Settings that can be applied client-wide can be found in {@link ClientContext}.
 */
public final class CallContext {
    /**
     * The read-only endpoint for the request.
     */
    public static final Context.Key<Endpoint> ENDPOINT = Context.key("Endpoint of the request");

    /**
     * The endpoint resolver used to resolve the destination endpoint for a request.
     *
     * <p>This is a read-only value; modifying this value has no effect on a request.
     */
    public static final Context.Key<EndpointResolver> ENDPOINT_RESOLVER = Context.key("EndpointResolver");

    /**
     * The read-only identity resolved for the request.
     *
     * <p>This is a read-only value; modifying this value has no effect on a request.
     */
    public static final Context.Key<Identity> IDENTITY = Context.key("Identity of the caller");

    /**
     * The current number of retry attempts the client has made for the current call, starting at 1.
     *
     * <p>This is a read-only value; modifying this value has no effect on a request.
     */
    public static final Context.Key<Integer> RETRY_ATTEMPT = Context.key("Retry attempt");

    /**
     * The maximum number of retries the client will issue before giving up.
     *
     * <p>This is a read-only value; modifying this value has no effect on a request.
     */
    public static final Context.Key<Integer> RETRY_MAX = Context.key("Max retries");

    /**
     * The opaque retry token for the current in-progress attempt, if a retry strategy is in use.
     *
     * <p>The token is acquired before the first attempt and refreshed after each retryable failure, so the value
     * observed by an interceptor is always the token in effect for the current attempt. It is {@code null} once the
     * call completes and the token is released.
     *
     * <p>This is a read-only value; modifying this value has no effect on a request.
     */
    public static final Context.Key<RetryToken> RETRY_TOKEN = Context.key("Retry token");

    /**
     * The idempotency token used with the call, if any.
     *
     * <p>This is a read-only value; modifying this value has no effect on a request.
     */
    public static final Context.Key<String> IDEMPOTENCY_TOKEN = Context.key("Idempotency token");

    /**
     * The set of user-defined feature IDs used with a request.
     *
     * <p>When {@link FeatureId#getShortName()} is called on a provided value, the string should be short and contain
     * only ASCII letters, numbers, and hyphens. For example, "P" might be used to indicate that pagination was used
     * with a request.
     */
    public static final Context.Key<Set<FeatureId>> FEATURE_IDS = Context.key(
            "Feature IDs used with a request",
            HashSet::new);

    private CallContext() {}
}
