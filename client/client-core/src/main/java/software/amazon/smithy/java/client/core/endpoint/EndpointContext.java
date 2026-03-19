/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.endpoint;

import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.context.Context;

/**
 * Context parameters specifically relevant to a resolved {@link Endpoint} property.
 */
public final class EndpointContext {

    private EndpointContext() {}

    /**
     * Assigns headers to an endpoint. These are typically HTTP headers.
     */
    public static final Context.Key<Map<String, List<String>>> HEADERS = Context.key("Endpoint headers");
}
