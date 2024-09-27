/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.xml;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.StringReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import org.junit.jupiter.api.Test;

public class XmlReaderTest {

    private static XMLInputFactory getFactory() {
        var xmlInputFactory = XMLInputFactory.newInstance();
        xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        xmlInputFactory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        xmlInputFactory.setProperty(XMLInputFactory.IS_COALESCING, false);
        return xmlInputFactory;
    }

    @Test
    public void usesStreamReader() throws Exception {
        var xml = """
            <foo>
                <bar>bar</bar>
                <baz hi="A" bye="B">1</baz>
            </foo>
            """;

        var factory = getFactory();
        try (var reader = new XmlReader.StreamReader(factory.createXMLStreamReader(new StringReader(xml)), factory)) {
            assertThat(reader.getLocation().getLineNumber(), equalTo(1));
            assertThat(reader.getText(), equalTo(""));
            assertThat(reader.nextMemberElement(), equalTo("foo"));

            assertThat(reader.nextMemberElement(), equalTo("bar"));
            assertThat(reader.getAttributeValue(null, "bye"), nullValue());
            assertThat(reader.getText(), equalTo("bar"));
            assertThat(reader.getText(), equalTo("bar")); // call it again, it's cached now.
            reader.closeElement();
            assertThat(reader.getText(), not(equalTo("bar"))); // ensure the text is not cached when node changes

            assertThat(reader.nextMemberElement(), equalTo("baz"));
            assertThat(reader.getAttributeValue(null, "hi"), equalTo("A"));
            assertThat(reader.getAttributeValue(null, "bye"), equalTo("B"));
            assertThat(reader.getText(), equalTo("1"));
            reader.closeElement();

            assertThat(reader.nextMemberElement(), nullValue());

            assertThat(reader.getLocation().getLineNumber(), equalTo(4));
            assertThat(reader.getLocation().getColumnNumber(), equalTo(7));
            reader.closeElement();
        }
    }

    @Test
    public void selfClosedTag() throws Exception {
        var xml = "<foo/>";

        var factory = getFactory();
        try (var reader = new XmlReader.StreamReader(factory.createXMLStreamReader(new StringReader(xml)), factory)) {
            assertThat(reader.getText(), equalTo(""));
            assertThat(reader.nextMemberElement(), equalTo("foo"));
            assertThat(reader.getText(), equalTo(""));
            assertThat(reader.nextMemberElement(), nullValue());
            reader.closeElement();
        }
    }

    @Test
    public void getLocationNameFromBufferedReader() throws XMLStreamException {
        var xml = "<foo>hi</foo>";
        var factory = getFactory();
        var streamReader = new XmlReader.StreamReader(factory.createXMLStreamReader(new StringReader(xml)), factory);
        var bufferedReader = new XmlReader.BufferedReader(streamReader.bufferElement("foo"));

        assertThat(bufferedReader.getLocation().getLineNumber(), equalTo(1));
        assertThat(bufferedReader.getLocation().getColumnNumber(), equalTo(6));
    }

    @Test
    public void convertsStreamReaderToString() throws XMLStreamException {
        var xml = "<foo>hi</foo>";
        var factory = getFactory();
        var streamReader = new XmlReader.StreamReader(factory.createXMLStreamReader(new StringReader(xml)), factory);

        assertThat(streamReader.toString(), equalTo("(event: 1, line: 1, column: 6)"));
    }

    @Test
    public void convertsBufferedReaderToString() throws XMLStreamException {
        var xml = "<foo>hi</foo>";
        var factory = getFactory();
        var streamReader = new XmlReader.StreamReader(factory.createXMLStreamReader(new StringReader(xml)), factory);
        var bufferedReader = new XmlReader.BufferedReader(streamReader.bufferElement("foo"));

        assertThat(bufferedReader.toString(), equalTo("(event: 1, line: 1, column: 6)"));
    }

