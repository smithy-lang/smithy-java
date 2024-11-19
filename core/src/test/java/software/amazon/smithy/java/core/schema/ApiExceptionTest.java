/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.retries.api.RetrySafety;

public class ApiExceptionTest {
    @Test
    public void resetsRetryStateWhenSetToNotRetry() {
        var e = new ApiException("foo");
        var after = Duration.ofDays(2);

        e.isRetrySafe(RetrySafety.YES);
        e.retryAfter(after);
        e.isThrottle(true);

        assertThat(e.isRetrySafe(), is(RetrySafety.YES));
        assertThat(e.retryAfter(), equalTo(after));
        assertThat(e.isThrottle(), is(true));

        e.isRetrySafe(RetrySafety.NO);

        assertThat(e.isRetrySafe(), is(RetrySafety.NO));
        assertThat(e.retryAfter(), nullValue());
        assertThat(e.isThrottle(), is(false));
    }
}
