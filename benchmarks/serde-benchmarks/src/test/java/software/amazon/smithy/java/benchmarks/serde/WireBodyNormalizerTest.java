/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WireBodyNormalizerTest {
    @Test
    void removesOnlyWhitespaceOutsideJsonStrings() {
        String json = " \n{ \"s\": \" a\\\" b\\\\ c\\n \", \"utf8\": \"中 é\", \"n\": [ 1, 2 ] }\r\t";
        byte[] normalized = WireBodyNormalizer.normalize(bytes(json), "Application/X-Amz-Json-1.0; charset=utf-8");
        assertEquals("{\"s\":\" a\\\" b\\\\ c\\n \",\"utf8\":\"中 é\",\"n\":[1,2]}",
                new String(normalized, StandardCharsets.UTF_8));
    }

    @Test
    void retainsBodiesWithoutJsonFormattingWhitespace() {
        byte[] compact = bytes("{\"s\":\" x \"}");
        assertArrayEquals(compact, WireBodyNormalizer.normalize(compact, "application/json"));
    }

    @Test
    void preservesJsonNumberLexemesAndStringEscapes() {
        String json = " [ -0, 1.2300, 1E+09, 123456789012345678901, \"\\u0061\\/b\" ] ";
        assertArrayEquals(bytes("[-0,1.2300,1E+09,123456789012345678901,\"\\u0061\\/b\"]"),
                WireBodyNormalizer.normalize(bytes(json), "application/json"));
    }

    @Test
    void preservesXmlWhitespaceIncludingMixedContentAndCdata() {
        for (String xml : new String[] {
                " <root> <child/> </root> ",
                "<root xml:space=\"preserve\"> <child/> </root>",
                "<root><![CDATA[> <]]></root>",
                "<root attribute=\"> <\"> </root>",
                "<root><b>hello</b> <b>world</b></root>"
        }) {
            byte[] body = bytes(xml);
            assertArrayEquals(body, WireBodyNormalizer.normalize(body, "application/xml"));
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
