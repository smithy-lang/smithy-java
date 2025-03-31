/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.server.restjson.router;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import software.amazon.smithy.model.pattern.InvalidPatternException;

public final class PathPattern {

    private static final String LABEL_PATTERN_REGEX = "\\w+";
    private static final Pattern LABEL_PATTERN = Pattern.compile(LABEL_PATTERN_REGEX);

    private final CharSequence pattern;
    private final List<Segment> segments;
    private final Map<String, Segment> labelSegments;

    public PathPattern(CharSequence pattern) {
        this(pattern, true);
    }

    public PathPattern(CharSequence pattern, boolean checkForLabelsAfterGreedyLabels) {
        if (pattern == null) {
            throw new IllegalArgumentException();
        }

        this.pattern = pattern;
        this.segments = getSegments(pattern);

        checkForDuplicateLabels();
        if (checkForLabelsAfterGreedyLabels) {
            checkForLabelsAfterGreedyLabels();
        }

        this.labelSegments = Collections.unmodifiableMap(collectLabelSegments());
    }

    public Iterable<String> getLabels() {
        return labelSegments.keySet();
    }

    public List<Segment> getSegments() {
        return segments;
    }

    public Segment getSegmentForLabel(String label) {
        return labelSegments.get(label);
    }

    @Override
    public String toString() {
        return segments.toString();
    }

    public UriPattern.ConflictType conflictType(PathPattern that) {
        int minSize = Math.min(segments.size(), that.segments.size());
        for (int i = 0; i < minSize; i++) {
            Segment thisSegment = segments.get(i);
            Segment otherSegment = that.segments.get(i);

            if (!thisSegment.isLabel()) {
                // both are literals, check literal content
                // if not equal, there's no conflict
                if (thisSegment.getContent().toString().equalsIgnoreCase(otherSegment.getContent().toString())) {
                    return UriPattern.ConflictType.NONE;
                }
            }
        }

        // at this point, the two patterns are identical. make sure one is longer than the other
        if (segments.size() != that.segments.size()) {
            return UriPattern.ConflictType.NONE;
        }

        return UriPattern.ConflictType.EQUIVALENT_CONFLICT;
    }

    public boolean conflictsWith(PathPattern pathPattern) {
        return conflictType(pathPattern) != UriPattern.ConflictType.NONE;
    }

    private void checkForDuplicateLabels() {
        Set<CharSequence> labels = new HashSet<>();
        for (Segment s : segments) {
            if (!s.isLabel()) {
                continue;
            }

            if (!labels.add(s.getContent())) {
                throw new InvalidPatternException(
                        "Label '" + s.getContent()
                                + "' is defined more than once in pattern: '" + pattern + "'");
            }
        }
    }

    private void checkForLabelsAfterGreedyLabels() {
        // make sure at most one greedy label exists, and that it is the last label segment
        for (int i = 0; i < segments.size(); i++) {
            Segment s = segments.get(i);
            if (s.isGreedyLabel()) {
                for (int j = i + 1; j < segments.size(); j++) {
                    if (segments.get(j).isGreedyLabel()) {
                        throw new InvalidPatternException(
                                "At most one greedy label segment may exist in pattern: '"
                                        + pattern + "'");
                    }
                    if (segments.get(j).isLabel()) {
                        throw new InvalidPatternException(
                                "A greedy label segment must be the last label segment in its pattern: '" + pattern
                                        + "'");
                    }
                }
            }
        }
    }

    private Map<String, Segment> collectLabelSegments() {
        Map<String, Segment> labelSegments = new HashMap<String, Segment>();
        for (Segment s : segments) {
            if (s.isLabel()) {
                labelSegments.put(String.valueOf(s.getContent()), s);
            }
        }
        return labelSegments;
    }

    private static List<Segment> getSegments(CharSequence pattern) {
        List<Segment> segments = new ArrayList<Segment>();
        StringBuilder sb = new StringBuilder();
        int offset = 0;

        // tokenize the pattern by '/'
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);

