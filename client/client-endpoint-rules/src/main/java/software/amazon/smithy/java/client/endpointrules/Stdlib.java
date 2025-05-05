/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.endpointrules;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.regex.Pattern;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.io.uri.URLEncoding;

/**
 * Implements stdlib functions of the rules engine that weren't promoted to opcodes (GetAttr, isset, not).
 */
enum Stdlib implements VmFunction {
    // https://smithy.io/2.0/additional-specs/rules-engine/standard-library.html#stringequals-function
    STRING_EQUALS("stringEquals", 2) {
        @Override
        public Object apply2(Object a, Object b) {
            return Objects.equals((String) a, (String) b);
        }
    },

    // https://smithy.io/2.0/additional-specs/rules-engine/standard-library.html#booleanequals-function
    BOOLEAN_EQUALS("booleanEquals", 2) {
        @Override
        public Object apply2(Object a, Object b) {
            return Objects.equals((Boolean) a, (Boolean) b);
        }
    },

    // https://smithy.io/2.0/additional-specs/rules-engine/standard-library.html#substring-function
    SUBSTRING("substring", 4) {
        @Override
        public Object apply(Object... operands) {
            // software.amazon.smithy.rulesengine.language.syntax.expressions.functions.Substring.Definition.evaluate
            String str = (String) operands[0];
            int startIndex = (int) operands[1];
            int stopIndex = (int) operands[2];
            boolean reverse = (boolean) operands[3];

            for (int i = 0; i < str.length(); i++) {
                if (!(str.charAt(i) <= 127)) {
                    return null;
                }
            }

            if (startIndex >= stopIndex || str.length() < stopIndex) {
                return null;
            }

            if (!reverse) {
                return str.substring(startIndex, stopIndex);
            } else {
                int revStart = str.length() - stopIndex;
                int revStop = str.length() - startIndex;
                return str.substring(revStart, revStop);
            }
        }
    },

    // https://smithy.io/2.0/additional-specs/rules-engine/standard-library.html#isvalidhostlabel-function
    IS_VALID_HOST_LABEL("isValidHostLabel", 1) {
        private static final Pattern HOST_LABEL_PATTERN =
                Pattern.compile("^[a-zA-Z\\d][a-zA-Z\\d\\-]{0,62}$");

        @Override
        public Object apply2(Object arg1, Object arg2) {
            var hostLabel = (String) arg1;
            var allowDots = (Boolean) arg2;
            if (allowDots == null || !allowDots) {
                return HOST_LABEL_PATTERN.matcher(hostLabel).matches();
            } else {
                // ensure that empty matches at the end are included
                for (String subLabel : hostLabel.split("[.]", -1)) {
                    if (!HOST_LABEL_PATTERN.matcher(subLabel).matches()) {
                        return false;
                    }
                }
                return true;
            }
        }
    },

    // https://smithy.io/2.0/additional-specs/rules-engine/standard-library.html#parseurl-function
    PARSE_URL("parseURL", 1) {
        @Override
        public Object apply1(Object arg) {
            String input = (String) arg;
            try {
                return new URI(input);
            } catch (URISyntaxException e) {
                throw new RulesEvaluationError("Error parsing URI in endpoint rule parseURL method", e);
            }
        }
    },

    // https://smithy.io/2.0/additional-specs/rules-engine/standard-library.html#uriencode-function
    URI_ENCODE("uriEncode", 1) {
        @Override
        public Object apply1(Object arg) {
            return URLEncoding.encodeUnreserved((String) arg, false);
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
            // TODO: grab statically set endpoint via a config key.
        }
        return null;
    }
}
