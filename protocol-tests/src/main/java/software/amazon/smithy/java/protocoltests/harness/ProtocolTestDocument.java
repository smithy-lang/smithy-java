/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.runtime.common.datastream.DataStream;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.core.serde.document.DocumentDeserializer;
import software.amazon.smithy.model.node.BooleanNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.NumberNode;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.StreamingTrait;

/**
 * This is a document format used in smithy protocol tests to model expected modeled values.
 */
final class ProtocolTestDocument implements Document {
    private static final Schema STRING_MAP_KEY = Schema.structureBuilder(PreludeSchemas.DOCUMENT.id())
        .putMember("key", PreludeSchemas.STRING)
        .build()
        .member("key");
    private final Node node;
    private final ShapeType type;
    private final Schema schema;
    private final String contentType;

    public ProtocolTestDocument(Node node, String contentType) {
        this.node = node;
        this.contentType = contentType;

        // Determine the type from the underlying JSON value.
        this.type = switch (node.getType()) {
            case NUMBER -> {
                if (node.expectNumberNode().isFloatingPointNumber()) {
                    yield ShapeType.DOUBLE;
                } else {
                    yield ShapeType.INTEGER;
                }
            }
            case ARRAY -> ShapeType.LIST;
            case OBJECT -> ShapeType.MAP;
            case STRING -> ShapeType.STRING;
            case BOOLEAN -> ShapeType.BOOLEAN;
            case NULL -> ShapeType.DOCUMENT;
            // The default case should never be reached.
            default -> throw new SerializationException("Expected JSON value: " + node.getType());
        };
        this.schema = PreludeSchemas.getSchemaForType(type);
    }

    @Override
    public ShapeType type() {
        return type;
    }

    @Override
    public boolean asBoolean() {
        return node.asBooleanNode().map(BooleanNode::getValue).orElseGet(Document.super::asBoolean);
    }

    @Override
    public byte asByte() {
        return node.asNumberNode().map(NumberNode::getValue).map(Number::byteValue).orElseGet(Document.super::asByte);
    }

    @Override
    public short asShort() {
        return node.asNumberNode().map(NumberNode::getValue).map(Number::shortValue).orElseGet(Document.super::asShort);
    }

    @Override
    public int asInteger() {
        return node.asNumberNode().map(NumberNode::getValue).map(Number::intValue).orElseGet(Document.super::asInteger);
    }

    @Override
    public long asLong() {
        return node.asNumberNode().map(NumberNode::getValue).map(Number::longValue).orElseGet(Document.super::asLong);
    }

    @Override
    public float asFloat() {
        if (node.isStringNode()) {
            switch (node.expectStringNode().getValue()) {
                case "NaN":
                    return Float.NaN;
                case "-Infinity":
                    return Float.NEGATIVE_INFINITY;
                case "Infinity":
                    return Float.POSITIVE_INFINITY;
                default:
                    Document.super.asFloat();
            }
        }
        return node.asNumberNode().map(NumberNode::getValue).map(Number::floatValue).orElseGet(Document.super::asFloat);
    }

    @Override
    public double asDouble() {
        if (node.isStringNode()) {
            switch (node.expectStringNode().getValue()) {
                case "NaN":
                    return Double.NaN;
                case "-Infinity":
                    return Double.NEGATIVE_INFINITY;
                case "Infinity":
                    return Double.POSITIVE_INFINITY;
                default:
                    Document.super.asFloat();
            }
        }
        return node.asNumberNode()
            .map(NumberNode::getValue)
            .map(Number::doubleValue)
            .orElseGet(Document.super::asDouble);
    }

    @Override
    public BigInteger asBigInteger() {
        if (node.isNullNode()) {
            return null;
        }
        return node.asNumberNode()
            .map(NumberNode::getValue)
            .map(n -> BigInteger.valueOf(n.longValue()))
            .orElseGet(Document.super::asBigInteger);
    }

    @Override
    public BigDecimal asBigDecimal() {
        if (node.isNullNode()) {
            return null;
        }
        return node.asNumberNode()
            .flatMap(NumberNode::asBigDecimal)
            .orElseGet(Document.super::asBigDecimal);
    }

    @Override
    public String asString() {
        if (node.isNullNode()) {
            return null;
        }
        return node.asStringNode().map(StringNode::getValue).orElseGet(Document.super::asString);
    }

