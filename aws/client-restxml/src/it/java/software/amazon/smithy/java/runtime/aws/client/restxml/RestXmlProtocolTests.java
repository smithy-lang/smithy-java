/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.aws.client.restxml;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import software.amazon.smithy.java.protocoltests.harness.HttpClientRequestTests;
import software.amazon.smithy.java.protocoltests.harness.HttpClientResponseTests;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTest;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTestFilter;
import software.amazon.smithy.java.protocoltests.harness.StringBuildingSubscriber;
import software.amazon.smithy.java.protocoltests.harness.TestType;
import software.amazon.smithy.java.runtime.io.ByteBufferUtils;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;

@ProtocolTest(
    service = "aws.protocoltests.restxml#RestXml",
    testType = TestType.CLIENT
)
@ProtocolTestFilter(
    skipOperations = {

    }
)
public class RestXmlProtocolTests {
    @HttpClientRequestTests
    @ProtocolTestFilter(
        skipTests = {
            "SDKAppliedContentEncoding_restXml",
            "SDKAppendedGzipAfterProvidedEncoding_restXml"
        }
    )
    public void requestTest(DataStream expected, DataStream actual) {
        if (expected.contentLength() != 0) {
            var a = new String(ByteBufferUtils.getBytes(actual.waitForByteBuffer()), StandardCharsets.UTF_8);
            var b = new String(ByteBufferUtils.getBytes(expected.waitForByteBuffer()), StandardCharsets.UTF_8);
            if ("application/xml".equals(expected.contentType())) {
                if (!XMLComparator.compareXMLStrings(a, b)) {
                    // Do this comparison to see what is different.
                    assertThat(a, equalTo(b));
                }
            } else {
                assertEquals(a, b);
            }
        } else {
            assertEquals("", new StringBuildingSubscriber(actual).getResult());
        }
    }

    @HttpClientResponseTests
    @ProtocolTestFilter(
        skipTests = {
            "XmlEnums",
            "XmlIntEnums",
            "XmlLists",
            "XmlMaps",
            "XmlMapsXmlName",
            "FlatNestedXmlMapResponse",
            "FlattenedXmlMap",
            "FlattenedXmlMapWithXmlName",
            "RestXmlFlattenedXmlMapWithXmlNamespace",
            "RestXmlXmlMapWithXmlNamespace",
            "RestXmlDateTimeWithFractionalSeconds",
            "HttpPrefixHeadersArePresent", //failing due to case mismatch in keys
            "HttpPayloadTraitsWithBlob",
            "HttpPayloadTraitsWithMediaTypeWithBlob",
            "RestXmlEnumPayloadResponse",
            "RestXmlStringPayloadResponse",
            "RestXmlHttpPayloadWithUnion",
            "BodyWithXmlName", 
            "NestedXmlMapWithXmlNameDeserializes"
        }
    )
    public void responseTest(Runnable test) {
        test.run();
    }

    static final class XMLComparator {

        public static boolean compareXMLStrings(String xml1, String xml2) {
            try {
                Document doc1 = parseXML(xml1);
                Document doc2 = parseXML(xml2);
                removeWhitespaceNodes(doc1);
                removeWhitespaceNodes(doc2);
                doc1.normalizeDocument();
                doc2.normalizeDocument();
                return compareNodes(doc1.getDocumentElement(), doc2.getDocumentElement());
            } catch (Exception e) {
                throw new RuntimeException("Error loading XML: " + xml1 + "\n\n vs " + xml2, e);
            }
        }

        private static Document parseXML(String xml) throws Exception {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setIgnoringComments(true);
            // Merge CDATA and Text nodes
            dbf.setCoalescing(true);
            dbf.setNamespaceAware(true);
            dbf.setIgnoringElementContentWhitespace(true);

            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(new org.xml.sax.InputSource(new StringReader(xml)));
        }

        private static void removeWhitespaceNodes(Node node) {
            NodeList childNodes = node.getChildNodes();
            for (int i = childNodes.getLength() - 1; i >= 0; i--) {
                Node child = childNodes.item(i);
                if (child.getNodeType() == Node.TEXT_NODE) {
                    if (child.getNodeValue().trim().isEmpty()) {
                        node.removeChild(child);
                    }
                } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                    removeWhitespaceNodes(child);
                }
            }
        }

        private static boolean compareNodes(Node node1, Node node2) {
            if (node1 == null && node2 == null) {
                return true;
            }

            if (node1 == null || node2 == null) {
                return false;
            }

            if (node1.getNodeType() != node2.getNodeType()) {
                return false;
            }

            if (!node1.getNodeName().equals(node2.getNodeName())) {
                return false;
            }

            if (node1.getNodeType() == Node.TEXT_NODE || node1.getNodeType() == Node.CDATA_SECTION_NODE) {
                String value1 = node1.getNodeValue().trim();
                String value2 = node2.getNodeValue().trim();
                if (!value1.equals(value2)) {
                    return false;
                }
            }

            // Compare attributes
            NamedNodeMap attrs1 = node1.getAttributes();
            NamedNodeMap attrs2 = node2.getAttributes();
            if (!compareAttributes(attrs1, attrs2)) {
                return false;
            }

            // Compare child nodes
            NodeList children1 = node1.getChildNodes();
            NodeList children2 = node2.getChildNodes();
            if (children1.getLength() != children2.getLength()) {
                return false;
            }

            for (int i = 0; i < children1.getLength(); i++) {
                Node child1 = children1.item(i);
                Node child2 = children2.item(i);
                if (!compareNodes(child1, child2)) {
                    return false;
                }
            }

            return true;
        }

        private static boolean compareAttributes(NamedNodeMap attrs1, NamedNodeMap attrs2) {
            if (attrs1 == null && attrs2 == null) {
                return true;
            }

            if ((attrs1 == null && attrs2 != null) || (attrs1 != null && attrs2 == null)) {
                return false;
            }

            if (attrs1.getLength() != attrs2.getLength()) {
                return false;
            }

            Map<String, String> map1 = new HashMap<>();
            Map<String, String> map2 = new HashMap<>();

            for (int i = 0; i < attrs1.getLength(); i++) {
                Attr attr = (Attr) attrs1.item(i);
                map1.put(attr.getName(), attr.getValue());
            }

            for (int i = 0; i < attrs2.getLength(); i++) {
                Attr attr = (Attr) attrs2.item(i);
                map2.put(attr.getName(), attr.getValue());
            }

            return map1.equals(map2);
        }
    }
}
