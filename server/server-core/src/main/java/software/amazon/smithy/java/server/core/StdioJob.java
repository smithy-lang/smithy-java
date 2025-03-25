/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.server.Operation;

public final class StdioJob extends DefaultJob {
    private final StdioRequest request;
    private final StdioResponse response;

    public StdioJob(
            Operation<? extends SerializableStruct, ? extends SerializableStruct> operation,
            ServerProtocol protocol,
            StdioRequest request,
            StdioResponse response
    ) {
        super(operation, protocol);
        this.request = request;
        this.response = response;
    }

    @Override
    public StdioRequest request() {
        return request;
    }

    @Override
    public StdioResponse response() {
        return response;
    }
}
