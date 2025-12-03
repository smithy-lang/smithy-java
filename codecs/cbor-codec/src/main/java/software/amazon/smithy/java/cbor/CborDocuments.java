/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.document.DocumentDeserializer;
import software.amazon.smithy.java.core.serde.document.DocumentUtils;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class CborDocuments {

    private static final Schema STRING_MAP_KEY;

    static {
        var tempSchema = Schema.structureBuilder(PreludeSchemas.DOCUMENT.id())
                .putMember("key", PreludeSchemas.STRING)
                .build();
        STRING_MAP_KEY = tempSchema.mapKeyMember();
    }

    private CborDocuments() {}

    public static Document of(Map<String, Document> values, CborSettings settings) {
        return new MapDocument(values, settings);
    }

    private static final class MapDocument implements Document {
        private final Map<String, Document> values;
        private final CborSettings settings;

        private MapDocument(Map<String, Document> values, CborSettings settings) {
            this.values = values;
            this.settings = settings;
        }

        @Override
        public ShapeType type() {
            return ShapeType.MAP;
        }

        @Override
        public ShapeId discriminator() {
            var discriminator = extractType();
            return DocumentDeserializer.parseDiscriminator(discriminator, settings.defaultNamespace());
        }

        @Override
        public ShapeId parseErrorType() {
            var discriminator = DocumentUtils.removeUri(extractType());
            return DocumentDeserializer.parseDiscriminator(discriminator, settings.defaultNamespace());
        }

        @Override
        public Map<String, Document> asStringMap() {
            return values;
        }

        @Override
        public Document getMember(String memberName) {
            return values.get(memberName);
        }

        @Override
        public Set<String> getMemberNames() {
            return values.keySet();
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public ShapeDeserializer createDeserializer() {
            return new CborDocumentSerializer(settings, this);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeMap(PreludeSchemas.DOCUMENT, values, values.size(), (stringMap, mapSerializer) -> {
                for (var e : stringMap.entrySet()) {
                    mapSerializer.writeEntry(STRING_MAP_KEY, e.getKey(), e.getValue(), Document::serializeContents);
                }
            });
        }

        @Override
        public boolean equals(Object obj) {
            return Document.equals(this, obj);
        }

        @Override
        public int hashCode() {
            return values.hashCode();
        }

        private String extractType() {
            var member = values.get("__type");
            if (member != null && member.type() == ShapeType.STRING) {
                return member.asString();
            }
            return null;
        }
    }

    /**
     * Customized version of DocumentDeserializer to account for the settings of the CBOR codec.
     */
    private static final class CborDocumentSerializer extends DocumentDeserializer {

        private final CborSettings settings;

        CborDocumentSerializer(CborSettings settings, Document value) {
            super(value);
            this.settings = settings;
        }

        @Override
        protected DocumentDeserializer deserializer(Document nextValue) {
            return new CborDocumentSerializer(settings, nextValue);
        }
    }
}
