/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CannedResponseTest {

    @Test
    void contentLengthDescribesTheServedBodyEvenWhenTheFixtureDeclaresOne() {
        var response = CannedResponse.of(
                200,
                Map.of("Content-Length", "999", "Content-Type", "application/xml", "X-Fixture", "yes"),
                "{}".getBytes(StandardCharsets.UTF_8),
                "application/json");
        assertThat(response.headerMap())
                .containsEntry("content-length", List.of("2"))
                .containsEntry("content-type", List.of("application/xml"))
                .containsEntry("x-fixture", List.of("yes"));
        assertThat(response.contentType()).isEqualTo("application/xml");
        assertThat(response.headers().contentLength()).isEqualTo(2L);
    }
}
