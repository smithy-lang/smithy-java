/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.xml;

import java.util.ArrayDeque;
import java.util.Deque;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;

sealed interface XmlReader extends AutoCloseable {

    Location getLocation();

    String getText() throws XMLStreamException;

    String getAttributeValue(String namespaceURI, String localName);

    String nextMemberElement() throws XMLStreamException;

    // Close the current element by skipping over all contained elements.
    void closeElement() throws XMLStreamException;

    Deque<XMLEvent> bufferElement(String startElementName) throws XMLStreamException;

    final class StreamReader implements XmlReader {

        private final XMLStreamReader reader;
        private final XMLInputFactory factory;
        private final SmartTextReader textReader = new SmartTextReader();
        private boolean pendingNext;

        StreamReader(XMLStreamReader reader, XMLInputFactory factory) throws XMLStreamException {
            this.reader = reader;
            this.factory = factory;
            // Skip past the start of the document.
            while (canSkipEvent(reader.getEventType()) || reader.isWhiteSpace()) {
                next();
            }
        }

        private void next() throws XMLStreamException {
            reader.next();
            pendingNext = false;
            textReader.reset();
        }

        private void nextIfNeeded() throws XMLStreamException {
            if (pendingNext) {
                next();
            }
        }

        @Override
        public Location getLocation() {
            return reader.getLocation();
        }

        @Override
        public String getText() throws XMLStreamException {
            nextIfNeeded();
            if (textReader.isEmpty()) {
                while (XmlReader.readNextString(reader.getEventType())) {
                    textReader.add(reader.getText());
                    reader.next();
                }
            }
            return textReader.toString();
        }

        @Override
        public String getAttributeValue(String namespaceURI, String localName) {
            return reader.getAttributeValue(namespaceURI, localName);
        }

        @Override
        public String nextMemberElement() throws XMLStreamException {
            nextIfNeeded();

            while (true) {
                if (reader.isStartElement()) {
                    // Don't go to the next node just yet since attributes may need to be deserialized.
                    pendingNext = true;
                    return reader.getLocalName();
                } else if (reader.isEndElement() || !reader.hasNext()) {
                    return null;
                } else {
                    next();
                }
            }
        }

        public void closeElement() throws XMLStreamException {
            nextIfNeeded();
            int depth = 1;
            while (reader.hasNext() && depth > 0) {
                if (reader.isStartElement()) {
                    depth++;
                } else if (reader.isEndElement()) {
                    depth--;
                }
                next();
            }
        }

        @Override
        public Deque<XMLEvent> bufferElement(String startElementName) throws XMLStreamException {
            nextIfNeeded();

            XMLEventReader eventReader = factory.createXMLEventReader(reader);
            Deque<XMLEvent> eventBuffer = new ArrayDeque<>();

            // Track the depth of the starting element and push the starting event.
            int depth = 0;

            // Process the events and buffer them
            do {
                XMLEvent e = eventReader.nextEvent();
                eventBuffer.add(e);
                // If we encounter another start element with the same name, increment the depth
                if (e.isStartElement() && e.asStartElement().getName().getLocalPart().equals(startElementName)) {
                    depth++;
                }
                if (e.isEndElement() && e.asEndElement().getName().getLocalPart().equals(startElementName)) {
                    // Break when matching END_ELEMENT at the original depth.
                    if (--depth <= 0) {
                        break;
                    }
                }
            } while (true);

            return eventBuffer;
        }

        @Override
        public void close() throws Exception {
            reader.close();
        }

        @Override
        public String toString() {
            return XmlReader.toString(reader.getEventType(), getLocation());
        }
    }

    final class BufferedReader implements XmlReader {

        private final Deque<XMLEvent> eventBuffer;
        private final SmartTextReader textReader = new SmartTextReader();
        private XMLEvent currentEvent;
        private boolean pendingNext;

        BufferedReader(Deque<XMLEvent> eventBuffer) {
            this.eventBuffer = eventBuffer;
            do {
                next();
            } while (currentEvent != null && canSkipEvent(currentEvent.getEventType()));
        }

        @Override
        public void close() {}

        private void next() {
            textReader.reset();
            pendingNext = false;
            currentEvent = eventBuffer.poll();
        }

        private void nextIfNeeded() {
            if (pendingNext) {
                next();
            }
        }

        @Override
        public Location getLocation() {
            return currentEvent != null ? currentEvent.getLocation() : null;
        }

