/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import java.time.Duration;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.context.Context;

/**
 * Context parameters that can be provided on a client config and take effect on each request.
 *
 * <p>Other per/call settings can be found in {@link CallContext}.
 */
public final class ClientContext {
    /**
     * A custom endpoint used in each request.
     *
     * <p>This can be used in lieu of setting something like {@link EndpointResolver#staticEndpoint}, allowing
     * endpoint resolvers like the Smithy Rules Engine resolver to still process and validate endpoints even when a
     * custom endpoint is provided.
     */
    public static final Context.Key<Endpoint> CUSTOM_ENDPOINT = Context.key("Custom endpoint to use with requests");

    /**
     * The name of the application, used in things like user-agent headers.
     *
     * <p>This value is used by AWS SDKs, but can be used generically for any client.
     * See <a href="https://docs.aws.amazon.com/sdkref/latest/guide/feature-appid.html">Application ID</a> for more
     * information.
     *
     * <p>This value should be less than 50 characters.
     */
    public static final Context.Key<String> APPLICATION_ID = Context.key("Application ID");

    /**
     * The total amount of time to wait for an API call to complete, including retries, and serialization.
     *
     * <p>This can be overridden per/call too.
     */
    public static final Context.Key<Duration> API_CALL_TIMEOUT = Context.key("API call timeout");

    /**
     * The amount of time to wait for a single, underlying network request to complete before giving up and timing out.
     *
     * <p>This can be overridden per/call too.
     */
    public static final Context.Key<Duration> API_CALL_ATTEMPT_TIMEOUT = Context.key("API call attempt timeout");

    private ClientContext() {}
}
