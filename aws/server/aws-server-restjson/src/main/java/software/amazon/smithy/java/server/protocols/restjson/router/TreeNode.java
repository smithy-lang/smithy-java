/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocols.restjson.router;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.server.protocols.restjson.router.UriTreeMatcherMap.ParsedUri;
import software.amazon.smithy.java.server.protocols.restjson.router.UriTreeMatcherMap.TreeMatch;

/**
 * Represents a Radix(ish) Tree Node, implementing specificity routing
 * for a given set of URI patterns.
 *
 * <p>The way the tree is traversed, first constant segments, then
 * labels and finally greedy labels seamlessly routes to the most
 * specific pattern. For those cases when there are still ambiguity we
 * use the rank of the path pattern which is the total amount of
 * constant segments and the rank of the query pattern which is the
 * total amount of query literals.
 *
 * @param <T> The type that the URI patterns map to.
 */
final class TreeNode<T> {
    static final Match NO_QUERY_MATCH = new NullMatch();
    private final String content;
    private final List<QueryMatcher<T>> queryMatchers;
    private final Map<String, TreeNode<T>> children;
    private final List<TreeNode<T>> placeholders;
    private final List<TreeNode<T>> greedyPlaceholders;
    private final boolean isGreedyPlaceholder;
    private final boolean isPlaceholder;

    TreeNode(
            CharSequence content,
            List<QueryMatcher<T>> queryMatchers,
            Map<String, TreeNode<T>> children,
            List<TreeNode<T>> placeholders,
            List<TreeNode<T>> greedyPlaceholders,
            boolean isPlaceholder,
            boolean isGreedyPlaceholder
    ) {
        this.content = Objects.requireNonNull(content).toString();
        this.queryMatchers = Objects.requireNonNull(queryMatchers);
        this.children = Objects.requireNonNull(children);
        this.placeholders = Objects.requireNonNull(placeholders);
        this.greedyPlaceholders = Objects.requireNonNull(greedyPlaceholders);
        this.isPlaceholder = isPlaceholder;
        this.isGreedyPlaceholder = isGreedyPlaceholder;
    }

    /**
     * Routes the given parsed URI and returns the match result.
     *
     * @param uri The parsed uri to match against
     * @return The match result
     */
    public TreeMatch<T> route(ParsedUri uri) {
        // Check for a corner case when allowing empty path segments
        // and placeholders at the root of the URI.
        TreeMatch<T> result = routeWithEmptyPath(uri);
        if (result.matches()) {
            return result;
        }
        return route(uri, 0, LabelValuesNode.SENTINEL);
    }

    /**
     * Routes the given parsed URI starting at the given index and
     * returns the match result.
     *
     * @param uri      The parsed uri to match against
     * @param index    The current index of the segment to be routed
     * @param captures The list of currently captured label values
     * @return The match result
     */
    private TreeMatch<T> route(
            ParsedUri uri,
            int index,
            LabelValuesNode captures
    ) {
        if (!uri.hasSegmentAt(index)) {
            if (isLeaf()) {
                return matchQuery(uri, captures);
            }
            return UriTreeMatcherMap.noMatch();
        }
        String segment = uri.getSegment(index);
        TreeMatch<T> result;
        TreeNode<T> child = children.get(segment);
        if (child != null) {
            result = child.route(uri, index + 1, captures);
            if (result.matches()) {
                return result;
            }
        }
        // Route labels
        result = routeLabels(uri, index, captures, placeholders);
        if (result.matches()) {
            return result;
        }
        // Route greedy labels
        result = routeLabels(uri, index, captures, greedyPlaceholders);
        if (result.matches()) {
            return result;
        }
        if (isGreedyPlaceholder) {
            // Capture this segment and continue with the next.
            captures = captures.add(this.content, segment);
            result = route(uri, index + 1, captures);
        }
        return result;
    }

    /**
     * Routes to all the placeholder branches and returns the best
     * match.
     *
     * @param uri          The parsed uri to match against
     * @param index        The current index of the segment to be routed
     * @param captures     The list of currently captured label values
     * @param placeholders The list of placeholders to match against
     * @return The match result
     */
    private TreeMatch<T> routeLabels(
            ParsedUri uri,
            int index,
            LabelValuesNode captures,
            List<TreeNode<T>> placeholders
    ) {
        TreeMatch<T> result = UriTreeMatcherMap.noMatch();
        String segment = uri.getSegment(index);
        for (TreeNode<T> placeholder : placeholders) {
            LabelValuesNode newCaptures = captures.add(placeholder.content, segment);
            TreeMatch<T> newResult = placeholder.route(uri, index + 1, newCaptures);
            if (newResult.isBetterThan(result)) {
                result = newResult;
            }
        }
        return result;
    }

    /**
     * Matches the query string.
     *
     * @param uri      The parsed uri to match against
     * @param captures The list of currently captured label values
     * @return The match result
     */
    private TreeMatch<T> matchQuery(
            ParsedUri uri,
            LabelValuesNode captures
    ) {
        TreeMatch<T> result = UriTreeMatcherMap.noMatch();
        for (QueryMatcher<T> queryMatcher : queryMatchers) {
            TreeMatch<T> newResult = queryMatcher.match(uri, captures);
            if (newResult.isBetterThan(result)) {
                result = newResult;
            }
        }
        return result;
    }

