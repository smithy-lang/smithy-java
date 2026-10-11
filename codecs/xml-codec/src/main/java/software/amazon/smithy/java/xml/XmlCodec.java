/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import software.amazon.smithy.java.codecs.commons.OutputBuffers;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.traits.XmlNamespaceTrait;

/**
 * Serialize and deserialize XML documents.
 *
 * <p>This codec honors the xmlName, xmlAttribute, xmlFlattened, and xmlNamespace traits.
 */
public final class XmlCodec implements Codec {

    private static final boolean USE_SMITHY_NATIVE =
            !"stax".equals(System.getProperty("smithy-java.xml-provider"));

    private volatile XMLInputFactory xmlInputFactory;
    private volatile XMLOutputFactory xmlOutputFactory;
    private volatile XMLEventFactory eventFactory;
    private final XmlInfo xmlInfo = new XmlInfo();
    private final List<String> wrapperElements;
    private final XmlNamespaceTrait defaultNamespace;
    private final boolean useNative;
    private final boolean strictRootElement;
    private final OutputBuffers outputBuffers;

    private XmlCodec(Builder builder) {
        this.wrapperElements = builder.wrapperElements;
        this.defaultNamespace = builder.defaultNamespace;
        this.strictRootElement = builder.strictRootElement;
        this.outputBuffers = builder.outputBuffers;
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

    public OutputBuffers outputBuffers() {
        return outputBuffers;
    }

    @Override
    public DataStream serializeToDataStream(SerializableShape shape, String contentType) {
        if (!useNative) {
            return Codec.super.serializeToDataStream(shape, contentType);
        }
        var serializer = SmithyXmlSerializer.acquire(defaultNamespace, xmlInfo, null, outputBuffers);
        try {
            shape.serialize(serializer);
            return serializer.detach(contentType);
        } finally {
            SmithyXmlSerializer.release(serializer);
        }
    }

    @Override
    public ShapeSerializer createSerializer(OutputStream sink) {
        if (useNative) {
            return new LazyXmlSerializer(defaultNamespace, xmlInfo, sink, outputBuffers);
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
            return new SmithyXmlDeserializer(bytes,
                    offset,
                    length,
                    xmlInfo,
                    true,
                    wrapperElements,
                    strictRootElement);
        }

        try {
            var reader = xmlInputFactory.createXMLStreamReader(ByteBufferUtils.byteBufferInputStream(source));
            return XmlDeserializer.topLevel(
                    xmlInfo,
                    eventFactory,
                    new XmlReader.StreamReader(reader, xmlInputFactory),
                    wrapperElements,
                    strictRootElement);
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
        private boolean strictRootElement = true;
        private OutputBuffers outputBuffers = OutputBuffers.shared();

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
         * Whether the root element name must match the deserialized shape's expected name.
         *
         * <p>When {@code true} (default), a mismatched root element throws; use this for servers. When
         * {@code false}, the mismatch is tolerated and the children are read regardless; use this for
         * clients, where a service may return a wrapper name that differs from the modeled output shape
         * (e.g. S3's {@code <CopyObjectResult>} for {@code CopyObjectOutput}).
         *
         * @param strictRootElement true to require an exact root element name, false to tolerate a mismatch
         * @return the builder
         */
        public Builder strictRootElement(boolean strictRootElement) {
            this.strictRootElement = strictRootElement;
            return this;
        }

        /**
         * Sets the output buffer pool. The default is {@link OutputBuffers#shared()}.
         *
         * <p>Use {@link OutputBuffers#create()} for a pool owned by this codec.
         *
         * @param outputBuffers pool for serialization output
         * @return the builder
         */
        public Builder outputBuffers(OutputBuffers outputBuffers) {
            this.outputBuffers = Objects.requireNonNull(outputBuffers, "outputBuffers");
            return this;
        }

        /**
         * Override the native provider selection for testing. When set to true, the Smithy
         * implementation is used regardless of system property. When false, the StAX
         * implementation is used.
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
