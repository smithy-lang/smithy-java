/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.BiConsumer;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

final class XmlSerializer extends InterceptingSerializer {

    private static final TimestampFormatTrait.Format DEFAULT_FORMAT = TimestampFormatTrait.Format.DATE_TIME;

    private final XmlInfo xmlInfo;
    private final XMLStreamWriter writer;
    private final NonFlattenedMemberSerializer nonFlattenedMemberSerializer = new NonFlattenedMemberSerializer();
    private final ValueSerializer valueSerializer = new ValueSerializer();
    private final StructMemberSerializer structMemberSerializer = new StructMemberSerializer();
    private final StructAttributeSerializer structAttributeSerializer = new StructAttributeSerializer();
    private final AttributeSerializer attributeSerializer = new AttributeSerializer();

    XmlSerializer(XMLStreamWriter writer, XmlInfo xmlInfo) {
        this.writer = writer;
        this.xmlInfo = xmlInfo;
    }

    // Handles writing top-level shapes that are not members. The element uses xmlName or the shape name.
    @Override
    protected ShapeSerializer before(Schema schema) {
        try {
            // Top-level members are things like httpPayload members. They peek-through to the target shape xmlName.
            String xmlName;
            var trait = schema.getTrait(TraitKey.XML_NAME_TRAIT);
            if (trait != null) {
                xmlName = trait.getValue();
            } else if (schema.isMember()) {
                xmlName = schema.memberTarget().id().getName();
            } else {
                xmlName = schema.id().getName();
            }

            writer.writeStartElement(xmlName);

            // Add a namespace if present, and peek-through to the target shape for a namespace when it's a member.
            var ns = schema.getTrait(TraitKey.XML_NAMESPACE_TRAIT);
            if (ns != null) {
                writer.writeNamespace(ns.getPrefix().orElse(null), ns.getUri());
            }

            return valueSerializer;
        } catch (XMLStreamException e) {
            throw new SerializationException(e);
        }
    }

    // Close the top-level shape element.
    @Override
    protected void after(Schema schema) {
        try {
            writer.writeEndElement();
        } catch (XMLStreamException e) {
            throw new SerializationException(e);
        }
    }

    private static String formatTimestamp(Schema schema, Instant value) {
        return TimestampFormatter.of(
                schema.getTrait(TraitKey.TIMESTAMP_FORMAT_TRAIT),
                DEFAULT_FORMAT).writeString(value);
    }

