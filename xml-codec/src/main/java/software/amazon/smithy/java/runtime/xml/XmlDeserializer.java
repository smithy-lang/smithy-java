/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.xml;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import javax.xml.stream.XMLStreamException;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.TraitKey;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.TimestampFormatter;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

final class XmlDeserializer implements ShapeDeserializer {

    private final XmlInfo xmlInfo;
    private final XmlReader reader;
    private final InnerDeserializer innerDeserializer;
    private final boolean isTopLevel;

    static XmlDeserializer topLevel(XmlInfo xmlInfo, XmlReader reader) throws XMLStreamException {
        return new XmlDeserializer(xmlInfo, reader, true);
    }

    static XmlDeserializer flattened(XmlInfo xmlInfo, XmlReader reader) throws XMLStreamException {
        return new XmlDeserializer(xmlInfo, reader, false);
    }

    private XmlDeserializer(XmlInfo xmlInfo, XmlReader reader, boolean isTopLevel) throws XMLStreamException {
        this.xmlInfo = xmlInfo;
        this.reader = reader;
        this.isTopLevel = isTopLevel;
        this.innerDeserializer = new InnerDeserializer();
    }

    @Override
    public void close() {
        try {
            reader.close();
        } catch (Exception e) {
            throw error(e.getMessage(), e);
        }
    }

    // The first deserialization of XML expects a containing XML element for the shape.
    // The inner deserializer deserializes members and doesn't have this expectation.
    private void enter(Schema schema) {
        try {
            // Always go to the next member element, even if deserializing a nested element.
            var name = reader.nextMemberElement();

            if (!isTopLevel) {
                // List and map members are validated in those deserializers, not here.
                return;
            }

            String expected;
            var trait = schema.getTrait(TraitKey.XML_NAME_TRAIT);
            if (trait != null) {
                expected = trait.getValue();
            } else if (schema.isMember()) {
                expected = schema.memberTarget().id().getName();
            } else {
                expected = schema.id().getName();
            }

            if (!expected.equals(name)) {
                throw new SerializationException("Expected XML element named '" + expected + "', found " + name);
            }
        } catch (XMLStreamException e) {
            throw new SerializationException(e);
        }
    }

    private void exit() {
        try {
            reader.closeElement();
        } catch (XMLStreamException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public boolean readBoolean(Schema schema) {
        enter(schema);
        var result = innerDeserializer.readBoolean(schema);
        exit();
        return result;
    }

    @Override
    public ByteBuffer readBlob(Schema schema) {
        enter(schema);
        var result = innerDeserializer.readBlob(schema);
        exit();
        return result;
    }

    @Override
    public byte readByte(Schema schema) {
        enter(schema);
        var result = innerDeserializer.readByte(schema);
        exit();
        return result;
    }

    @Override
    public short readShort(Schema schema) {
        enter(schema);
        var result = innerDeserializer.readShort(schema);
        exit();
        return result;
    }

    @Override
    public int readInteger(Schema schema) {
        enter(schema);
        var result = innerDeserializer.readInteger(schema);
        exit();
        return result;
    }

    @Override
    public long readLong(Schema schema) {
        enter(schema);
        var result = innerDeserializer.readLong(schema);
        exit();
        return result;
    }

    @Override
    public float readFloat(Schema schema) {
        enter(schema);
        var result = innerDeserializer.readFloat(schema);
        exit();
        return result;
    }

    @Override
    public double readDouble(Schema schema) {
        enter(schema);
        var result = innerDeserializer.readDouble(schema);
        exit();
        return result;
    }

    @Override
    public BigInteger readBigInteger(Schema schema) {
        enter(schema);
        var result = innerDeserializer.readBigInteger(schema);
        exit();
        return result;
    }

    @Override
    public BigDecimal readBigDecimal(Schema schema) {
        enter(schema);
        var result = innerDeserializer.readBigDecimal(schema);
        exit();
        return result;
    }

    @Override
    public String readString(Schema schema) {
        enter(schema);
        var result = innerDeserializer.readString(schema);
        exit();
        return result;
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        enter(schema);
        var result = innerDeserializer.readTimestamp(schema);
        exit();
        return result;
    }

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
        enter(schema);
        innerDeserializer.readStruct(schema, state, consumer);
        exit();
    }

    @Override
    public <T> void readList(Schema schema, T state, ListMemberConsumer<T> consumer) {
        enter(schema);
        innerDeserializer.readList(schema, state, consumer);
        exit();
    }

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer) {
        enter(schema);
        innerDeserializer.readStringMap(schema, state, consumer);
        exit();
    }

    @Override
    public boolean isNull() {
        return innerDeserializer.isNull();
    }

