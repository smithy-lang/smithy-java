/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.plugins;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.OutputHook;
import software.amazon.smithy.java.client.core.settings.ClockSetting;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiException;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.retries.api.RetrySafety;

/**
 * Adds retry information to HTTP errors based on retry-after headers, 429 and 503 status codes, the presence of
 * idempotency tokens, if a request is idempotent or readonly, and whether an error is retryable.
 *
 * <p>This plugin is applied automatically when using an HTTP protocol via {@link HttpMessageExchange}.
 */
public final class ApplyHttpRetryInfoPlugin implements ClientPlugin {
    @Override
    public void configureClient(ClientConfig.Builder config) {
        config.addInterceptor(Interceptor.INSTANCE);
    }

    private static final class Interceptor implements ClientInterceptor {
        private static final ClientInterceptor INSTANCE = new Interceptor();

        @Override
        public <O extends SerializableStruct> O modifyBeforeAttemptCompletion(
            OutputHook<?, O, ?, ?> hook,
            RuntimeException error
        ) {
            if (error instanceof ApiException ae
                && ae.isRetrySafe() == RetrySafety.MAYBE
                && hook.response() instanceof HttpResponse res) {
                applyRetryInfo(res, ae, hook.context());
            }
            return hook.forward(error);
        }
    }

    static void applyRetryInfo(HttpResponse response, ApiException exception, Context context) {
        // (1) Check with the protocol if the server explicitly wants a retry.
        if (!applyRetryAfterHeader(response, exception, context)) {
            if (!applyThrottlingStatusCodes(response, exception)) {
                // (2) If no retry was detected so far, is it safe to retry because of a 5XX error + idempotency token?
                if (exception.isRetrySafe() == RetrySafety.MAYBE) {
                    var idempotencyTokenUsed = context.get(CallContext.IDEMPOTENCY_TOKEN) != null;
                    if (response.statusCode() >= 500 && idempotencyTokenUsed) {
                        exception.isRetrySafe(RetrySafety.YES);
                    } else {
                        exception.isRetrySafe(RetrySafety.NO);
                    }
                }
            }
        }
    }

    // Treat 429 and 503 errors as retryable throttling errors.
    private static boolean applyThrottlingStatusCodes(HttpResponse response, ApiException exception) {
        if (response.statusCode() == 429 || response.statusCode() == 503) {
            exception.isRetrySafe(RetrySafety.YES);
            exception.isThrottle(true);
            return true;
        }
        return false;
    }

    // If there's a retry-after header, then the server is telling us it's retryable.
    private static boolean applyRetryAfterHeader(HttpResponse response, ApiException exception, Context context) {
        var retryAfter = response.headers().firstValue("retry-after");
        if (retryAfter != null) {
            exception.isThrottle(true);
            exception.isRetrySafe(RetrySafety.YES);
            exception.retryAfter(parseRetryAfter(retryAfter, context));
            return true;
        }

        return false;
    }

    private static Duration parseRetryAfter(String retryAfter, Context context) {
        try {
            return Duration.of(Integer.parseInt(retryAfter), ChronoUnit.SECONDS);
        } catch (NumberFormatException e) {
            // It's not a number, so it must be a http-date like "Wed, 21 Oct 2015 07:28:00 GMT".
            var date = ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME);
            // Use the Clock associated with the context, if any, to account for things like clock skew.
            var clock = context.get(ClockSetting.CLOCK);
            var now = clock == null ? Instant.now() : clock.instant();
            return Duration.between(now, date);
        }
    }
}
