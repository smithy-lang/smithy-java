/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RequestOptionsTest {

    @Test
    void defaultsUseNullRequestTimeout() {
        var options = RequestOptions.defaults();

        assertNull(options.requestTimeout());
    }

    @Test
    void ofTimeoutUsesDefaultsForNull() {
        assertEquals(new RequestOptions(null), RequestOptions.defaults());
    }

    @Test
    void ofTimeoutSetsRequestTimeout() {
        var options = new RequestOptions(Duration.ofSeconds(5));

        assertEquals(Duration.ofSeconds(5), options.requestTimeout());
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new RequestOptions(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new RequestOptions(Duration.ofMillis(-1)));
    }
}