    @Test
    public void buffersEvents() throws Exception {
        var xml = """
            <foo>
                <bar>bar</bar>
                <baz hi="A" bye="B">1</baz>
            </foo>
            """;

        var factory = getFactory();
        try (
            var r = new XmlReader.StreamReader(
                factory.createXMLStreamReader(new StringReader(xml)),
                factory
            ); var reader = new XmlReader.BufferedReader(r.bufferElement("foo"))
        ) {
            assertThat(reader.getLocation().getLineNumber(), equalTo(1));
            assertThat(reader.getText(), equalTo(""));
            assertThat(reader.nextMemberElement(), equalTo("foo"));

            assertThat(reader.nextMemberElement(), equalTo("bar"));
            assertThat(reader.getAttributeValue(null, "bye"), nullValue());
            assertThat(reader.getText(), equalTo("bar"));
            assertThat(reader.getText(), equalTo("bar")); // call it again, it's cached now.
            reader.closeElement();
            assertThat(reader.getText(), not(equalTo("bar"))); // ensure the text is not cached when node changes

            assertThat(reader.nextMemberElement(), equalTo("baz"));
            assertThat(reader.getAttributeValue(null, "hi"), equalTo("A"));
            assertThat(reader.getAttributeValue(null, "bye"), equalTo("B"));
            assertThat(reader.getText(), equalTo("1"));
            reader.closeElement();

            assertThat(reader.nextMemberElement(), nullValue());

            assertThat(reader.getLocation().getLineNumber(), equalTo(4));
            assertThat(reader.getLocation().getColumnNumber(), equalTo(7));
            reader.closeElement();
        }
    }

    @Test
    public void buffersNestedElementsOfSameName() throws XMLStreamException {
        var xml = "<foo><foo><foo>hi</foo></foo></foo>";
        var factory = getFactory();
        var streamReader = new XmlReader.StreamReader(factory.createXMLStreamReader(new StringReader(xml)), factory);
        var bufferedReader = new XmlReader.BufferedReader(streamReader.bufferElement("foo"));

        assertThat(bufferedReader.nextMemberElement(), equalTo("foo"));
        assertThat(bufferedReader.nextMemberElement(), equalTo("foo"));
        assertThat(bufferedReader.nextMemberElement(), equalTo("foo"));
        assertThat(bufferedReader.getText(), equalTo("hi"));
        bufferedReader.closeElement();
        bufferedReader.closeElement();
        bufferedReader.closeElement();
    }

    @Test
    public void canBufferNestedBuffers() throws XMLStreamException {
        var xml = "<foo><foo><foo>hi</foo></foo></foo>";
        var factory = getFactory();
        var streamReader = new XmlReader.StreamReader(factory.createXMLStreamReader(new StringReader(xml)), factory);
        var bufferedReader1 = new XmlReader.BufferedReader(streamReader.bufferElement("foo"));
        var bufferedReader2 = new XmlReader.BufferedReader(bufferedReader1.bufferElement("foo"));

        assertThat(bufferedReader2.nextMemberElement(), equalTo("foo"));
        assertThat(bufferedReader2.nextMemberElement(), equalTo("foo"));
        assertThat(bufferedReader2.nextMemberElement(), equalTo("foo"));
        assertThat(bufferedReader2.getText(), equalTo("hi"));
        bufferedReader2.closeElement();
        bufferedReader2.closeElement();
        bufferedReader2.closeElement();
    }

    @Test
    public void canBufferPartOfBuffer() throws XMLStreamException {
        var xml = "<foo><bar>hi</bar><baz>bye</baz></foo>";
        var factory = getFactory();
        var streamReader = new XmlReader.StreamReader(factory.createXMLStreamReader(new StringReader(xml)), factory);
        var bufferedReader1 = new XmlReader.BufferedReader(streamReader.bufferElement("foo"));

        assertThat(bufferedReader1.nextMemberElement(), equalTo("foo"));
        assertThat(bufferedReader1.nextMemberElement(), equalTo("bar"));

        var bufferedReader2 = new XmlReader.BufferedReader(bufferedReader1.bufferElement("bar")); // capture bar
        assertThat(bufferedReader2.getText(), equalTo("hi"));
        assertThat(bufferedReader2.nextMemberElement(), nullValue());
        bufferedReader2.closeElement();

        assertThat(bufferedReader1.nextMemberElement(), equalTo("baz"));
    }
}
