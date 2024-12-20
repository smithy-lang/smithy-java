/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.XmlNamespaceTrait;

/**
 * Stores and cached precomputed XML information from Schemas.
 */
final class XmlInfo {

    private final ConcurrentMap<Schema, StructInfo> structInfo = new ConcurrentHashMap<>();
    private final ConcurrentMap<Schema, ListMemberInfo> listInfo = new ConcurrentHashMap<>();
    private final ConcurrentMap<Schema, MapMemberInfo> mapInfo = new ConcurrentHashMap<>();

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
        var xmlName = schema.getDirectTrait(TraitKey.XML_NAME_TRAIT);
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
            this.xmlNamespace = schema.getTrait(TraitKey.XML_NAMESPACE_TRAIT);
            this.xmlName = getName(schema);

            Map<String, Schema> attributes = null;
            Map<String, Schema> elements = null;
            boolean hasFlattened = false;

            for (var member : schema.members()) {
                var name = getName(member);
                if (member.hasTrait(TraitKey.XML_ATTRIBUTE_TRAIT)) {
                    if (attributes == null) {
                        attributes = new HashMap<>();
                    }
                    attributes.put(name, member);
                } else {
                    if (elements == null) {
                        elements = new HashMap<>();
                    }
                    elements.put(name, member);
                    if (member.hasTrait(TraitKey.XML_FLATTENED_TRAIT)) {
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
            XMLEventFactory eventFactory,
            ShapeDeserializer deserializer,
            XmlReader reader,
            T state,
            ShapeDeserializer.StructMemberConsumer<T> consumer,
            String elementName,
            Schema member
        ) throws XMLStreamException {
            if (!hasFlattened || !member.hasTrait(TraitKey.XML_FLATTENED_TRAIT)) {
                consumer.accept(state, member, deserializer);
            } else {
                // Events for flattened members need to be buffered to ensure that all flattened members are
                // deserialized regardless of if they are interspersed with other nodes.
                var current = flatState.get(member);
                var buffer = reader.bufferElement(elementName);

                // The just read start event needs to be added back to the event buffer so it can be replayed while
                // parsing the deferred flattened list values.
                QName qname = new QName(elementName);
                StartElement startElement = eventFactory.createStartElement(qname, null, null);

                if (current == null) {
                    buffer.addFirst(startElement);
                    flatState.put(member, buffer);
                } else {
                    current.add(startElement);
                    current.addAll(buffer);
                }
            }
        }

        <T> void finishReadingStruct(
            Map<Schema, Deque<XMLEvent>> flatState,
            XMLEventFactory eventFactory,
            XmlInfo decoders,
            T state,
            ShapeDeserializer.StructMemberConsumer<T> consumer
        ) throws XMLStreamException {
            for (var entry : flatState.entrySet()) {
                var schema = entry.getKey();
                var events = entry.getValue();
                consumer.accept(
                    state,
                    schema,
                    // Use a special flattened deserializer that delegates validation of the encountered element.
                    XmlDeserializer.flattened(decoders, eventFactory, new XmlReader.BufferedReader(events))
                );
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
            this.flattened = schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
            if (flattened) {
                this.memberName = getName(schema);
            } else {
                var member = schema.listMember();
                var memberXmlName = member.getTrait(TraitKey.XML_NAME_TRAIT);
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
            this.flattened = schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
            this.entryName = flattened ? xmlName : "entry";
            this.keyName = getName(schema.mapKeyMember());
            this.valueName = getName(schema.mapValueMember());
        }
    }
}
