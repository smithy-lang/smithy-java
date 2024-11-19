/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class SmithyHttpMessageTest {
    @Test
    public void canAddHeadersToImmutableHeaders() throws Exception {
        var r = HttpRequest.builder()
            .method("GET")
            .uri(new URI("https://example.com"))
            .headers(HttpHeaders.of(Map.of("foo", List.of("bar"))))
            .build();

        var builder = r.toBuilder();
        builder.withAddedHeaders("foo", "bar2");
        builder.withAddedHeaders(HttpHeaders.of(Map.of("foo", List.of("bar3"))));
        var updated = builder.build();

        assertThat(updated.headers().allValues("foo"), contains("bar", "bar2", "bar3"));
    }
}
