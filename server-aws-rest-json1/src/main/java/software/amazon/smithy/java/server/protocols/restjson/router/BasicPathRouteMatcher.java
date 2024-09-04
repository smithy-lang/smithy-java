/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocols.restjson.router;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import software.amazon.smithy.java.server.protocols.restjson.router.PathPattern.Segment;

/**
 * Simple URI path matcher based upon regular expressions. Capable of handling 'long' label matches which span segments
 * in the URI path.
 */
class BasicPathRouteMatcher implements RouteMatcher {

    private final Pattern pattern;
    private final List<String> orderedLabels;
    private final int rank;

    public BasicPathRouteMatcher(CharSequence pattern) {
        this(new PathPattern(pattern));
    }

    BasicPathRouteMatcher(PathPattern pattern) {
        Objects.requireNonNull(pattern);

        this.pattern = Pattern.compile(createRegex(pattern));
        this.orderedLabels = gatherLabels(pattern);
        this.rank = pattern.getSegments().size();
    }

    @Override
    public int getRank() {
        return rank;
    }

    @Override
    public Match match(String path) {
        java.util.regex.Matcher m = pattern.matcher(path);

        if (!m.matches()) {
            return null;
        }

        // associate groups from matcher with label names
        LabelValues lvs = new LabelValues();

        for (int i = 0; i < orderedLabels.size(); i++) {
            lvs.addUriPathLabelValue(orderedLabels.get(i), m.group(i + 1));
        }

        return new LabelValuesMatch(lvs);
    }

    protected String createRegex(PathPattern pattern) {
        StringBuilder sb = new StringBuilder();

        sb.append("\\A");

        boolean isFirst = true;
        for (Segment s : pattern.getSegments()) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append("[/]+");
            }

            if (s.isLabel()) {
                if (s.isGreedyLabel()) {
                    sb.append("(.+)");
                } else {
                    sb.append("([^/]+)");
                }
            } else {
                sb.append(getRegexForLiteral(s.getContent()));
            }
        }

        sb.append("[/]*\\z"); // allow trailing slashes

        return sb.toString();
    }

    protected CharSequence getRegexForLiteral(CharSequence literal) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (i = 0; i < literal.length(); i++) {
            char c = literal.charAt(i);
            // slashes no longer allowed in literals -- filtered by PathPattern
            if ("[]\\^$()|".indexOf(c) != -1) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb;
    }

    protected List<String> gatherLabels(PathPattern pattern) {
        List<String> labels = new ArrayList<>();
        for (Segment s : pattern.getSegments()) {
            if (!s.isLabel()) {
                continue;
            }

            labels.add(String.valueOf(s.getContent()));
        }
        return labels;
    }
}
