/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.plugins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.client.core.settings.ClockSetting;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.error.CallException;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.retries.api.RetrySafety;

public class ApplyHttpRetryInfoPluginTest {
    @Test
    public void appliesRetryAfterHeader() {
        var response = HttpResponse.builder()
                .statusCode(500)
                .headers(HttpHeaders.of(Map.of("retry-after", List.of("10"))))
                .build();
        var e = new CallException("err");
        var context = Context.create();

        ApplyHttpRetryInfoPlugin.applyRetryInfo(response, e, context);

        assertThat(e.isRetrySafe(), is(RetrySafety.YES));
        assertThat(e.isThrottle(), is(true));
        assertThat(e.retryAfter(), equalTo(Duration.ofSeconds(10)));
    }

    @Test
    public void appliesRetryAfterHeaderDate() {
        var response = HttpResponse.builder()
                .statusCode(500)
                .headers(HttpHeaders.of(Map.of("retry-after", List.of("Wed, 21 Oct 2015 07:28:00 GMT"))))
                .build();
        var e = new CallException("err");
        var context = Context.create();
        context.put(ClockSetting.CLOCK, Clock.fixed(Instant.parse("2015-10-21T05:28:00Z"), ZoneId.of("UTC")));

        ApplyHttpRetryInfoPlugin.applyRetryInfo(response, e, context);

        assertThat(e.isRetrySafe(), is(RetrySafety.YES));
        assertThat(e.isThrottle(), is(true));
        assertThat(e.retryAfter(), equalTo(Duration.ofHours(2)));
    }

    @Test
    public void appliesThrottlingStatusCode503() {
        var response = HttpResponse.builder().statusCode(503).build();
        var e = new CallException("err");
        var context = Context.create();

        ApplyHttpRetryInfoPlugin.applyRetryInfo(response, e, context);

        assertThat(e.isRetrySafe(), is(RetrySafety.YES));
        assertThat(e.isThrottle(), is(true));
        assertThat(e.retryAfter(), nullValue());
    }

    @Test
    public void appliesThrottlingStatusCode429() {
        var response = HttpResponse.builder().statusCode(429).build();
        var e = new CallException("err");
        var context = Context.create();

        ApplyHttpRetryInfoPlugin.applyRetryInfo(response, e, context);

        assertThat(e.isRetrySafe(), is(RetrySafety.YES));
        assertThat(e.isThrottle(), is(true));
        assertThat(e.retryAfter(), nullValue());
    }

    @Test
    public void retriesSafe5xx() {
        var response = HttpResponse.builder().statusCode(500).build();
        var e = new CallException("err");
        var context = Context.create();
        context.put(CallContext.IDEMPOTENCY_TOKEN, "foo");

        ApplyHttpRetryInfoPlugin.applyRetryInfo(response, e, context);

        assertThat(e.isRetrySafe(), is(RetrySafety.YES));
        assertThat(e.isThrottle(), is(false));
        assertThat(e.retryAfter(), nullValue());
    }

    @Test
    public void doesNotRetryUnsafe5xx() {
        var response = HttpResponse.builder().statusCode(500).build();
        var e = new CallException("err");
        var context = Context.create();

        ApplyHttpRetryInfoPlugin.applyRetryInfo(response, e, context);

        assertThat(e.isRetrySafe(), is(RetrySafety.NO));
        assertThat(e.isThrottle(), is(false));
        assertThat(e.retryAfter(), nullValue());
    }

    @Test
    public void doesNotRetryNormal4xx() {
        var response = HttpResponse.builder().statusCode(400).build();
        var e = new CallException("err");
        var context = Context.create();

        ApplyHttpRetryInfoPlugin.applyRetryInfo(response, e, context);

        assertThat(e.isRetrySafe(), is(RetrySafety.NO));
        assertThat(e.isThrottle(), is(false));
        assertThat(e.retryAfter(), nullValue());
    }
}
