/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jackson;

import java.io.IOException;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;

final class JacksonMapSerializer implements MapSerializer {
    private final JacksonJsonSerializer parent;

    public JacksonMapSerializer(JacksonJsonSerializer parent) {
        this.parent = parent;
    }

    @Override
    public <T> void writeEntry(Schema keySchema, String key, T state, BiConsumer<T, ShapeSerializer> valueSerializer) {
        try {
            parent.generator.writeFieldName(key);
        } catch (IOException e) {
            throw new SerializationException(e);
        }
        valueSerializer.accept(state, parent);
    }
}
