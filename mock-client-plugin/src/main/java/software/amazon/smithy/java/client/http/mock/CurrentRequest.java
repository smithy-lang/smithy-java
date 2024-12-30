/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.mock;

import software.amazon.smithy.java.client.core.ClientProtocol;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.server.Operation;

/**
 * Package-private request placeholder.
 *
 * @param operation Server operation being sent.
 * @param request
 * @param protocol
 */
record CurrentRequest(
        Operation<? extends SerializableStruct, ? extends SerializableStruct> operation,
        MockedRequest request,
        ClientProtocol<HttpRequest, HttpResponse> protocol) {
    /**
     * Create a new CurrentRequest using the provided request.
     *
     * @param request request to use.
     * @return the new CurrentRequest.
     */
    CurrentRequest withMessage(HttpRequest request) {
        return new CurrentRequest(operation, request().withRequest(request), protocol);
    }
}
