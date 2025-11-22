/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import software.amazon.smithy.java.core.serde.ShapeDeserializer;

/**
 * Utility class for XML codec.
 */
public final class XmlUtil {
    /**
     * Parse the Code element value from error response for both AWS restXml and AWS Query protocols.
     *
     * @param deserializer the deserializer for the error response
     * @return String value of the Code element if found
     */
    public static String parseErrorCodeName(ShapeDeserializer deserializer) {
        try (var xmlDeserializer = (XmlDeserializer) deserializer) {
            return xmlDeserializer.parseErrorCodeName();
        }
    }
}
