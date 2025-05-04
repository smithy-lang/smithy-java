/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.endpointrules;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import software.amazon.smithy.java.context.Context;

/**
 * Implements stdlib functions of the rules engine that weren't promoted to opcodes.
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
            String input = (String) operands[0];
            int start = (int) operands[1];
            int end = (int) operands[2];
            boolean reverse = (boolean) operands[3];

            if (input == null || input.length() < (end - start)) {
                return null;
            }

            var result = input.substring(start, end);

            // TODO: Did we really add a reverse functionality to this rule?
            if (reverse) {
                result = new StringBuilder(result).reverse().toString();
            }

            return result;
        }
    },

    // https://smithy.io/2.0/additional-specs/rules-engine/standard-library.html#isvalidhostlabel-function
    IS_VALID_HOST_LABEL("isValidHostLabel", 1) {
        @Override
        public Object apply1(Object arg) {
            // TODO: Implement this.
            //var input = (String) operands[0];
            throw new UnsupportedOperationException("isValidHostLabel is not yet implemented");
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
            String input = (String) arg;
            StringBuilder encoded = new StringBuilder();
            for (int i = 0; i < input.length();) {
                int codePoint = input.codePointAt(i);
                i += Character.charCount(codePoint);
                if (isUnreserved(codePoint)) {
                    encoded.appendCodePoint(codePoint);
                } else {
                    byte[] bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
                    for (byte b : bytes) {
                        encoded.append('%');
                        encoded.append(String.format("%02X", b));
                    }
                }
            }
            return encoded.toString();
        }

        private static boolean isUnreserved(int codePoint) {
            return (codePoint >= 'A' && codePoint <= 'Z') ||
                    (codePoint >= 'a' && codePoint <= 'z')
                    ||
                    (codePoint >= '0' && codePoint <= '9')
                    ||
                    codePoint == '-'
                    || codePoint == '_'
                    || codePoint == '.'
                    || codePoint == '~';
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
