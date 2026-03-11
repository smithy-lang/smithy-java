/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.endpoint;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.pattern.SmithyPattern;

/**
 * Serializer that resolves the Host prefix from a template pattern and an input shape.
 *
 * <p>Host prefix templates can be defined by the {@code smithy.api#endpoint} trait and can use input shape members
 * marked with {@code smithy.api#hostLabel} trait as template parameters.
 *
 * @see <a href="https://smithy.io/2.0/spec/endpoint-traits.html#endpoint-trait">Endpoint Trait</a>
 */
final class HostLabelSerializer extends SpecificShapeSerializer implements ShapeSerializer {

    private final Map<String, String> labelMap = new HashMap<>();
    private final SmithyPattern hostLabelTemplate;
    private String prefix;

    private HostLabelSerializer(SmithyPattern hostLabelTemplate) {
        this.hostLabelTemplate = hostLabelTemplate;
    }

    /**
     * Resolve the host prefix template to a prefix string.
     *
     * @param hostLabelTemplate host prefix template to resolve.
     * @param inputShape input shape to use to resolve template parameters.
     * @return resolved host prefix
     */
    public static String resolvePrefix(SmithyPattern hostLabelTemplate, SerializableStruct inputShape) {
        // The prefix is static and can simply be prepended to endpoint host name with no templating.
        if (hostLabelTemplate.getLabels().isEmpty()) {
            return hostLabelTemplate.toString();
        } else {
            var serializer = new HostLabelSerializer(hostLabelTemplate);
            inputShape.serialize(serializer);
            serializer.flush();
            return serializer.prefix;
        }
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

    private record ValueSerializer(HostLabelSerializer serializer) implements ShapeSerializer {

        @Override
        public void writeString(Schema schema, String value) {
            if (!schema.hasTrait(TraitKey.HOST_LABEL_TRAIT)) {
                return;
            }
            serializer.labelMap.put(schema.memberName(), value);
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            // ignore
        }

        @Override
        public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
            // ignore
        }

        @Override
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            // ignore
        }

        @Override
        public void writeBoolean(Schema schema, boolean value) {
            // ignore
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            // ignore
        }

        @Override
        public void writeShort(Schema schema, short value) {
            // ignore
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            // ignore
        }

        @Override
        public void writeLong(Schema schema, long value) {
            // ignore
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            // ignore
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            // ignore
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            // ignore
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            // ignore
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            // ignore
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            // ignore
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            // ignore
        }

        @Override
        public void writeNull(Schema schema) {
            // ignore
        }
    }
}
