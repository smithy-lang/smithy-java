/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.any;

import java.util.Map;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.model.shapes.ShapeType;

final class StructAny implements Any {

    private final SdkSchema schema;
    private final Map<String, Any> value;

    StructAny(Map<String, Any> value, SdkSchema schema) {
        this.value = value;
        this.schema = schema;
    }

    @Override
    public SdkSchema getSchema() {
        return schema;
    }

    @Override
    public ShapeType getType() {
        return schema.type() != ShapeType.DOCUMENT ? schema.type() : ShapeType.STRUCTURE;
    }

    @Override
    public Any getStructMember(String memberName) {
        return value.get(memberName);
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        encoder.beginStruct(schema, structSerializer -> {
            for (var entry : value.entrySet()) {
                structSerializer.member(entry.getValue().getSchema(), valueWriter -> entry.getValue()
                        .serialize(valueWriter));
            }
        });
    }
}