            if (c == '/') {
                if (!sb.isEmpty()) {
                    // form a segment from any existing contents of the stringbuilder
                    segments.add(Segment.parse(sb, pattern, offset));

                    // replace the stringbuilder
                    sb = new StringBuilder();
                }
                offset = i + 1;
            } else {
                sb.append(c);
            }
        }

        if (!sb.isEmpty()) {
            segments.add(Segment.parse(sb, pattern, offset));
        }

        return segments;
    }

    public static class Segment {
        private final CharSequence content;
        private final boolean isLabel;
        private final boolean isGreedyLabel;

        public Segment(CharSequence content, boolean isLabel, boolean isGreedyLabel) {
            if (content == null) {
                throw new IllegalArgumentException();
            }
            if (content.isEmpty()) {
                throw new IllegalArgumentException();
            }
            if (!isLabel && isGreedyLabel) {
                throw new IllegalArgumentException();
            }

            this.content = content;
            this.isLabel = isLabel;
            this.isGreedyLabel = isGreedyLabel;
        }

        public CharSequence getContent() {
            return content;
        }

        public boolean isLabel() {
            return isLabel;
        }

        /**
         * Whether the label match should extend across slashes in the URI path
         */
        public boolean isGreedyLabel() {
            return isGreedyLabel;
        }

        @Override
        public String toString() {
            return "'" + content + "' isLabel: " + isLabel + " isGreedyLabel: " + isGreedyLabel;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Segment)) {
                return false;
            }

            Segment s = (Segment) o;
            return (isLabel() == s.isLabel()) &&
                    (isGreedyLabel() == s.isGreedyLabel())
                    &&
                    (getContent().toString()).equalsIgnoreCase(s.getContent().toString());

        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    isLabel(),
                    isGreedyLabel(),
                    getContent());
        }

        static Segment parse(CharSequence segment, CharSequence pattern, int offset) {
            if (segment.length() >= 2 && segment.charAt(0) == '{' && segment.charAt(segment.length() - 1) == '}') {
                boolean greedy = segment.charAt(segment.length() - 2) == '+';
                if (greedy) {
                    segment = segment.subSequence(1, segment.length() - 2);
                } else {
                    segment = segment.subSequence(1, segment.length() - 1);
                }

                // isLabel
                if (segment.isEmpty()) {
                    throw new InvalidPatternException(
                            "Empty label declaration in path pattern: '" + pattern
                                    + "' at index: " + (offset + 1));
                }

                // make sure no {} present in content, and make sure greedy symbol '+' doesn't exist in the label name
                for (int i = 0; i < segment.length(); i++) {
                    int idx = offset + i + 1;

                    switch (segment.charAt(i)) {
                        case '{':
                            throw new InvalidPatternException(
                                    "Attempted to create nested label in path pattern: '"
                                            + pattern + "' at index: " + idx);
                        case '}':
                            throw new InvalidPatternException(
                                    "Unmatched label termination in path pattern: '" + pattern
                                            + "' at index: " + idx);
                        case '+':
                            throw new InvalidPatternException(
                                    "Labels may contain '+' only as the last character, and only to denote use as a greedy pattern.  Error in pattern: '"
                                            + pattern + "' at index: " + idx);
                    }
                }

                validateLabelName(segment, pattern, offset + 1);

                return new Segment(segment, true, greedy);
            } else {
                // make sure no {} present in content
                for (int i = 0; i < segment.length(); i++) {
                    int idx = offset + i;

                    switch (segment.charAt(i)) {
                        case '{':
                        case '}':
                            throw new InvalidPatternException(
                                    "Labels must be delimited by slash ('/') characters.  Error in pattern: '" + pattern
                                            + "' at index: " + idx);
                    }
                }

                return new Segment(segment, false, false);
            }
        }

        private static void validateLabelName(CharSequence name, CharSequence pattern, int offset) {
            if (!LABEL_PATTERN.matcher(name).matches()) {
                throw new InvalidPatternException(
                        "Invalid label name in path pattern at index: " + offset
                                + " pattern: '" + pattern + "'. Must satisfy: " + LABEL_PATTERN_REGEX);
            }
        }
    }
}
