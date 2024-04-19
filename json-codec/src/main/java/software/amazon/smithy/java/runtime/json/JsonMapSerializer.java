/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import com.jsoniter.output.JsonStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.RequiredWriteSerializer;
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
    public void writeEntry(Schema keySchema, String key, Consumer<ShapeSerializer> valueSerializer) {
        try {
            beforeWrite();
            stream.writeObjectField(key);
            RequiredWriteSerializer.assertWrite(
                parent,
                () -> new IllegalStateException("Map member value was not written for key: " + key),
                valueSerializer
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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

    @Override
    public void writeEntry(Schema keySchema, int key, Consumer<ShapeSerializer> valueSerializer) {
        writeEntry(keySchema, Integer.toString(key), valueSerializer);
    }

    @Override
    public void writeEntry(Schema keySchema, long key, Consumer<ShapeSerializer> valueSerializer) {
        writeEntry(keySchema, Long.toString(key), valueSerializer);
    }
}
