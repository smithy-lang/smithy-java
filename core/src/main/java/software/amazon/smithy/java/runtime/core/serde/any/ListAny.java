/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.any;

import java.util.List;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

final class ListAny implements Any {

    private final SdkSchema schema;
    private final List<Any> value;

    ListAny(List<Any> value, SdkSchema schema) {
        this.value = value;
        this.schema = schema;
    }

    @Override
    public SdkSchema getSchema() {
        return schema;
    }

    @Override
    public ShapeType getType() {
        return ShapeType.LIST;
    }

    @Override
    public List<Any> asList() {
        return value;
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        encoder.beginList(schema, s -> {
            for (Any v : value) {
                v.serialize(s);
            }
        });
    }
}
