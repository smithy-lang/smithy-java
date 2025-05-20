/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import software.amazon.smithy.java.client.core.ClientContext;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.io.uri.URLEncoding;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.IsValidHostLabel;

/**
 * Implements stdlib functions of the rules engine that weren't promoted to opcodes (GetAttr, isset, not, substring).
 */
enum Stdlib implements RulesFunction {
    // https://smithy.io/2.0/additional-specs/rules-engine/standard-library.html#stringequals-function
    STRING_EQUALS("stringEquals", 2) {
        @Override
        public Object apply2(Object a, Object b) {
            return Objects.equals(EndpointUtils.castFnArgument(a, String.class, "stringEquals", 1),
                    EndpointUtils.castFnArgument(b, String.class, "stringEquals", 2));
        }
    },

    // https://smithy.io/2.0/additional-specs/rules-engine/standard-library.html#booleanequals-function
    BOOLEAN_EQUALS("booleanEquals", 2) {
        @Override
        public Object apply2(Object a, Object b) {
            return Objects.equals(EndpointUtils.castFnArgument(a, Boolean.class, "booleanEquals", 1),
                    EndpointUtils.castFnArgument(b, Boolean.class, "booleanEquals", 2));
        }
    },

    // https://smithy.io/2.0/additional-specs/rules-engine/standard-library.html#isvalidhostlabel-function
    IS_VALID_HOST_LABEL("isValidHostLabel", 2) {
        @Override
        public Object apply2(Object arg1, Object arg2) {
            var hostLabel = EndpointUtils.castFnArgument(arg1, String.class, "isValidHostLabel", 1);
            var allowDots = EndpointUtils.castFnArgument(arg2, Boolean.class, "isValidHostLabel", 2);
            return IsValidHostLabel.isValidHostLabel(hostLabel, Boolean.TRUE.equals(allowDots));
        }
    },

    // https://smithy.io/2.0/additional-specs/rules-engine/standard-library.html#parseurl-function
    PARSE_URL("parseURL", 1) {
        @Override
        public Object apply1(Object arg) {
            if (arg == null) {
                return null;
            }

            try {
                var result = new URI(EndpointUtils.castFnArgument(arg, String.class, "parseURL", 1));
                if (null != result.getRawQuery()) {
                    // "If the URL given contains a query portion, the URL MUST be rejected and the function MUST
                    // return an empty optional."
                    return null;
                }
                return result;
            } catch (URISyntaxException e) {
                throw new RulesEvaluationError("Error parsing URI in endpoint rule parseURL method", e);
            }
        }
    },

    // https://smithy.io/2.0/additional-specs/rules-engine/standard-library.html#uriencode-function
    URI_ENCODE("uriEncode", 1) {
        @Override
        public Object apply1(Object arg) {
            var str = EndpointUtils.castFnArgument(arg, String.class, "uriEncode", 1);
            return URLEncoding.encodeUnreserved(str, false);
        }
    };

    private final String name;
    private final int operands;

    Stdlib(String name, int operands) {
        this.name = name;
        this.operands = operands;
    }

    @Override
    public int getOperandCount() {
        return operands;
    }

    @Override
    public String getFunctionName() {
        return name;
    }

    static Object standardBuiltins(String name, Context context) {
        if (name.equals("SDK::Endpoint")) {
            var result = context.get(ClientContext.CUSTOM_ENDPOINT);
            if (result != null) {
                return result.uri().toString();
            }
        }
        return null;
    }
}
