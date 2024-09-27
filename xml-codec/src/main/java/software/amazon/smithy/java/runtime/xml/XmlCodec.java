/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.xml;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.io.ByteBufferUtils;

/**
 * Serialize and deserialize XML documents.
 *
 * <p>This codec honors the xmlName, xmlAttribute, xmlFlattened, and xmlNamespace traits.
 */
public final class XmlCodec implements Codec {

    private final XMLInputFactory xmlInputFactory;
    private final XMLOutputFactory xmlOutputFactory;
    private final XmlInfo xmlInfo = new XmlInfo();

    private XmlCodec(Builder builder) {
        xmlInputFactory = XMLInputFactory.newInstance();
        xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        xmlInputFactory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        xmlInputFactory.setProperty(XMLInputFactory.IS_COALESCING, false);
        xmlOutputFactory = XMLOutputFactory.newInstance();
    }

    /**
     * Create a builder used to build an XmlCodec.
     *
     * @return the created builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ShapeSerializer createSerializer(OutputStream sink) {
        try {
            return new XmlSerializer(xmlOutputFactory.createXMLStreamWriter(sink), xmlInfo);
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ShapeDeserializer createDeserializer(ByteBuffer source) {
        try {
            var reader = xmlInputFactory.createXMLStreamReader(ByteBufferUtils.byteBufferInputStream(source));
            return new XmlDeserializer(xmlInfo, new XmlReader.StreamReader(reader, xmlInputFactory));
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Builder used to create an XML codec.
     */
    public static final class Builder {

        private Builder() {}

        /**
         * Create the codec and ensure all required settings are present.
         *
         * @return the codec.
         * @throws NullPointerException if any required settings are missing.
         */
        public Codec build() {
            return new XmlCodec(this);
        }
    }
}
