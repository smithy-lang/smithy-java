/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import com.jsoniter.output.JsonStream;
import com.jsoniter.spi.JsonException;
import java.io.IOException;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;

final class JsonMapSerializer implements MapSerializer {

    private final ShapeSerializer parent;
    private final JsonStream stream;
    private boolean wroteValue = false;

    JsonMapSerializer(ShapeSerializer parent, JsonStream stream) {
        this.parent = parent;
        this.stream = stream;
    }

    @Override
    public <T> void writeEntry(
        SdkSchema keySchema,
        String key,
        T state,
        BiConsumer<T, ShapeSerializer> valueSerializer
    ) {
        try {
            beforeWrite();
            stream.writeObjectField(key);
            valueSerializer.accept(state, parent);
        } catch (JsonException | IOException e) {
            throw new SdkSerdeException(e);
        }
    }

    // Write commas between entries.
    private void beforeWrite() throws IOException {
        if (wroteValue) {
            stream.writeMore();
        } else {
            wroteValue = true;
        }
    }
}