    @Override
    public <T> T readNull() {
        if (!innerDeserializer.isNull()) {
            throw new SerializationException("Expected null, found " + reader);
        }
        return null;
    }

    @Override
    public Document readDocument() {
        return null;
    }

    private SerializationException error(String message) {
        return error(message, null);
    }

    private SerializationException error(String message, Exception previous) {
        return error(this.reader, message, previous);
    }

    static SerializationException error(XmlReader reader, String message, Exception previous) {
        var location = reader.getLocation();
        String msg = location == null
            ? ("Error reading XML: " + message)
            : ("Error reading XML near line " + location.getLineNumber()
                + ", column " + location.getColumnNumber() + ": " + message);
        return new SerializationException(msg, previous);
    }

    private static boolean parseBoolean(XmlReader reader, String value) {
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw error(reader, "Expected boolean 'true' or 'false'", null);
        };
    }

    private static Instant parseTimestamp(Schema schema, String value, XmlReader reader) {
        try {
            return TimestampFormatter.of(schema, TimestampFormatTrait.Format.DATE_TIME).readFromString(value, false);
        } catch (TimestampFormatter.TimestampSyntaxError e) {
            throw error(reader, "Failed to read timestamp: " + e.getMessage(), e);
        } catch (DateTimeException e) {
            throw error(reader, "Failed to read timestamp", e);
        }
    }

    private static final class AttributeDeserializer extends SpecificShapeDeserializer {

        private final XmlReader reader;
        private final String value;

        private AttributeDeserializer(XmlReader reader, String value) {
            this.reader = reader;
            this.value = value;
        }

        @Override
        public boolean readBoolean(Schema schema) {
            return parseBoolean(reader, value);
        }

        @Override
        public String readString(Schema schema) {
            return value;
        }

        @Override
        public Instant readTimestamp(Schema schema) {
            return parseTimestamp(schema, value, reader);
        }

        @Override
        public byte readByte(Schema schema) {
            return Byte.parseByte(value);
        }

        @Override
        public BigDecimal readBigDecimal(Schema schema) {
            return new BigDecimal(value);
        }

        @Override
        public BigInteger readBigInteger(Schema schema) {
            return new BigInteger(value);
        }

        @Override
        public double readDouble(Schema schema) {
            return Double.parseDouble(value);
        }

        @Override
        public float readFloat(Schema schema) {
            return Float.parseFloat(value);
        }

        @Override
        public int readInteger(Schema schema) {
            return Integer.parseInt(value);
        }

        @Override
        public long readLong(Schema schema) {
            return Long.parseLong(value);
        }

        @Override
        public short readShort(Schema schema) {
            return Short.parseShort(value);
        }
    }

    private final class InnerDeserializer implements ShapeDeserializer {
        @Override
        public Document readDocument() {
            return null;
        }

        @Override
        public boolean readBoolean(Schema schema) {
            try {
                return parseBoolean(reader, reader.getText());
            } catch (XMLStreamException e) {
                throw error("Failed to read boolean", e);
            }
        }

        @Override
        public ByteBuffer readBlob(Schema schema) {
            try {
                return ByteBuffer.wrap(Base64.getDecoder().decode(reader.getText()));
            } catch (XMLStreamException | IllegalArgumentException e) {
                throw error("Failed to read blob", e);
            }
        }

        @Override
        public byte readByte(Schema schema) {
            try {
                return Byte.parseByte(reader.getText());
            } catch (XMLStreamException | NumberFormatException e) {
                throw error("Failed to read byte", e);
            }
        }

        @Override
        public short readShort(Schema schema) {
            try {
                return Short.parseShort(reader.getText());
            } catch (XMLStreamException | NumberFormatException e) {
                throw error("Failed to read short", e);
            }
        }

        @Override
        public int readInteger(Schema schema) {
            try {
                return Integer.parseInt(reader.getText());
            } catch (XMLStreamException | NumberFormatException e) {
                throw error("Failed to read integer", e);
            }
        }

        @Override
        public long readLong(Schema schema) {
            try {
                return Long.parseLong(reader.getText());
            } catch (XMLStreamException | NumberFormatException e) {
                throw error("Failed to read long", e);
            }
        }

        @Override
        public float readFloat(Schema schema) {
            try {
                return Float.parseFloat(reader.getText());
            } catch (XMLStreamException | NumberFormatException e) {
                throw error("Failed to read float", e);
            }
        }

        @Override
        public double readDouble(Schema schema) {
            try {
                return Double.parseDouble(reader.getText());
            } catch (XMLStreamException | NumberFormatException e) {
                throw error("Failed to read double", e);
            }
        }

        @Override
        public BigInteger readBigInteger(Schema schema) {
            try {
                return new BigInteger(reader.getText());
            } catch (XMLStreamException | NumberFormatException e) {
                throw error("Failed to read BigInteger", e);
            }
        }

        @Override
        public BigDecimal readBigDecimal(Schema schema) {
            try {
                return new BigDecimal(reader.getText());
            } catch (XMLStreamException | NumberFormatException e) {
                throw error("Failed to read BigDecimal", e);
            }
        }

        @Override
        public String readString(Schema schema) {
            try {
                return reader.getText();
            } catch (XMLStreamException e) {
                throw error("Failed to read string", e);
            }
        }

        @Override
        public Instant readTimestamp(Schema schema) {
            try {
                return parseTimestamp(schema, reader.getText(), reader);
            } catch (XMLStreamException e) {
                throw error("Failed to read timestamp", e);
            }
        }

        @Override
        public boolean isNull() {
            try {
                return reader.getText().isEmpty();
            } catch (XMLStreamException e) {
                throw error("Failed to determine if value is null", e);
            }
        }

        @Override
        public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
            try {
                var decoder = xmlInfo.getStructInfo(schema);
                readStructAttributes(decoder, state, consumer);

                // Create a state object to buffer flattened values so they can be emitted all at once.
                // Operations like S3's ListObjectVersions contain multiple interspersed flattened lists.
                var flattenedState = decoder.createFlattenedState();

                for (var member = reader.nextMemberElement(); member != null; member = reader.nextMemberElement()) {
                    Schema elementSchema = decoder.elements.get(member);
                    if (elementSchema != null) {
                        decoder.readMember(flattenedState, this, reader, state, consumer, member, elementSchema);
                        reader.closeElement();
                    } else {
                        consumer.unknownMember(state, member);
                        reader.closeElement();
                    }
                }

                decoder.finishReadingStruct(flattenedState, xmlInfo, state, consumer);

            } catch (XMLStreamException e) {
                throw error("Failed to read struct", e);
            }
        }

        private <T> void readStructAttributes(XmlInfo.StructInfo decoder, T state, StructMemberConsumer<T> consumer) {
            for (var entry : decoder.attributes.entrySet()) {
                String attributeName = entry.getKey();
                Schema attributeSchema = entry.getValue();
                String attributeValue = reader.getAttributeValue(null, attributeName);
                if (attributeValue != null) {
                    try {
                        consumer.accept(state, attributeSchema, new AttributeDeserializer(reader, attributeValue));
                    } catch (NumberFormatException e) {
                        throw error("Failed to parse " + decoder.schema.type() + " attribute", e);
                    }
                }
            }
        }

        @Override
        public <T> void readList(Schema schema, T state, ListMemberConsumer<T> consumer) {
            try {
                var info = xmlInfo.getListInfo(schema);
                for (var member = reader.nextMemberElement(); member != null; member = reader.nextMemberElement()) {
                    if (!member.equals(info.memberName)) {
                        throw error(
                            String.format(
                                "Expected list item '%s' but found '%s': %s",
                                info.memberName,
                                member,
                                reader
                            )
                        );
                    }
                    consumer.accept(state, this);
                    reader.closeElement(); // Close the list member.
                }
            } catch (XMLStreamException e) {
                throw error("Failed to read list", e);
            }
        }

        @Override
        public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer) {
            try {
                var decoder = xmlInfo.getMapInfo(schema);

                for (var member = reader.nextMemberElement(); member != null; member = reader.nextMemberElement()) {
                    // Verify the element name matches expected map entry name.
                    if (!decoder.entryName.equals(member)) {
                        // Handle non-flattened maps by breaking on unexpected elements.
                        if (!decoder.flattened) {
                            break;
                        } else {
                            throw error("Unexpected element in map: " + reader);
                        }
                    }

                    // Open the key element.
                    var keyElement = reader.nextMemberElement();
                    if (keyElement == null) {
                        throw error("Expected map key, but map unexpectedly closed");
                    } else if (!keyElement.equals(decoder.keyName)) {
                        throw error(String.format("Expected map key '%s' but found %s", decoder.keyName, reader));
                    }
                    // Consume the key content.
                    var key = reader.getText();
                    // Close the key element.
                    reader.closeElement();

                    // Open the value element.
                    var valueElement = reader.nextMemberElement();
                    if (valueElement == null) {
                        throw error("Expected map value, but map unexpectedly closed");
                    } else if (!decoder.valueName.equals(valueElement)) {
                        throw error(String.format("Expected map value '%s' but found %s", decoder.valueName, reader));
                    }
                    // Consume the value content.
                    consumer.accept(state, key, this);
                    // Close the value element.
                    reader.closeElement();

                    // The </entry> is not closed by the consumer but the map container is,
                    // so close one element.
                    reader.closeElement();
                }
            } catch (XMLStreamException e) {
                throw error("Failed to read map", e);
            }
        }
    }
}
