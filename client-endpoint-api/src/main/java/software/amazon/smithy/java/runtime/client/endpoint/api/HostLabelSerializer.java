/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoint.api;

import java.util.HashMap;
import java.util.Map;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.pattern.SmithyPattern;
import software.amazon.smithy.model.traits.HostLabelTrait;

final class HostLabelSerializer extends SpecificShapeSerializer implements ShapeSerializer {

    private final Map<String, String> labelMap = new HashMap<>();
    private final SmithyPattern hostLabelTemplate;
    private String prefix;

    HostLabelSerializer(SmithyPattern hostLabelTemplate) {
        this.hostLabelTemplate = hostLabelTemplate;
    }

    String prefix() {
        return prefix;
    }

    @Override
    public void flush() {
        var builder = new StringBuilder();
        for (var segment : hostLabelTemplate.getSegments()) {
            // Append as-is if not a label
            if (!segment.isLabel()) {
                builder.append(segment.getContent());
                continue;
            }
            var labelValue = labelMap.get(segment.getContent());
            if (labelValue == null) {
                throw new SerializationException("Could not find value for label `" + segment.getContent() + "`");
            }
            builder.append(labelValue);
        }
        prefix = builder.toString();
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        struct.serializeMembers(new ValueSerializer(this));
    }

    private static final class ValueSerializer extends SpecificShapeSerializer {
        private final HostLabelSerializer serializer;

        private ValueSerializer(HostLabelSerializer serializer) {
            this.serializer = serializer;
        }

        @Override
        public void writeString(Schema schema, String value) {
            if (!schema.hasTrait(HostLabelTrait.class)) {
                return;
            }
            serializer.labelMap.put(schema.memberName(), value);
        }
    }
}