    // Write to structure elements that are _not_ attributes.
    private final class StructMemberSerializer extends InterceptingSerializer {
        @Override
        protected ShapeSerializer before(Schema schema) {
            if (schema.hasTrait(TraitKey.XML_ATTRIBUTE_TRAIT)) {
                // Attributes have to be written before writing non-attribute members.
                return ShapeSerializer.nullSerializer();
            } else if (schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT)) {
                return valueSerializer;
            } else {
                return nonFlattenedMemberSerializer;
            }
        }
    }

    // Write to structure attributes.
    private final class StructAttributeSerializer extends InterceptingSerializer {
        @Override
        protected ShapeSerializer before(Schema schema) {
            if (schema.hasTrait(TraitKey.XML_ATTRIBUTE_TRAIT)) {
                return attributeSerializer;
            } else {
                return ShapeSerializer.nullSerializer();
            }
        }
    }

    // Writes members by writing their containing element, their value, then the closing element.
    // Note that flattened structure members skip this writer and just use MemberValueSerializer.
    private final class NonFlattenedMemberSerializer extends InterceptingSerializer {
        @Override
        protected ShapeSerializer before(Schema schema) {
            try {
                writeStart(writer, getMemberXmlName(schema), schema);
                return valueSerializer;
            } catch (XMLStreamException e) {
                throw new SerializationException(e);
            }
        }

        @Override
        protected void after(Schema schema) {
            try {
                writer.writeEndElement();
            } catch (XMLStreamException e) {
                throw new SerializationException(e);
            }
        }
    }

    private static void writeStart(XMLStreamWriter writer, String xmlName, Schema schema) throws XMLStreamException {
        writer.writeStartElement(xmlName);

        // Add a namespace if present.
        var ns = schema.getDirectTrait(TraitKey.XML_NAMESPACE_TRAIT);
        if (ns != null) {
            writer.writeNamespace(ns.getPrefix().orElse(null), ns.getUri());
        }
    }

    private static String getMemberXmlName(Schema schema) {
        var trait = schema.getDirectTrait(TraitKey.XML_NAME_TRAIT);
        if (trait != null) {
            return trait.getValue();
        } else if (schema.isMember()) {
            return schema.memberName();
        } else {
            throw new IllegalArgumentException("Expected member schema in XML serializer, found " + schema);
        }
    }

    // Serialize member values. This does not open and close a containing element.
    private final class ValueSerializer implements ShapeSerializer {
        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            var structInfo = xmlInfo.getStructInfo(schema);
            // Serialize attributes first; they have to occur before writing siblings and closing the opening node.
            if (!structInfo.attributes.isEmpty()) {
                struct.serializeMembers(structAttributeSerializer);
            }
            // Now serialize nested elements.
            struct.serializeMembers(structMemberSerializer);
        }

        @Override
        public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
            var info = xmlInfo.getListInfo(schema);
            consumer.accept(listState, new XmlListItemSerializer(info));
        }

        @Override
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            var info = xmlInfo.getMapInfo(schema);
            consumer.accept(mapState, new XmlMapEntrySerializer(info));
        }

        @Override
        public void writeBoolean(Schema schema, boolean value) {
            write(value ? "true" : "false");
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            write(Byte.toString(value));
        }

        @Override
        public void writeShort(Schema schema, short value) {
            write(Short.toString(value));
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            write(Integer.toString(value));
        }

        @Override
        public void writeLong(Schema schema, long value) {
            write(Long.toString(value));
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            write(Float.toString(value));
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            write(Double.toString(value));
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            write(value.toString());
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            write(value.toString());
        }

        @Override
        public void writeString(Schema schema, String value) {
            write(value);
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            write(ByteBufferUtils.base64Encode(value));
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            write(formatTimestamp(schema, value));
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            // do nothing
        }

        @Override
        public void writeNull(Schema schema) {
            // do nothing
        }

        private void write(String value) {
            try {
                writer.writeCharacters(value);
            } catch (XMLStreamException e) {
                throw new SerializationException(e);
            }
        }
    }

    // Serialize XML attributes of structures and unions.
    private final class AttributeSerializer extends SpecificShapeSerializer {
        @Override
        public void writeBoolean(Schema schema, boolean value) {
            write(schema, value ? "true" : "false");
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            write(schema, Byte.toString(value));
        }

        @Override
        public void writeShort(Schema schema, short value) {
            write(schema, Short.toString(value));
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            write(schema, Integer.toString(value));
        }

        @Override
        public void writeLong(Schema schema, long value) {
            write(schema, Long.toString(value));
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            write(schema, Float.toString(value));
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            write(schema, Double.toString(value));
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            write(schema, value.toString());
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            write(schema, value.toString());
        }

        @Override
        public void writeString(Schema schema, String value) {
            write(schema, value);
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            write(schema, formatTimestamp(schema, value));
        }

        @Override
        public void writeNull(Schema schema) {
            // do nothing
        }

        private void write(Schema schema, String value) {
            try {
                writer.writeAttribute(getMemberXmlName(schema), value);
            } catch (XMLStreamException e) {
                throw new SerializationException(e);
            }
        }
    }

    // Wrap list member values in the "item" node.
    private final class XmlListItemSerializer extends InterceptingSerializer {

        private final XmlInfo.ListMemberInfo info;

        XmlListItemSerializer(XmlInfo.ListMemberInfo info) {
            this.info = info;
        }

        @Override
        protected ShapeSerializer before(Schema schema) {
            try {
                writeStart(writer, info.memberName, schema);
                return valueSerializer;
            } catch (XMLStreamException e) {
                throw new SerializationException(e);
            }
        }

        @Override
        protected void after(Schema schema) {
            try {
                writer.writeEndElement();
            } catch (XMLStreamException e) {
                throw new SerializationException(e);
            }
        }
    }

    private final class XmlMapEntrySerializer implements MapSerializer {

        private final XmlInfo.MapMemberInfo info;

        XmlMapEntrySerializer(XmlInfo.MapMemberInfo info) {
            this.info = info;
        }

        @Override
        public <T> void writeEntry(
                Schema keySchema,
                String key,
                T state,
                BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            try {
                // Write the "<entry>" element.
                writer.writeStartElement(info.entryName);

                // Write the "<key>" element.
                writeStart(writer, info.keyName, keySchema);
                writer.writeCharacters(key);
                writer.writeEndElement();

                // The <value> element is opened and closed by the nonFlattenedMemberSerializer.
                valueSerializer.accept(state, nonFlattenedMemberSerializer);

                writer.writeEndElement();
            } catch (XMLStreamException e) {
                throw new SerializationException(e);
            }
        }
    }
}