    /**
     * Returns true if the current node is a leaf node.
     *
     * @return {@code true} if the current node is a leaf node.
     */
    private boolean isLeaf() {
        return !queryMatchers.isEmpty();
    }

    /**
     * Handle the corner case created by allowing empty paths
     * alongside with using labels without any prefix.  If the root
     * takes empty path segments <b>and</b> the URI path is empty
     * <b>and</b> the patterns accept labels at the root of the
     * hierarchy, then retrofit the URI to add a single segment with
     * an empty string and route it, otherwise return a non-matching
     * result.
     *
     * @param uri The parsed uri to match against
     * @return The match result
     */
    public TreeMatch<T> routeWithEmptyPath(ParsedUri uri) {
        if (uri.getAllowEmptyPathSegments() && !uri.hasSegmentAt(0) && hasPlaceholders()) {
            return route(uri.withSingleEmptySegment(), 0, LabelValuesNode.SENTINEL);
        }
        return UriTreeMatcherMap.noMatch();
    }

    /**
     * Returns {@code true} if this node can accept placeholders.
     *
     * @return {@code true} if this node can accept placeholders.
     */
    private boolean hasPlaceholders() {
        return !placeholders.isEmpty() || !greedyPlaceholders.isEmpty();
    }

    /**
     * Helper class to keep track of the labels captured so
     * far. Implemented as a single linked list as a persistent
     * immutable data structure that avoids copying over all the
     * contents whenever we have to check a different branch of the
     * tree.
     */
    static final class LabelValuesNode {
        private static final LabelValuesNode SENTINEL = new LabelValuesNode();
        private final String key;
        private final CharSequence value;
        private final LabelValuesNode next;

        private LabelValuesNode() {
            // Setting to non-null values such that linters can figure
            // out that those values are never meant to be null.
            key = "<null>";
            value = "<null>";
            next = this;
        }

        LabelValuesNode(String key, CharSequence value, LabelValuesNode next) {
            this.key = Objects.requireNonNull(key);
            this.value = Objects.requireNonNull(value);
            this.next = Objects.requireNonNull(next);
        }

        public LabelValuesNode add(String key, CharSequence value) {
            return new LabelValuesNode(key, value, this);
        }

        public Map<String, String> toMap() {
            if (this == SENTINEL) {
                return Collections.emptyMap();
            }
            Map<String, String> values = new HashMap<>();
            LabelValuesNode current = this;
            while (current != SENTINEL) {
                // handle greedy params correctly by joining them
                // using slashes. Unlike query params the path
                // segments are not URI decoded here, this should be
                // done by the caller.
                String prevValue = values.get(current.key);
                String value = current.value.toString();
                if (prevValue != null) {
                    values.put(current.key, value + "/" + prevValue);
                } else {
                    values.put(current.key, value);
                }
                current = current.next;
            }
            return values;
        }
    }

    /**
     * Helper class to encapsulate the matching logic of the query
     * string pattern (if any) and keep track of the mapped value.
     *
     * @param <T> The type that the URI patterns map to.
     */
    static final class QueryMatcher<T> {
        private final QueryPattern queryPattern;
        private final int pathRank;
        private final T value;

        QueryMatcher(QueryPattern queryPattern, int pathRank, T value) {
            this.queryPattern = queryPattern;
            this.value = Objects.requireNonNull(value);
            this.pathRank = pathRank;
        }

        public TreeMatch<T> match(
                ParsedUri uri,
                LabelValuesNode captures
        ) {
            Match match = matchQuery(uri);
            if (match != null) {
                return new TreeMatch<>(captures::toMap, match, value, pathRank, getQueryRank());
            }
            return UriTreeMatcherMap.noMatch();
        }

        int getQueryRank() {
            if (queryPattern == null) {
                return 0;
            }
            return queryPattern.getRequiredLiteralKeys().size();
        }

        Match matchQuery(ParsedUri uri) {
            if (queryPattern == null) {
                return NO_QUERY_MATCH;
            }

            Map<String, List<String>> queryValues = uri.getQueryValues();
            for (String sq : queryPattern.getRequiredLiteralKeys()) {
                List<String> values = queryValues.get(sq);
                if (!containsOneValue(values, queryPattern.getRequiredLiteralValue(sq))) {
                    return null;
                }
            }
            LabelValues labelValues = new LabelValues();
            for (Map.Entry<String, List<String>> kvp : queryValues.entrySet()) {
                String label = queryPattern.getLabelForKey(kvp.getKey());
                if (label != null) {
                    for (String val : kvp.getValue()) {
                        labelValues.addQueryParamLabelValue(label, val);
                    }
                }
            }
            return new LabelValuesMatch(labelValues);
        }

        private boolean containsOneValue(List<String> values, CharSequence expected) {
            String expectedAsString = null;
            if (expected != null) {
                expectedAsString = expected.toString();
            }
            return values != null && values.size() == 1 && Objects.equals(expectedAsString, values.get(0));
        }
    }
}