    @Override
    public ByteBuffer asBlob() {
        if (node.isNullNode()) {
            return null;
        }
        if (!node.isStringNode()) {
            return Document.super.asBlob(); // this always fails
        } else {
            return ByteBuffer.wrap(node.expectStringNode().getValue().getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public Instant asTimestamp() {
        if (node.isNullNode()) {
            return null;
        }
        return node.asNumberNode()
            .map(NumberNode::getValue)
            .map(Number::longValue)
            .map(Instant::ofEpochSecond)
            .orElseGet(Document.super::asTimestamp);
    }

    @Override
    public List<Document> asList() {
        if (node.isNullNode()) {
            return null;
        }
        if (!node.isArrayNode()) {
            return Document.super.asList();
        }

        List<Document> result = new ArrayList<>();
        for (var value : node.expectArrayNode()) {
            result.add(new ProtocolTestDocument(value, contentType));
        }

        return result;
    }

    @Override
    public Map<String, Document> asStringMap() {
        if (node.isNullNode()) {
            return null;
        }
        if (!node.isObjectNode()) {
            return Document.super.asStringMap();
        } else {
            Map<String, Document> result = new LinkedHashMap<>();
            for (var entry : node.expectObjectNode().getMembers().entrySet()) {
                result.put(
                    entry.getKey().getValue(),
                    new ProtocolTestDocument(entry.getValue(), contentType)
                );
            }
            return result;
        }
    }

    @Override
    public Document getMember(String memberName) {
        if (node.isNullNode()) {
            return null;
        }
        if (node.isObjectNode()) {
            var memberDocument = node.expectObjectNode().getMember(memberName);
            if (memberDocument.isPresent() && !memberDocument.get().isNullNode()) {
                return new ProtocolTestDocument(memberDocument.get(), contentType);
            }
        }
        return null;
    }

    @Override
    public void serializeContents(ShapeSerializer encoder) {
        if (node.isNullNode()) {
            encoder.writeNull(schema);
            return;
        }
        switch (type()) {
            case BOOLEAN -> encoder.writeBoolean(schema, asBoolean());
            case INTEGER -> encoder.writeInteger(schema, asInteger());
            case DOUBLE -> encoder.writeDouble(schema, asDouble());
            case STRING -> encoder.writeString(schema, asString());
            case MAP -> encoder.writeMap(schema, asStringMap(), asStringMap().size(), (values, mapSerializer) -> {
                for (var entry : values.entrySet()) {
                    mapSerializer.writeEntry(
                        STRING_MAP_KEY,
                        entry.getKey(),
                        entry.getValue(),
                        Document::serializeContents
                    );
                }
            });
            case LIST -> encoder.writeList(schema, asList(), asList().size(), (list, c) -> {
                for (Document entry : list) {
                    entry.serializeContents(c);
                }
            });
            // When type is set in the ctor, it only allows the above types; the default switch should never happen.
            default -> throw new SerializationException("Cannot serialize unexpected JSON value: " + node);
        }
    }

    @Override
    public <T extends SerializableShape> void deserializeInto(ShapeBuilder<T> builder) {
        builder.deserialize(new ProtocolTestDocumentDeserializer(this, contentType));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProtocolTestDocument that = (ProtocolTestDocument) o;
        return Objects.equals(node, that.node) && type == that.type && Objects.equals(schema, that.schema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node, type, schema);
    }

    @Override
    public String toString() {
        return "ProtocolTestDocument{" +
            "node=" + node +
            ", type=" + type +
            ", schema=" + schema +
            '}';
    }

    /**
     * Customized version of DocumentDeserializer to account for the settings of the JSON codec.
     */
    private static final class ProtocolTestDocumentDeserializer extends DocumentDeserializer {

        private final ProtocolTestDocument jsonDocument;
        private final String contentType;
        private DataStream dataStream;

        ProtocolTestDocumentDeserializer(ProtocolTestDocument value, String contentType) {
            super(value);
            this.jsonDocument = value;
            this.contentType = contentType;
        }

        @Override
        protected DocumentDeserializer deserializer(Document nextValue) {
            return new ProtocolTestDocumentDeserializer((ProtocolTestDocument) nextValue, contentType);
        }

        @Override
        public <T> void readStruct(
            Schema schema,
            T state,
            ShapeDeserializer.StructMemberConsumer<T> structMemberConsumer
        ) {
            for (var member : schema.members()) {
                var nextValue = jsonDocument.getMember(member.memberName());
                if (nextValue != null) {
                    if (member.memberTarget().hasTrait(StreamingTrait.class)) {
                        dataStream = DataStream.ofByteBuffer(nextValue.asBlob(), contentType);
                    } else {
                        structMemberConsumer.accept(state, member, deserializer(nextValue));
                    }
                }
            }
        }

        @Override
        public boolean isNull() {
            return jsonDocument.node.isNullNode();
        }

        @Override
        public <T> T readNull() {
            if (!jsonDocument.node.isNullNode()) {
                throw new SerializationException("Attempted to read non-null value as null");
            }
            return null;
        }

        public DataStream getDataStream() {
            return dataStream;
        }
    }
}
