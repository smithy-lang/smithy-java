/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.xml;

import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.TraitKey;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.XmlAttributeTrait;
import software.amazon.smithy.model.traits.XmlFlattenedTrait;
import software.amazon.smithy.model.traits.XmlNameTrait;
import software.amazon.smithy.model.traits.XmlNamespaceTrait;

/**
 * Stores and cached precomputed XML information from Schemas.
 */
final class XmlInfo {

    private final ConcurrentMap<Schema, StructInfo> structInfo = new ConcurrentHashMap<>();
    private final ConcurrentMap<Schema, ListMemberInfo> listInfo = new ConcurrentHashMap<>();
    private final ConcurrentMap<Schema, MapMemberInfo> mapInfo = new ConcurrentHashMap<>();

    private static final TraitKey<XmlAttributeTrait> XML_ATTRIBUTE = TraitKey.get(XmlAttributeTrait.class);
    private static final TraitKey<XmlFlattenedTrait> XML_FLATTENED = TraitKey.get(XmlFlattenedTrait.class);
    private static final TraitKey<XmlNameTrait> XML_NAME = TraitKey.get(XmlNameTrait.class);
    private static final TraitKey<XmlNamespaceTrait> XML_NAMESPACE = TraitKey.get(XmlNamespaceTrait.class);

    StructInfo getStructInfo(Schema schema) {
        return structInfo.computeIfAbsent(schema, StructInfo::new);
    }

    ListMemberInfo getListInfo(Schema schema) {
        return listInfo.computeIfAbsent(schema, ListMemberInfo::new);
    }

    MapMemberInfo getMapInfo(Schema schema) {
        return mapInfo.computeIfAbsent(schema, MapMemberInfo::new);
    }

    private static String getName(Schema schema) {
        var xmlName = schema.getTrait(XML_NAME);
        if (xmlName != null) {
            return xmlName.getValue();
        } else if (schema.isMember()) {
            return schema.memberName();
        } else {
            return schema.id().getName();
        }
    }

    static final class StructInfo {
        final Schema schema;
        final String xmlName;
        final XmlNamespaceTrait xmlNamespace;
        final Map<String, Schema> attributes;
        final Map<String, Schema> elements;
        final boolean hasFlattened;

        private StructInfo(Schema schema) {
            if (schema.type() != ShapeType.STRUCTURE && schema.type() != ShapeType.UNION) {
                throw new IllegalArgumentException("Expected a structure or union schema but found " + schema);
            }

            this.schema = schema;
            this.xmlNamespace = schema.getTrait(XML_NAMESPACE);
            this.xmlName = getName(schema);

            Map<String, Schema> attributes = null;
            Map<String, Schema> elements = null;
            boolean hasFlattened = false;

            for (var member : schema.members()) {
                var name = getName(member);
                if (member.hasTrait(XML_ATTRIBUTE)) {
                    if (attributes == null) {
                        attributes = new HashMap<>();
                    }
                    attributes.put(name, member);
                } else {
                    if (elements == null) {
                        elements = new HashMap<>();
                    }
                    elements.put(name, member);
                    if (member.hasTrait(XML_FLATTENED)) {
                        hasFlattened = true;
                    }
                }
            }

            this.hasFlattened = hasFlattened;
            this.attributes = attributes == null ? Collections.emptyMap() : attributes;
            this.elements = elements == null ? Collections.emptyMap() : elements;
        }

        // If the shape has flattened members, then prepare a map to store buffered state.
        Map<Schema, Deque<XMLEvent>> createFlattenedState() {
            return hasFlattened ? new HashMap<>() : Collections.emptyMap();
        }

        <T> void readMember(
            Map<Schema, Deque<XMLEvent>> flatState,
            ShapeDeserializer deserializer,
            XmlReader reader,
            T state,
            ShapeDeserializer.StructMemberConsumer<T> consumer,
            String elementName,
            Schema member
        ) throws XMLStreamException {
            if (!hasFlattened || !member.hasTrait(XML_FLATTENED)) {
                consumer.accept(state, member, deserializer);
            } else {
                // Events for flattened members need to be buffered to ensure that all flattened members are
                // deserialized regardless of if they are interspersed with other nodes.
                var current = flatState.get(member);
                var buffer = reader.bufferElement(elementName);
                if (current == null) {
                    flatState.put(member, buffer);
                } else {
                    current.addAll(buffer);
                }
            }
        }

        <T> void finishReadingStruct(
            Map<Schema, Deque<XMLEvent>> flatState,
            XmlInfo decoders,
            T state,
            ShapeDeserializer.StructMemberConsumer<T> consumer
        ) throws XMLStreamException {
            for (var entry : flatState.entrySet()) {
                var schema = entry.getKey();
                var events = entry.getValue();
                consumer.accept(state, schema, new XmlDeserializer(decoders, new XmlReader.BufferedReader(events)));
            }
        }
    }

    static final class ListMemberInfo {
        final String xmlName;
        final String memberName;
        final boolean flattened;

        ListMemberInfo(Schema schema) {
            if (schema.type() != ShapeType.LIST) {
                throw new IllegalArgumentException("Expected a list schema but found " + schema);
            }

            this.xmlName = getName(schema);
            this.flattened = schema.hasTrait(XML_FLATTENED);
            if (flattened) {
                this.memberName = getName(schema);
            } else {
                var member = schema.listMember();
                var memberXmlName = member.getTrait(XML_NAME);
                if (memberXmlName != null) {
                    memberName = memberXmlName.getValue();
                } else {
                    memberName = "member";
                }
            }
        }
    }

    static final class MapMemberInfo {
        final String xmlName;
        final String entryName;
        final String keyName;
        final String valueName;
        final boolean flattened;

        MapMemberInfo(Schema schema) {
            if (schema.type() != ShapeType.MAP) {
                throw new IllegalArgumentException("Expected a map schema but found " + schema);
            }
            this.xmlName = getName(schema);
            this.flattened = schema.hasTrait(XML_FLATTENED);
            this.entryName = flattened ? xmlName : "entry";
            this.keyName = getName(schema.mapKeyMember());
            this.valueName = getName(schema.mapValueMember());
        }
    }
}
