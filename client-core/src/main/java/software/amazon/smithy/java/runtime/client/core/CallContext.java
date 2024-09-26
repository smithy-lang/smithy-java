/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import java.time.Duration;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.auth.api.identity.Identity;
import software.amazon.smithy.java.runtime.client.core.endpoint.EndpointResolver;

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

    private CallContext() {}
}
