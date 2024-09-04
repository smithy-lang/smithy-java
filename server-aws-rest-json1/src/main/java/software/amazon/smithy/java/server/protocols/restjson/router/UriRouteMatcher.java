/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocols.restjson.router;

import software.amazon.smithy.java.runtime.io.uri.QueryStringParser;

/**
 * Matches a full URI by matching both the path and query components. This uses the BasicPathMatcher which does NOT
 * allow empty path segments in the URI.
 */
class UriRouteMatcher implements RouteMatcher {

    private final RouteMatcher pathRouteMatcher;
    private final RouteMatcher queryRouteMatcher;

    public UriRouteMatcher(PathPattern pathPattern, QueryPattern queryPattern) {
        this(new BasicPathRouteMatcher(pathPattern), getQueryMatcher(queryPattern));
    }

    UriRouteMatcher(RouteMatcher pathRouteMatcher, RouteMatcher queryRouteMatcher) {
        if (pathRouteMatcher == null) {
            throw new IllegalArgumentException();
        }

        this.pathRouteMatcher = pathRouteMatcher;
        this.queryRouteMatcher = queryRouteMatcher;
    }

    @Override
    public int getRank() {
        return pathRouteMatcher.getRank() + (queryRouteMatcher == null ? 0 : queryRouteMatcher.getRank());
    }

    @Override
    public Match match(String uri) {
        String path = QueryStringParser.getPath(uri);
        String query = QueryStringParser.getQuery(uri);
        return match(path, query);
    }

    protected Match match(String path, String query) {
        Match pathMatch = pathRouteMatcher.match(path);
        if (pathMatch == null) {
            return null;
        }

        if (queryRouteMatcher == null) {
            return pathMatch;
        }

        Match queryMatch = queryRouteMatcher.match(query);
        if (queryMatch == null) {
            return null;
        }

        return new CompositeMatch(pathMatch, queryMatch);
    }

    protected static RouteMatcher getQueryMatcher(QueryPattern queryPattern) {
        if (queryPattern == null) {
            return null;
        } else {
            return new QueryStringRouteMatcher(queryPattern);
        }
    }
}
