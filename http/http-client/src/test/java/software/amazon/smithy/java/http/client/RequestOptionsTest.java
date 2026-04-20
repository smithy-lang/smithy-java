/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;

class RequestOptionsTest {

    @Test
    void resolveInterceptorsReturnsClientOnlyWhenNoRequestInterceptors() {
        var clientInterceptor = new NoOpInterceptor();
        var options = RequestOptions.defaults();
        var resolved = options.resolveInterceptors(List.of(clientInterceptor));

        assertEquals(List.of(clientInterceptor), resolved);
    }

    @Test
    void resolveInterceptorsReturnsRequestOnlyWhenNoClientInterceptors() {
        var requestInterceptor = new NoOpInterceptor();
        var options = RequestOptions.builder().addInterceptor(requestInterceptor).build();
        var resolved = options.resolveInterceptors(List.of());

        assertEquals(List.of(requestInterceptor), resolved);
    }

    @Test
    void resolveInterceptorsCombinesClientThenRequest() {
        var clientInterceptor = new NoOpInterceptor();
        var requestInterceptor = new NoOpInterceptor();
        var options = RequestOptions.builder().addInterceptor(requestInterceptor).build();
        var resolved = options.resolveInterceptors(List.of(clientInterceptor));

        assertEquals(2, resolved.size());
        assertEquals(clientInterceptor, resolved.get(0));
        assertEquals(requestInterceptor, resolved.get(1));
    }

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

    private static class NoOpInterceptor implements HttpInterceptor {}
}