        @Override
        public String getText() {
            nextIfNeeded();
            if (textReader.isEmpty()) {
                while (currentEvent != null && XmlReader.readNextString(currentEvent.getEventType())) {
                    textReader.add(currentEvent.asCharacters().getData());
                    currentEvent = eventBuffer.poll(); // don't clear the reader.
                }
            }
            return textReader.toString();
        }

        @Override
        public String getAttributeValue(String namespaceURI, String localName) {
            if (currentEvent != null && currentEvent.isStartElement()) {
                StartElement startElement = currentEvent.asStartElement();
                QName qName = new QName(namespaceURI, localName);
                Attribute attribute = startElement.getAttributeByName(qName);
                if (attribute != null) {
                    return attribute.getValue();
                }
            }
            return null;
        }

        @Override
        public String nextMemberElement() {
            nextIfNeeded();

            while (currentEvent != null) {
                if (currentEvent.isStartElement()) {
                    pendingNext = true;
                    return currentEvent.asStartElement().getName().getLocalPart();
                } else if (currentEvent.isEndElement()) {
                    return null;
                } else {
                    next();
                }
            }
            return null;
        }

        @Override
        public void closeElement() {
            nextIfNeeded();

            int depth = 1;
            while (currentEvent != null && depth > 0) {
                if (currentEvent.isStartElement()) {
                    depth++;
                } else if (currentEvent.isEndElement()) {
                    depth--;
                }
                next();
            }
        }

        @Override
        public Deque<XMLEvent> bufferElement(String startElementName) {
            nextIfNeeded();
            Deque<XMLEvent> bufferedEvents = new ArrayDeque<>();

            // Track the depth of nested elements with the same name.
            int depth = 1;

            bufferedEvents.add(currentEvent);

            do {
                next();
                bufferedEvents.add(currentEvent);
                if (currentEvent.isStartElement()) {
                    if (startElementName.equals(currentEvent.asStartElement().getName().getLocalPart())) {
                        depth++;
                    }
                } else if (currentEvent.isEndElement()) {
                    if (startElementName.equals(currentEvent.asEndElement().getName().getLocalPart())) {
                        if (--depth <= 0) {
                            break;
                        }
                    }
                }
            } while (true);

            // Move past the buffered element
            next();

            return bufferedEvents;
        }

        @Override
        public String toString() {
            if (currentEvent == null) {
                return "(end of XML)";
            } else {
                return XmlReader.toString(currentEvent.getEventType(), getLocation());
            }
        }
    }

    // Uses a single StringBuilder to store potentially multiple CHARACTER events.
    final class SmartTextReader {

        private final StringBuilder builder = new StringBuilder();
        private String single = "";
        private int reads;

        void reset() {
            single = "";
            reads = 0;
            if (!builder.isEmpty()) {
                builder.setLength(0);
            }
        }

        void add(String text) {
            if (reads == 0) {
                single = text;
            } else if (reads == 1) {
                builder.append(single).append(text);
            } else {
                builder.append(text);
            }
            reads++;
        }

        boolean isEmpty() {
            return single.isEmpty() && builder.isEmpty();
        }

        @Override
        public String toString() {
            if (!builder.isEmpty()) {
                single = builder.toString();
                builder.setLength(0);
                reads = 1;
            }
            return single;
        }
    }

    private static String toString(int event, Location location) {
        var line = location.getLineNumber();
        var column = location.getColumnNumber();
        return "(event: " + event + ", line: " + line + ", column: " + column + ")";
    }

    private static boolean canSkipEvent(int event) {
        return switch (event) {
            case XMLStreamConstants.START_DOCUMENT,
                XMLStreamConstants.COMMENT,
                XMLStreamConstants.SPACE,
                XMLStreamConstants.DTD,
                XMLStreamConstants.PROCESSING_INSTRUCTION,
                XMLStreamConstants.NOTATION_DECLARATION,
                XMLStreamConstants.ENTITY_DECLARATION -> true;
            default -> false;
        };
    }

    private static boolean readNextString(int event) {
        return switch (event) {
            case XMLStreamReader.CHARACTERS, XMLStreamConstants.CDATA -> true;
            case XMLStreamConstants.ENTITY_REFERENCE -> {
                throw new SerializationException("Unexpected entity reference in XML");
            }
            default -> false;
        };
    }
}
