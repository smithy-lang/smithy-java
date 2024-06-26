/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jackson;

import java.io.IOException;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;

final class JacksonStructSerializer extends InterceptingSerializer {
    private final JacksonJsonSerializer parent;

    public JacksonStructSerializer(JacksonJsonSerializer parent) {
        this.parent = parent;
    }

    @Override
    protected ShapeSerializer before(Schema schema) {
        try {
            parent.generator.writeFieldName(parent.settings.fieldMapper().memberToField(schema));
        } catch (IOException e) {
            throw new SerializationException(e);
        }
        return parent;
    }
}
