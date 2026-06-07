/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.model.traits.XmlNamespaceTrait;

/**
 * Serialize and deserialize XML documents.
 *
 * <p>This codec honors the xmlName, xmlAttribute, xmlFlattened, and xmlNamespace traits.
 */
public final class XmlCodec implements Codec {

    private static final boolean USE_SMITHY_NATIVE =
            "smithy".equals(System.getProperty("smithy-java.xml-provider"));

    private volatile XMLInputFactory xmlInputFactory;
    private volatile XMLOutputFactory xmlOutputFactory;
    private volatile XMLEventFactory eventFactory;
    private final XmlInfo xmlInfo = new XmlInfo();
    private final List<String> wrapperElements;
    private final XmlNamespaceTrait defaultNamespace;
    private final boolean useNative;

    private XmlCodec(Builder builder) {
        this.wrapperElements = builder.wrapperElements;
        this.defaultNamespace = builder.defaultNamespace;
        this.useNative = builder.useNative != null ? builder.useNative : USE_SMITHY_NATIVE;
        if (!useNative) {
            initStax();
        }
    }

    private void initStax() {
        xmlInputFactory = XMLInputFactory.newInstance();
        xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        xmlInputFactory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        xmlInputFactory.setProperty(XMLInputFactory.IS_COALESCING, false);
        xmlOutputFactory = XMLOutputFactory.newInstance();
        eventFactory = XMLEventFactory.newInstance();
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
        if (useNative) {
            return new LazyXmlSerializer(defaultNamespace, xmlInfo, sink);
        }
        try {
            return new XmlSerializer(xmlOutputFactory.createXMLStreamWriter(sink), xmlInfo, defaultNamespace);
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ShapeDeserializer createDeserializer(ByteBuffer source) {
        if (source == null || !source.hasRemaining()) {
            return EmptyXmlDeserializer.INSTANCE;
        }

        if (useNative) {
            byte[] bytes;
            int offset;
            int length = source.remaining();
            if (source.hasArray()) {
                bytes = source.array();
                offset = source.arrayOffset() + source.position();
            } else {
                bytes = ByteBufferUtils.getBytes(source);
                offset = 0;
            }
            return new SmithyXmlDeserializer(bytes, offset, length, xmlInfo, true, wrapperElements);
        }

        try {
            var reader = xmlInputFactory.createXMLStreamReader(ByteBufferUtils.byteBufferInputStream(source));
            return XmlDeserializer.topLevel(
                    xmlInfo,
                    eventFactory,
                    new XmlReader.StreamReader(reader, xmlInputFactory),
                    wrapperElements);
        } catch (XMLStreamException e) {
            throw new SerializationException(e);
        }
    }

    /**
     * Builder used to create an XML codec.
     */
    public static final class Builder {
        private List<String> wrapperElements = List.of();
        private XmlNamespaceTrait defaultNamespace;
        private Boolean useNative;

        private Builder() {}

        /**
         * Configure wrapper elements to skip during deserialization.
         *
         * <p>When deserializing, these elements are skipped in order at the top level only
         * before reading the actual content. This is useful for protocols like AWS Query
         * where responses are wrapped in elements like {@code <OperationNameResponse>}
         * and {@code <OperationNameResult>}.
         *
         * <p>The elements must match exactly (not by suffix) and are only skipped at
         * the top level, not for nested structures.
         *
         * @param wrapperElements the list of wrapper element names to skip, in order
         * @return the builder
         */
        public Builder wrapperElements(List<String> wrapperElements) {
            this.wrapperElements = wrapperElements;
            return this;
        }

        /**
         * Sets a default XML namespace to apply to top-level elements during serialization.
         *
         * @param defaultNamespace the default namespace trait
         * @return the builder
         */
        public Builder defaultNamespace(XmlNamespaceTrait defaultNamespace) {
            this.defaultNamespace = defaultNamespace;
            return this;
        }

        /**
         * Override the native provider selection for testing. When set to true, the native
         * (high-performance) implementation is used regardless of system property. When false,
         * the StAX implementation is used.
         */
        Builder useNative(boolean useNative) {
            this.useNative = useNative;
            return this;
        }

        /**
         * Create the codec and ensure all required settings are present.
         *
         * @return the codec.
         * @throws NullPointerException if any required settings are missing.
         */
        public XmlCodec build() {
            return new XmlCodec(this);
        }
    }
}
