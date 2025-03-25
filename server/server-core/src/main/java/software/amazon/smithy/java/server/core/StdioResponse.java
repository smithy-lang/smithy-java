/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.io.datastream.DataStream;

public final class StdioResponse extends ResponseImpl {
    private SerializableStruct response;

    public StdioResponse() {}

    public void setResponse(SerializableStruct response) {
        this.response = response;
    }

    public SerializableStruct response() {
        return this.response;
    }

    @Override
    public void setSerializedValue(DataStream serializedValue) {
        throw new UnsupportedOperationException("cannot serialize a value to a JsonRpcV2Response");
    }

    @Override
    public DataStream getSerializedValue() {
        throw new UnsupportedOperationException("cannot get a serialized value from a JsonRpcV2Response");
    }
}
