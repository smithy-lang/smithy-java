/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.io.datastream.DataStream;

abstract sealed class RequestImpl implements Request permits HttpRequest {

    private final Context context = Context.create();
    private DataStream dataStream;
    private SerializableStruct deserializedValue;

    @Override
    public final Context context() {
        return context;
    }

    @Override
    public DataStream getDataStream() {
        return dataStream;
    }

    @Override
    public void setDataStream(DataStream dataStream) {
        this.dataStream = dataStream;
    }

    @Override
    public <T extends SerializableStruct> T getDeserializedValue() {
        return (T) deserializedValue;
    }

    @Override
    public void setDeserializedValue(SerializableStruct serializableStruct) {
        this.deserializedValue = serializableStruct;
    }
}
