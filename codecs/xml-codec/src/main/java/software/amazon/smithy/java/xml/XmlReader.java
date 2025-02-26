/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

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
import software.amazon.smithy.java.core.serde.SerializationException;

sealed abstract class XmlReader implements AutoCloseable {

    private final SmartTextReader textReader = new SmartTextReader();
    private boolean pendingNext;

    abstract Location getLocation();

    abstract String getAttributeValue(String namespaceURI, String localName);

    abstract Deque<XMLEvent> bufferElement(String startElementName) throws XMLStreamException;

    protected abstract int getEventType();

    protected abstract boolean hasNext() throws XMLStreamException;

    protected abstract String getReaderText() throws XMLStreamException;

    protected abstract String getStartElementName() throws XMLStreamException;

    protected abstract void nextElement() throws XMLStreamException;

    protected final void next() throws XMLStreamException {
        nextElement();
        pendingNext = false;
        textReader.reset();
    }

    protected final void nextIfNeeded() throws XMLStreamException {
        if (pendingNext) {
            next();
        }
    }

    // Close the current element by skipping over all contained elements.
    final void closeElement() throws XMLStreamException {
        nextIfNeeded();
        int depth = 1;
        while (hasNext() && depth > 0) {
            if (getEventType() == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (getEventType() == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
            next();
        }
    }

    final String getText() throws XMLStreamException {
        nextIfNeeded();
        if (textReader.isEmpty()) {
            while (XmlReader.readNextString(getEventType())) {
                textReader.add(getReaderText());
                nextElement();
            }
        }
        return textReader.toString();
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

    final String nextMemberElement() throws XMLStreamException {
        nextIfNeeded();
        while (true) {
            switch (getEventType()) {
                case XMLStreamConstants.START_ELEMENT:
                    // Don't go to the next node just yet since attributes may need to be deserialized.
                    pendingNext = true;
                    return getStartElementName();
                case XMLStreamConstants.END_ELEMENT:
                case -1: // EOF
                    return null;
                default:
                    next();
            }
        }
    }

    @Override
    public final String toString() {
        var location = getLocation();
        var line = location.getLineNumber();
        var column = location.getColumnNumber();
        return "(event: " + getEventType() + ", line: " + line + ", column: " + column + ")";
    }

    static final class StreamReader extends XmlReader {

        private final XMLStreamReader reader;
        private final XMLInputFactory factory;

        StreamReader(XMLStreamReader reader, XMLInputFactory factory) throws XMLStreamException {
            this.reader = reader;
            this.factory = factory;
            // Skip past the start of the document.
            while (canSkipEvent(reader.getEventType()) || reader.isWhiteSpace()) {
                next();
            }
        }

        @Override
        protected void nextElement() throws XMLStreamException {
            reader.next();
        }

        @Override
        protected int getEventType() {
            return reader.getEventType();
        }

        @Override
        protected boolean hasNext() throws XMLStreamException {
            return reader.hasNext();
        }

        @Override
        public Location getLocation() {
            return reader.getLocation();
        }

        @Override
        public String getAttributeValue(String namespaceURI, String localName) {
            return reader.getAttributeValue(namespaceURI, localName);
        }

        @Override
        protected String getReaderText() {
            return reader.getText();
        }

        @Override
        protected String getStartElementName() {
            return reader.getLocalName();
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
    }

    static final class BufferedReader extends XmlReader {

        private final Deque<XMLEvent> eventBuffer;
        private XMLEvent currentEvent;

        BufferedReader(Deque<XMLEvent> eventBuffer) throws XMLStreamException {
            this.eventBuffer = eventBuffer;
            do {
                next();
            } while (currentEvent != null && canSkipEvent(currentEvent.getEventType()));
        }

        @Override
        public void close() {}

        @Override
        protected void nextElement() {
            currentEvent = eventBuffer.poll();
        }

        @Override
        protected int getEventType() {
            return currentEvent != null ? currentEvent.getEventType() : -1;
        }

        @Override
        protected boolean hasNext() {
            return currentEvent != null;
        }

        @Override
        public Location getLocation() {
            return currentEvent != null ? currentEvent.getLocation() : null;
        }

        @Override
        protected String getReaderText() {
            return currentEvent != null ? currentEvent.asCharacters().getData() : null;
        }

        @Override
        protected String getStartElementName() {
            return currentEvent.asStartElement().getName().getLocalPart();
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
        public Deque<XMLEvent> bufferElement(String startElementName) throws XMLStreamException {
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
    }

    // Uses a single StringBuilder to store potentially multiple CHARACTER events.
    private static final class SmartTextReader {

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

    private static boolean canSkipEvent(int event) {
        return switch (event) {
            case XMLStreamConstants.START_DOCUMENT,
                    XMLStreamConstants.COMMENT,
                    XMLStreamConstants.SPACE,
                    XMLStreamConstants.DTD,
                    XMLStreamConstants.PROCESSING_INSTRUCTION,
                    XMLStreamConstants.NOTATION_DECLARATION,
                    XMLStreamConstants.ENTITY_DECLARATION ->
                true;
            default -> false;
        };
    }
}
