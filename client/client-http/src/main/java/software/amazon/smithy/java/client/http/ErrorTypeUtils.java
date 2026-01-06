/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeType;

public final class ErrorTypeUtils {

    private ErrorTypeUtils() {}

    /**
     * Read the error type from __type field of the document.
     *
     * @param document the document of the payload to read.
     * @return the extracted error type from the document.
     */
    public static String readType(Document document) {
        String errorType = null;
        var member = document.getMember("__type");
        if (member != null && member.type() == ShapeType.STRING) {
            errorType = member.asString();
        }
        return errorType;
    }

    /**
     * Read the error type from __type or code field of the document.
     *
     * @param document the document of the payload to read.
     * @return the extracted error type from the document.
     */
    public static String readTypeAndCode(Document document) {
        String errorType = readType(document);
        if (errorType == null) {
            var member = document.getMember("code");
            if (member != null && member.type() == ShapeType.STRING) {
                errorType = member.asString();
            }
        }
        return errorType;
    }

    /**
     * Removes the trailing URI in {@code __type} field or {@code code} field of the document.
     *
     * <p>For example, given {@code __type = "aws.protocoltests.restjson#FooError:http://abc.com"},
     * protocols like restJSON should ignore the trailing URI and keep the namespace of the error type.
     *
     * @param text The error type string.
     * @return The error type string without the trailing URI.
     */
    public static String removeUri(String text) {
        if (text == null) {
            return null;
        }
        var colon = text.indexOf(':');
        if (colon > 0) {
            text = text.substring(0, colon);
        }
        return text;
    }

    /**
     * Removes the namespace and trailing URI in {@code __type} field or {@code code} field of the document.
     *
     * <p>For example, given {@code __type = "aws.protocoltests.restjson#FooError:http://abc.com"},
     * protocols like awsJSON 1.1 should ignore the namespace and the trailing URI of the error type.
     *
     * @param text The error type string.
     * @return The error type string without the trailing URI.
     */
    public static String removeNamespaceAndUri(String text) {
        if (text == null) {
            return null;
        }
        var hash = text.indexOf('#');
        if (hash > 0) {
            text = text.substring(hash + 1);
        }
        return removeUri(text);
    }
}
