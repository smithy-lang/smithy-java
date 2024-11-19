/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.io.datastream.DataStream;

public sealed interface Request permits RequestImpl {

    Context context();

    DataStream getDataStream();

    void setDataStream(DataStream dataStream);

    <T extends SerializableStruct> T getDeserializedValue();

    void setDeserializedValue(SerializableStruct serializableStruct);

}
