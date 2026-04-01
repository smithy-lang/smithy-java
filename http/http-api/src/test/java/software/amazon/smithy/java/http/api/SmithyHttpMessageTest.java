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
        var r = HttpRequest.create()
                .setMethod("GET")
                .setUri(new URI("https://example.com"))
                .setHeaders(HttpHeaders.of(Map.of("foo", List.of("bar"))))
                .toUnmodifiable();

        var mod = r.toModifiableCopy();
        mod.headers().addHeader("foo", "bar2");
        mod.headers().addHeaders(HttpHeaders.of(Map.of("foo", List.of("bar3"))));

        assertThat(mod.headers().allValues("foo"), contains("bar", "bar2", "bar3"));
    }
}
