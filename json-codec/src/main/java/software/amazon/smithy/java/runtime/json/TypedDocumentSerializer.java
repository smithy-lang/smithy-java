/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import com.jsoniter.spi.JsonException;
import java.io.IOException;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Injects __type into typed structure documents.
 */
final class TypedDocumentSerializer extends InterceptingSerializer {

    private final JsonSerializer delegate;

    TypedDocumentSerializer(JsonSerializer delegate) {
        this.delegate = delegate;
    }

    @Override
    protected ShapeSerializer before(SdkSchema schema) {
        if (schema.type() != ShapeType.STRUCTURE) {
            return delegate;
        }

        return new SpecificShapeSerializer() {
            @Override
            public void writeStruct(SdkSchema schema, SerializableStruct struct) {
                try {
                    delegate.stream.writeObjectStart();
                    delegate.stream.writeObjectField("__type");
                    delegate.stream.writeVal(schema.id().toString());
                    struct.serializeMembers(new JsonStructSerializer(delegate, false));
                    delegate.stream.writeObjectEnd();
                } catch (IOException | JsonException e) {
                    throw new SdkSerdeException(e);
                }
            }
        };
    }
}
