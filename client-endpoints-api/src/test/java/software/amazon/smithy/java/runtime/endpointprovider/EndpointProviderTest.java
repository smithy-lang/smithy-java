/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.endpointprovider;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.api.Endpoint;
import software.amazon.smithy.java.runtime.api.EndpointProvider;
import software.amazon.smithy.java.runtime.api.EndpointProviderRequest;

public class EndpointProviderTest {
    @Test
    public void returnsStaticEndpoint() {
        EndpointProvider provider = EndpointProvider
            .staticEndpoint(Endpoint.builder().uri("https://example.com").build());

        MatcherAssert.assertThat(
            provider.resolveEndpoint(EndpointProviderRequest.builder().build()).uri().toString(),
            Matchers.equalTo("https://example.com")
        );
    }
}
