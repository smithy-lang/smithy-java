/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;

class RequestOptionsTest {

    @Test
    void putContextAddsToContext() {
        var key = Context.<String>key("test");
        var options = RequestOptions.builder().putContext(key, "value").build();

        assertEquals("value", options.context().get(key));
    }

    @Test
    void buildClearsRequestTimeout() {
        var builder = RequestOptions.builder()
                .requestTimeout(Duration.ofSeconds(5));
        var first = builder.build();
        var second = builder.build();

        assertEquals(Duration.ofSeconds(5), first.requestTimeout());
        assertNull(second.requestTimeout(), "requestTimeout should be cleared after build");
    }
}
