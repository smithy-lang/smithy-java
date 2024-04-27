/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoints.api;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class EndpointResolverTest {
    @Test
    public void returnsStaticEndpoint() {
        EndpointResolver resolver = EndpointResolver
            .staticEndpoint(Endpoint.builder().uri("https://example.com").build());

        MatcherAssert.assertThat(
            resolver.resolveEndpoint(EndpointResolverParams.builder().operationName("Foo").build()).uri().toString(),
            Matchers.equalTo("https://example.com")
        );
    }
}
