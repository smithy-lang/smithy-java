/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.document;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class DocumentUtilsTest {
    private static final String SIMPLE_TYPE = "FooError";
    private static final String TYPE_WITH_URI = "FooError:http://amazon.com/smithy/com.amazon.smithy.validate/";
    private static final String TYPE_WITH_NAMESPACE = "aws.protocoltests.restjson#FooError";
    private static final String TYPE_WITH_NAMESPACE_AND_URI =
            "aws.protocoltests.restjson#FooError:http://amazon.com/smithy/com.amazon.smithy.validate/";

    @Test
    public void testRemoveUri() {
        String expected = "FooError";
        String expectedWithNamespace = "aws.protocoltests.restjson#FooError";
        assertEquals(expected, DocumentUtils.removeUri(SIMPLE_TYPE));
        assertEquals(expected, DocumentUtils.removeUri(TYPE_WITH_URI));
        assertEquals(expectedWithNamespace, DocumentUtils.removeUri(TYPE_WITH_NAMESPACE));
        assertEquals(expectedWithNamespace, DocumentUtils.removeUri(TYPE_WITH_NAMESPACE_AND_URI));
    }

    @Test
    public void testRemoveNameSpaceAndUri() {
        String expected = "FooError";
        assertEquals(expected, DocumentUtils.removeNamespaceAndUri(SIMPLE_TYPE));
        assertEquals(expected, DocumentUtils.removeNamespaceAndUri(TYPE_WITH_URI));
        assertEquals(expected, DocumentUtils.removeNamespaceAndUri(TYPE_WITH_NAMESPACE));
        assertEquals(expected, DocumentUtils.removeNamespaceAndUri(TYPE_WITH_NAMESPACE_AND_URI));
    }
}
