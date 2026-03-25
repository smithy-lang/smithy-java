/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.endpoints;

import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.context.Context;

/**
 * Context parameters specifically relevant to a resolved {@link Endpoint} property.
 */
public final class EndpointContext {

    private EndpointContext() {}

    /**
     * A custom endpoint used in each request.
     *
     * <p>This can be used in lieu of setting an endpoint resolver, allowing
     * endpoint resolvers like the Smithy Rules Engine resolver to still process and validate endpoints even when a
     * custom endpoint is provided.
     */
    public static final Context.Key<Endpoint> CUSTOM_ENDPOINT = Context.key("Custom endpoint to use with requests");

    /**
     * Assigns headers to an endpoint. These are typically HTTP headers.
     */
    public static final Context.Key<Map<String, List<String>>> HEADERS = Context.key("Endpoint headers");
}
