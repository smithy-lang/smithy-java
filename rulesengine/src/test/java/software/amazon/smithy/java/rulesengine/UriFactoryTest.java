/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.io.uri.SmithyUri;

class UriFactoryTest {

    @Test
    void testBasicUriCreation() {
        UriFactory factory = new UriFactory();
        SmithyUri uri = factory.createUri("https://example.com");
        assertEquals("https://example.com", uri.toString());
    }

    @Test
    void testNullUri() {
        UriFactory factory = new UriFactory();
        assertNull(factory.createUri(null));
    }

    @Test
    void testInvalidUri() {
        UriFactory factory = new UriFactory();
        assertNull(factory.createUri("not a valid uri with spaces"));
    }

    @Test
    void testCaching() {
        UriFactory factory = new UriFactory(3);

        SmithyUri uri1 = factory.createUri("https://example1.com");
        SmithyUri uri2 = factory.createUri("https://example2.com");
        SmithyUri uri3 = factory.createUri("https://example3.com");

        // Access uri1 again to make it recently used
        SmithyUri uri1Again = factory.createUri("https://example1.com");
        assertSame(uri1, uri1Again);

        // Add a fourth URI, which should evict uri2 (least recently used)
        SmithyUri uri4 = factory.createUri("https://example4.com");

        // uri1 and uri3 should still be cached
        assertSame(uri1, factory.createUri("https://example1.com"));
        assertSame(uri3, factory.createUri("https://example3.com"));
        assertSame(uri4, factory.createUri("https://example4.com"));
    }
}
