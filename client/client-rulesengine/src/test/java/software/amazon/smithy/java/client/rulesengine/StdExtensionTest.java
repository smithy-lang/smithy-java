/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.ClientContext;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.context.Context;

class StdExtensionTest {

    @Test
    void testProvidesEndpointBuiltin() {
        StdExtension extension = new StdExtension();
        Map<String, Function<Context, Object>> providers = new HashMap<>();

        extension.putBuiltinProviders(providers);

        assertTrue(providers.containsKey("SDK::Endpoint"));
    }

    @Test
    void testEndpointProviderReturnsNull() {
        StdExtension extension = new StdExtension();
        Map<String, Function<Context, Object>> providers = new HashMap<>();
        extension.putBuiltinProviders(providers);

        Function<Context, Object> endpointProvider = providers.get("SDK::Endpoint");
        Context context = Context.empty();

        assertNull(endpointProvider.apply(context));
    }

    @Test
    void testEndpointProviderReturnsCustomEndpoint() {
        StdExtension extension = new StdExtension();
        Map<String, Function<Context, Object>> providers = new HashMap<>();
        extension.putBuiltinProviders(providers);

        Function<Context, Object> endpointProvider = providers.get("SDK::Endpoint");

        Endpoint testEndpoint = Endpoint.builder().uri("https://foo.com").build();
        Context context = Context.create().put(ClientContext.CUSTOM_ENDPOINT, testEndpoint);

        assertEquals(testEndpoint.uri().toString(), endpointProvider.apply(context));
    }
}
