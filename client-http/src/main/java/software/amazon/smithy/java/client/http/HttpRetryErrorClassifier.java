/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.client.core.settings.ClockSetting;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiException;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.retries.api.RetrySafety;

/**
 * A retry classifier that decides if a retry is needed based on HTTP headers and status codes.
 */
public final class HttpRetryErrorClassifier {

    private HttpRetryErrorClassifier() {}

    /**
     * Applies default retry classification for HTTP protocols.
     *
     * @param operation Operation that was called.
     * @param response Response received.
     * @param exception Exception encountered.
     * @param context Context of the call.
     */
    public static void applyRetryInfo(
        ApiOperation<?, ?> operation,
        HttpResponse response,
        ApiException exception,
        Context context
    ) {
        // (1) Check the model for retry eligibility.
        ApiOperation.applyRetryInfoFromModel(operation.schema(), exception);

        // (2) Check with the protocol if the server explicitly wants a retry.
        if (!applyRetryAfterHeader(response, exception, context)) {
            if (!applyThrottlingStatusCodes(response, exception)) {
                // (3) If no retry was detected so far, is it safe to retry because of a 5XX error + idempotency token?
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
