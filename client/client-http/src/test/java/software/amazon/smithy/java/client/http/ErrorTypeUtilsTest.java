/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;

public class ErrorTypeUtilsTest {
    private static final String SIMPLE_TYPE = "FooError";
    private static final String TYPE_WITH_URI = "FooError:http://amazon.com/smithy/com.amazon.smithy.validate/";
    private static final String TYPE_WITH_NAMESPACE = "aws.protocoltests.restjson#FooError";
    private static final String TYPE_WITH_NAMESPACE_AND_URI =
            "aws.protocoltests.restjson#FooError:http://amazon.com/smithy/com.amazon.smithy.validate/";

    @Test
    public void testRemoveUri() {
        String expected = "FooError";
        String expectedWithNamespace = "aws.protocoltests.restjson#FooError";

        assertEquals(expected, ErrorTypeUtils.removeUri(SIMPLE_TYPE));
        assertEquals(expected, ErrorTypeUtils.removeUri(TYPE_WITH_URI));
        assertEquals(expectedWithNamespace, ErrorTypeUtils.removeUri(TYPE_WITH_NAMESPACE));
        assertEquals(expectedWithNamespace, ErrorTypeUtils.removeUri(TYPE_WITH_NAMESPACE_AND_URI));
    }

    @Test
    public void testRemoveNameSpaceAndUri() {
        String expected = "FooError";

        assertEquals(expected, ErrorTypeUtils.removeNamespaceAndUri(SIMPLE_TYPE));
        assertEquals(expected, ErrorTypeUtils.removeNamespaceAndUri(TYPE_WITH_URI));
        assertEquals(expected, ErrorTypeUtils.removeNamespaceAndUri(TYPE_WITH_NAMESPACE));
        assertEquals(expected, ErrorTypeUtils.removeNamespaceAndUri(TYPE_WITH_NAMESPACE_AND_URI));
    }

    @Test
    public void testReadType() {
        var document = Document.of(Map.of("__type", Document.of("foo")));
        assertEquals("foo", ErrorTypeUtils.readType(document));
    }

    @Test
    public void testReadTypeAndCode() {
        var document1 = Document.of(Map.of("__type", Document.of("foo"), "code", Document.of("bar")));
        var document2 = Document.of(Map.of("code", Document.of("bar")));

        assertEquals("foo", ErrorTypeUtils.readTypeAndCode(document1));
        assertEquals("bar", ErrorTypeUtils.readTypeAndCode(document2));
    }
}
