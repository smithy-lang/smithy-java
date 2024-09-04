/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;

public abstract sealed class ResponseImpl implements Response permits HttpResponse {

    private final Context context = Context.create();
    private SerializableStruct value;
    private DataStream dataStream;

    @Override
    public final Context context() {
        return context;
    }

    @Override
    public void setValue(SerializableStruct value) {
        this.value = value;
    }

    @Override
    public <T extends SerializableStruct> T getValue() {
        return (T) value;
    }

    @Override
    public void setSerializedValue(DataStream serializedValue) {
        this.dataStream = serializedValue;
    }

    @Override
    public DataStream getSerializedValue() {
        return dataStream;
    }
}
