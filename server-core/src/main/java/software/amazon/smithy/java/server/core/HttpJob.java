/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.server.Operation;

public final class HttpJob extends DefaultJob {

    private final HttpRequest request;
    private final HttpResponse response;

    public HttpJob(
        Operation<? extends SerializableStruct, ? extends SerializableStruct> operation,
        ServerProtocol protocol,
        HttpRequest request,
        HttpResponse response
    ) {
        super(operation, protocol);
        this.request = request;
        this.response = response;
    }

    @Override
    public HttpRequest request() {
        return request;
    }

    @Override
    public HttpResponse response() {
        return response;
    }

    @Override
    public boolean isHttpJob() {
        return true;
    }
}
