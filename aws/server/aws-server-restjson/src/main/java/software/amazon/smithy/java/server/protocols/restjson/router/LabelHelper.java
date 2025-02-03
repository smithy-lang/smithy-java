/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocols.restjson.router;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import software.amazon.smithy.model.pattern.InvalidPatternException;

abstract class LabelHelper {

    private static final Pattern REGEX_SLASH_BEFORE_END = Pattern.compile("[/]+\\z");
    private static final Pattern REGEX_SLASH_BEFORE_QUERY = Pattern.compile("[/]+\\?");
    private static final Pattern REGEX_STRING_OF_SLASHES = Pattern.compile("[/]+");

    public static String getLabel(CharSequence value) {
        // If contains { and }, these must be exclusively the first and last characters this also denotes that it's a
        // label.
        int braces = 0;

        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '{') {
                if (i == 0) {
                    braces++;
                } else {
                    throw new InvalidPatternException(
                            "Left brace may only be used as leading character of a label reference");
                }
            }

            if (value.charAt(i) == '}') {
                if (i == value.length() - 1) {
                    braces++;
                } else {
                    throw new InvalidPatternException(
                            "Right brace may only be used as last character of a label reference");
                }
            }
        }

        switch (braces) {
            case 0:
                return null;
            case 1:
                throw new InvalidPatternException("Unmatched pair of braces in query string parameter value");
            case 2:
                if (value.length() < 3) {
                    throw new InvalidPatternException("Label reference must contain label name; was \"" + value + "\"");
                }
                return value.subSequence(1, value.length() - 1).toString();
            default:
                throw new IllegalStateException(); // should be unreachable
        }
    }

    public static boolean isLabel(char c) {
        return c == '{';
    }

    public static int readName(CharSequence pattern, int i, StringBuilder sb) {
        i++;

        for (;;) {
            char c = pattern.charAt(i);
            if (c == '}') {
                break;
            }

            sb.append(c);
            i++;
        }

        i++;
        return i;
    }

    public static int readValue(CharSequence uri, int i, StringBuilder sb) {
        for (;;) {
            if (i >= uri.length()) {
                return i;
            }

            char c = uri.charAt(i);

            switch (c) {
                // check for control characters which end our label match
                case '/':
                case '?':
                    return i;
                default:
                    sb.append(c);
                    i++;
                    break;
            }
        }
    }

    public static CharSequence normalizePattern(CharSequence pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException();
        }

        String labelPattern = String.valueOf(pattern);

        // remove trailing slash before end
        labelPattern = REGEX_SLASH_BEFORE_END.matcher(labelPattern).replaceAll("");

        // remove trailing slash before query
        labelPattern = REGEX_SLASH_BEFORE_QUERY.matcher(labelPattern).replaceAll("?");

        // collapse strings of slashes
        labelPattern = REGEX_STRING_OF_SLASHES.matcher(labelPattern).replaceAll("/");

        return labelPattern;
    }

    public static void validatePattern(CharSequence pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException();
        }

        // pattern may not start with slash
        if (pattern.length() > 0 && pattern.charAt(0) == '/') {
            throw new InvalidPatternException("Pattern may not begin with slash");
        }

        getLabels(pattern);
    }

    public static List<String> getLabels(CharSequence pattern) {
        StringBuilder sb = null;
        List<String> labels = new ArrayList<String>();

        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            switch (c) {
                case '{':
                    if (sb != null) {
                        throw new InvalidPatternException(
                                "Illegal nested label at index: " + i
                                        + ".  Input was: '" + pattern + "'");
                    }
                    sb = new StringBuilder();
                    break;
                case '}':
                    if (sb == null) {
                        throw new InvalidPatternException(
                                "Unmatched closing brace at index: " + i
                                        + ".  Input was: '" + pattern + "'");
                    }
                    labels.add(sb.toString());
                    sb = null;
                    break;
                default:
                    if (sb != null) {
                        sb.append(c);
                    }
                    break;
            }
        }

        if (sb != null) {
            throw new InvalidPatternException("Unmatched open brace.  Input was: '" + pattern + "'");
        }

        return labels;
    }
}
