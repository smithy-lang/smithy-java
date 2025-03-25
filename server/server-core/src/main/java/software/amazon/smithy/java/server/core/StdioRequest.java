/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.jsonrpc.model.JsonRpcRequest;

public final class StdioRequest extends RequestImpl {
    private final JsonRpcRequest request;

    public StdioRequest(JsonRpcRequest request) {
        this.request = request;
    }

    public Document params() {
        return request.params();
    }

    public Integer id() {
        return request.id();
    }

    public String method() {
        return request.method();
    }

    @Override
    public DataStream getDataStream() {
        throw new UnsupportedOperationException("JSON-RPC does not support streamed data");
    }

    @Override
    public void setDataStream(DataStream dataStream) {
        throw new UnsupportedOperationException("JSON-RPC does not support streamed data");
    }
}
