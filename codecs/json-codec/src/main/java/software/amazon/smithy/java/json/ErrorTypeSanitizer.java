/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

/**
 * A util class contains the sanitizer functions to process {@code __type} field from the document
 *
 * <p>For example, given {@code __type = "aws.protocoltests.restjson#FooError:http://abc.com"},
 * different protocols have different requirements for the {@code __type} field. This class provides
 * sanitizers to remove trailing URIs or leading namespaces from {@code __type}.
 */
public class ErrorTypeSanitizer {
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
