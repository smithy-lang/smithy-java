/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import java.time.Duration;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.context.Context;

/**
 * Context parameters made available to underlying transports like HTTP clients.
 */
public final class CallContext {
    /**
     * The total amount of time to wait for an API call to complete, including retries, and serialization.
     */
    public static final Context.Key<Duration> API_CALL_TIMEOUT = Context.key("API call timeout");

    /**
     * The amount of time to wait for a single, underlying network request to complete before giving up and timing out.
     */
    public static final Context.Key<Duration> API_CALL_ATTEMPT_TIMEOUT = Context.key("API call attempt timeout");

    /**
     * The endpoint resolver used to resolve the destination endpoint for a request.
     */
    public static final Context.Key<EndpointResolver> ENDPOINT_RESOLVER = Context.key("EndpointResolver");

    /**
     * The identity resolved for the request.
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
     * The idempotency token used with the call, if any.
     *
     * <p>This is a read-only value; modifying this value has no effect on a request.
     */
    public static final Context.Key<String> IDEMPOTENCY_TOKEN = Context.key("Idempotency token");

    private CallContext() {}
}
