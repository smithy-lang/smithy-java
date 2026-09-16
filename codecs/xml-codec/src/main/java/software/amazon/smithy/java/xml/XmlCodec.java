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
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenFeature;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.MemberSubsetCodec;
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
public final class XmlCodec implements Codec, MemberSubsetCodec {

    private static final boolean USE_SMITHY_NATIVE =
            "smithy".equals(System.getProperty("smithy-java.xml-provider"));

    private volatile XMLInputFactory xmlInputFactory;
    private volatile XMLOutputFactory xmlOutputFactory;
    private volatile XMLEventFactory eventFactory;
    private final XmlInfo xmlInfo = new XmlInfo();
    private final List<String> wrapperElements;
    private final XmlNamespaceTrait defaultNamespace;
    private final boolean useNative;
    private final boolean strictRootElement;
    private final XmlSettings settings;

    /** The process-wide generated serde, or null when runtime code generation is off for this codec. */
    private final SmithyGeneratedXmlSerde generated;

    private XmlCodec(Builder builder) {
        this.wrapperElements = builder.wrapperElements;
        this.defaultNamespace = builder.defaultNamespace;
        this.strictRootElement = builder.strictRootElement;
        this.useNative = builder.useNative != null ? builder.useNative : USE_SMITHY_NATIVE;
        this.settings = XmlSettings.of(wrapperElements, defaultNamespace, strictRootElement);
        this.generated = runtimeCodegen(builder.runtimeCodegen, useNative)
                ? SmithyGeneratedXmlSerde.INSTANCE
                : null;
        if (!useNative) {
            initStax();
        }
    }

    /**
     * Generated codecs reproduce the native serializer's bytes, so they can only stand in for the
     * native serializer.
     *
     * <p>When codegen was requested through the system property and the StAX provider is selected, this
     * quietly returns false rather than failing: the property is process-wide, while the provider is
     * per codec, so a suite that exercises both providers would otherwise be unable to run with codegen
     * on at all. An explicit {@code runtimeCodegen(true)} is a narrower statement of intent and does
     * fail, because silently ignoring it would be indistinguishable from the feature not working.
     */
    private static boolean runtimeCodegen(Boolean requested, boolean useNative) {
        if (requested == null) {
            return RuntimeCodegenFeature.enabled("xml") && useNative;
        }
        if (!requested) {
            return false;
        }
        if (!useNative) {
            throw new IllegalStateException(
                    "XML runtime code generation replaces only the native Smithy serializer; select it "
                            + "with -Dsmithy-java.xml-provider=smithy or with useNative(true)");
        }
        return RuntimeCodegenFeature.available();
    }

    @Override
    public ByteBuffer serialize(SerializableShape shape) {
        if (generated != null && shape instanceof SerializableStruct struct) {
            ByteBuffer result = generated.serialize(struct, struct.schema(), settings);
            if (result != null) {
                return result;
            }
        }
        return Codec.super.serialize(shape);
    }

    /**
     * Writes only the members {@code subset} includes, using a codec generated for that subset.
     *
     * <p>Returns null unless runtime code generation is enabled and succeeded for this shape, so an HTTP
     * binding keeps its proxy for the shapes generation cannot reach. Getting the real structure rather
     * than a proxy is what makes generation possible at all here: a generated writer reaches into the
     * shape's own class, which a proxy is not.
     */
    @Override
    public ByteBuffer serialize(SerializableStruct struct, MemberSubset subset) {
        return generated == null ? null : generated.serialize(struct, struct.schema(), settings, subset);
    }

    @Override
    public boolean deserialize(
            Schema schema,
            ShapeBuilder<?> builder,
            ByteBuffer source,
            MemberSubset subset
    ) {
        return deserializeGenerated(schema, builder, source, subset);
    }

    @Override
    public <T extends SerializableShape> T deserializeShape(byte[] source, ShapeBuilder<T> builder) {
        if (deserializeGenerated(builder.schema(), builder, ByteBuffer.wrap(source), null)) {
            return builder.errorCorrection().build();
        }
        return Codec.super.deserializeShape(source, builder);
    }

    @Override
    public <T extends SerializableShape> T deserializeShape(ByteBuffer source, ShapeBuilder<T> builder) {
        if (deserializeGenerated(builder.schema(), builder, source, null)) {
            return builder.errorCorrection().build();
        }
        return Codec.super.deserializeShape(source, builder);
    }

    private boolean deserializeGenerated(
            Schema schema,
            ShapeBuilder<?> builder,
            ByteBuffer source,
            MemberSubset subset
    ) {
        if (generated == null || source == null || !source.hasRemaining()) {
            return false;
        }
        return smithyDeserializer(source).readStructGenerated(
                schema,
                builder,
                generated,
                settings,
                subset);
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
            return new LazyXmlSerializer(defaultNamespace, xmlInfo, sink, generated, settings);
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
            return smithyDeserializer(source);
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

    private SmithyXmlDeserializer smithyDeserializer(ByteBuffer source) {
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
        return new SmithyXmlDeserializer(
                bytes,
                offset,
                length,
                xmlInfo,
                true,
                wrapperElements,
                strictRootElement);
    }

    /**
     * Builder used to create an XML codec.
     */
    public static final class Builder {
        private List<String> wrapperElements = List.of();
        private XmlNamespaceTrait defaultNamespace;
        private Boolean useNative;
        private Boolean runtimeCodegen;
        private boolean strictRootElement = true;

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
         * Override the native provider selection for testing. When set to true, the native
         * (high-performance) implementation is used regardless of system property. When false,
         * the StAX implementation is used.
         */
        Builder useNative(boolean useNative) {
            this.useNative = useNative;
            return this;
        }

        /**
         * Override the {@code smithy-java.runtime-codegen} property for this codec.
         *
         * <p>Requires the native serializer, and requires a runtime that supports class generation;
         * on an older runtime this stays off.
         */
        Builder runtimeCodegen(boolean runtimeCodegen) {
            this.runtimeCodegen = runtimeCodegen;
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
