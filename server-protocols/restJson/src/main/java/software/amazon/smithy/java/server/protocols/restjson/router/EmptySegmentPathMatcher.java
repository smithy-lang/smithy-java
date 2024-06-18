/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocols.restjson.router;

import software.amazon.smithy.java.server.protocols.restjson.router.PathPattern.Segment;

/**
 * A URI Path Matcher that allows empty segments.
 */
class EmptySegmentPathMatcher extends BasicPathMatcher {

    public EmptySegmentPathMatcher(CharSequence pattern) {
        super(pattern);
    }

    EmptySegmentPathMatcher(PathPattern pattern) {
        super(pattern);
    }

    @Override
    protected String createRegex(PathPattern pattern) {
        StringBuilder sb = new StringBuilder();

        sb.append("\\A");

        boolean isFirst = true;
        for (Segment s : pattern.getSegments()) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append("/");
            }

            if (s.isLabel()) {
                if (s.isGreedyLabel()) {
                    sb.append("(.*)");
                } else {
                    sb.append("([^/]*)");
                }
            } else {
                sb.append(getRegexForLiteral(s.getContent()));
            }
        }

        sb.append("[/]*\\z"); // allow trailing slashes

        return sb.toString();
    }
}
