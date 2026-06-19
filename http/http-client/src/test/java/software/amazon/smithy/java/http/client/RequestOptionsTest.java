/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.uri.SmithyUri;

class RequestOptionsTest {

    @Test
    void defaultsAreAllNull() {
        var options = RequestOptions.defaults();

        assertNull(options.requestTimeout());
        assertNull(options.connectTimeout());
        assertNull(options.readTimeout());
        assertNull(options.acquireTimeout());
        assertNull(options.expectContinue());
    }

    @Test
    void defaultsIsSingleton() {
        assertSame(RequestOptions.defaults(), RequestOptions.defaults());
    }

    @Test
    void builderSetsEveryField() {
        var options = RequestOptions.builder()
                .requestTimeout(Duration.ofSeconds(1))
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(3))
                .acquireTimeout(Duration.ofSeconds(4))
                .expectContinue(true)
                .build();

        assertEquals(Duration.ofSeconds(1), options.requestTimeout());
        assertEquals(Duration.ofSeconds(2), options.connectTimeout());
        assertEquals(Duration.ofSeconds(3), options.readTimeout());
        assertEquals(Duration.ofSeconds(4), options.acquireTimeout());
        assertEquals(Boolean.TRUE, options.expectContinue());
    }

    @Test
    void equalsAndHashCodeAreValueBased() {
        var a = RequestOptions.builder()
                .requestTimeout(Duration.ofSeconds(1))
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(3))
                .acquireTimeout(Duration.ofSeconds(4))
                .expectContinue(true)
                .build();
        var b = RequestOptions.builder()
                .requestTimeout(Duration.ofSeconds(1))
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(3))
                .acquireTimeout(Duration.ofSeconds(4))
                .expectContinue(true)
                .build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(RequestOptions.defaults(), RequestOptions.builder().build());
        assertEquals(RequestOptions.defaults().hashCode(), RequestOptions.builder().build().hashCode());
    }

    @Test
    void notEqualWhenAnyFieldDiffers() {
        var base = RequestOptions.builder().connectTimeout(Duration.ofSeconds(2)).build();

        assertNotEquals(base, RequestOptions.builder().connectTimeout(Duration.ofSeconds(5)).build());
        assertNotEquals(base, RequestOptions.builder().readTimeout(Duration.ofSeconds(2)).build());
        assertNotEquals(base, RequestOptions.defaults());
        assertNotEquals(base, null);
    }

    @Test
    void rejectsNonPositiveTimeouts() {
        assertThrows(IllegalArgumentException.class,
                () -> RequestOptions.builder().requestTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> RequestOptions.builder().connectTimeout(Duration.ofMillis(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> RequestOptions.builder().readTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> RequestOptions.builder().acquireTimeout(Duration.ofSeconds(-5)));
    }

    @Test
    void nullTimeoutsAreAccepted() {
        var options = RequestOptions.builder()
                .requestTimeout(null)
                .connectTimeout(null)
                .readTimeout(null)
                .acquireTimeout(null)
                .build();

        assertNull(options.requestTimeout());
        assertNull(options.connectTimeout());
        assertNull(options.readTimeout());
        assertNull(options.acquireTimeout());
    }

    @Test
    void applyExpectContinueNullLeavesRequestUntouched() {
        var withHeader = request().toModifiableCopy().setHeader(HeaderName.EXPECT, "100-continue");
        var options = RequestOptions.defaults();

        assertSame(withHeader, options.applyExpectContinue(withHeader));
    }

    @Test
    void applyExpectContinueTrueAddsHeaderWhenAbsent() {
        var request = request();
        var options = RequestOptions.builder().expectContinue(true).build();

        var result = options.applyExpectContinue(request);

        assertEquals("100-continue", result.headers().firstValue(HeaderName.EXPECT));
        // Input is modifiable, so it is adjusted in place rather than copied.
        assertSame(request, result);
    }

    @Test
    void applyExpectContinueTrueIsNoOpWhenAlreadyPresent() {
        var request = request().toModifiableCopy().setHeader(HeaderName.EXPECT, "100-continue");
        var options = RequestOptions.builder().expectContinue(true).build();

        assertSame(request, options.applyExpectContinue(request));
    }

    @Test
    void applyExpectContinueFalseStripsHeader() {
        var request = request().toModifiableCopy().setHeader(HeaderName.EXPECT, "100-continue");
        var options = RequestOptions.builder().expectContinue(false).build();

        var result = options.applyExpectContinue(request);

        assertNull(result.headers().firstValue(HeaderName.EXPECT));
        // Input is modifiable, so it is adjusted in place rather than copied.
        assertSame(request, result);
    }

    @Test
    void applyExpectContinueFalseIsNoOpWhenAbsent() {
        var request = request();
        var options = RequestOptions.builder().expectContinue(false).build();

        assertSame(request, options.applyExpectContinue(request));
    }

    @Test
    void applyExpectContinueTreatsHeaderCaseInsensitively() {
        var request = request().toModifiableCopy().setHeader(HeaderName.EXPECT, "100-Continue");
        var options = RequestOptions.builder().expectContinue(false).build();

        var result = options.applyExpectContinue(request);

        assertTrue(result.headers().allValues(HeaderName.EXPECT).isEmpty());
    }

    private static HttpRequest request() {
        return HttpRequest.create()
                .setMethod("POST")
                .setUri(SmithyUri.of("https://example.com/test"));
    }
}
