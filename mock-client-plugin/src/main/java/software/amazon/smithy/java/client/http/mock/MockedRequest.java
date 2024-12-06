/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.mock;

import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.http.api.HttpRequest;

/**
 * A mocked request received by the {@link MockPlugin}.
 *
 * @param input The input shape.
 * @param request The HTTP request that was sent.
 */
public record MockedRequest(SerializableStruct input, HttpRequest request) {
    /**
     * Create an updated mocked request using the given request.
     *
     * @param request HTTP request to set.
     * @return a new mocked request.
     */
    public MockedRequest withRequest(HttpRequest request) {
        return new MockedRequest(input(), request);
    }
}
