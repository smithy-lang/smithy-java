/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.server.restjson.router;

import software.amazon.smithy.java.io.uri.QueryStringParser;

/**
 * Matches a full URI by matching both the path and query components. This uses the EmptySegmentPathMatcher which does
 * allow empty path segments in the URI.
 */
class EmptySegmentUriRouteMatcher extends UriRouteMatcher {

    public EmptySegmentUriRouteMatcher(PathPattern pathPattern, QueryPattern queryPattern) {
        super(new EmptySegmentPathRouteMatcher(pathPattern), getQueryMatcher(queryPattern));
    }

    @Override
    public Match match(String uri) {
        String path = QueryStringParser.getPath(uri, true);
        String query = QueryStringParser.getQuery(uri);
        return match(path, query);
    }
}
