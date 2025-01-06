/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocols.restjson.router;

import static software.amazon.smithy.java.server.protocols.restjson.router.TreeNode.NO_QUERY_MATCH;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import software.amazon.smithy.java.io.uri.QueryStringParser;

/**
 * A tree based URI Matcher map that implements path specificity URI
 * routing using similar ideas to those of the Radix Tree.
 *
 * <p>The most specific URI path pattern is defined by comparing each
 * of the segments of the path pattern. For two segments in the path A
 * and B we say that A is more specific than B if
 *
 * <ul>
 *     <li>A is a literal and B is a label, OR</li>
 *     <li>A is a non-greedy label and B is a greedy label, OR</li>
 *     <li>A is a non-null and B is non existent for the same
 *     index at which A is found</li>
 * </ul>
 *
 * @param <T> The type that the URI patterns map to.
 */
public final class UriTreeMatcherMap<T> implements UriMatcherMap<T> {
    @SuppressWarnings("rawtypes")
    private static final TreeMatch NO_MATCH = new TreeMatch<>(Collections::emptyMap, NO_QUERY_MATCH, null, 0, 0);
    private final TreeNode<T> root;
    private final boolean allowEmptyPathSegments;

    UriTreeMatcherMap(TreeNode<T> root, boolean allowEmptyPathSegments) {
        this.root = Objects.requireNonNull(root);
        this.allowEmptyPathSegments = allowEmptyPathSegments;
    }

    TreeNode<T> getRoot() {
        return root;
    }

    @Override
    public ValuedMatch<T> match(String uri) {
        ParsedUri parsedUri = new ParsedUri(uri, allowEmptyPathSegments);
        TreeMatch<T> result = root.route(parsedUri);
        if (result.matches()) {
            return result;
        }
        // Callers expect null result if the URI does not match.
        return null;
    }

    public static <T> UriMatcherMapBuilder<T> builder() {
        return new UriTreeMatcherMapBuilder<>();
    }

    /**
     * Internal value to represent a no match result to avoid dealing
     * with null values.
     *
     * @param <T> The type that the URI patterns map to.
     * @return an internal value to represent a no match result
     */
    @SuppressWarnings("unchecked")
    static <T> TreeMatch<T> noMatch() {
        return (TreeMatch<T>) NO_MATCH;
    }

    /**
     * Helper class implementing a {@link ValuedMatch} for a tree
     * based URI matcher.
     *
     * @param <T> The type that the URI patterns map to.
     */
    static class TreeMatch<T> implements ValuedMatch<T> {
        private final Supplier<Map<String, String>> pathLabelsSupplier;
        private final Match queryMatch;
        private final T value;
        private final int pathRank;
        private final int queryRank;

        TreeMatch(
                Supplier<Map<String, String>> pathLabelsSupplier,
                Match queryMatch,
                T value,
                int pathRank,
                int queryRank
        ) {
            this.pathLabelsSupplier = new MemoizingSupplier<>(pathLabelsSupplier);
            this.queryMatch = Objects.requireNonNull(queryMatch);
            this.value = value;
            this.pathRank = pathRank;
            this.queryRank = queryRank;
        }

        @Override
        public List<String> getLabelValues(String label) {
            Map<String, String> pathLabels = pathLabelsSupplier.get();
            if (pathLabels.containsKey(label)) {
                return Collections.singletonList(pathLabels.get(label));
            }
            return queryMatch.getLabelValues(label);
        }

        @Override
        public boolean isPathLabel(String label) {
            return pathLabelsSupplier.get().containsKey(label);
        }

        @Override
        public T getValue() {
            return value;
        }

        boolean isBetterThan(TreeMatch<T> other) {
            if (!matches()) {
                return false;
            }
            if (!other.matches()) {
                return true;
            }
            if (pathRank == other.pathRank) {
                return queryRank > other.queryRank;
            }
            return pathRank > other.pathRank;
        }

        public boolean matches() {
            return value != null;
        }
    }

    /**
     * Internal class to represent the parsed URI.
     */
    static class ParsedUri {
        private static final List<String> SINGLE_EMPTY_SEGMENT = List.of("");
        private final String query;
        private final Supplier<Map<String, List<String>>> queryValuesSupplier;
        private final List<String> segments;
        private final boolean allowEmptyPathSegments;

        ParsedUri(String uri, boolean allowEmptyPathSegments) {
            this(
                    QueryStringParser.getQuery(uri),
                    getPathSegments(QueryStringParser.getRawPath(uri), allowEmptyPathSegments),
                    allowEmptyPathSegments);
        }

        ParsedUri(String query, List<String> segments, boolean allowEmptyPathSegments) {
            this.query = query;
            this.queryValuesSupplier = query == null
                    ? Collections::emptyMap
                    : new MemoizingSupplier<>(() -> QueryStringParser.toMapOfLists(query));
            this.segments = segments;
            this.allowEmptyPathSegments = allowEmptyPathSegments;
        }

        boolean hasSegmentAt(int index) {
            return index < segments.size();
        }

        String getSegment(int index) {
            return segments.get(index);
        }

        String getQuery() {
            return query;
        }

        Map<String, List<String>> getQueryValues() {
            return queryValuesSupplier.get();
        }

        boolean getAllowEmptyPathSegments() {
            return allowEmptyPathSegments;
        }

        ParsedUri withSingleEmptySegment() {
            return new ParsedUri(query, SINGLE_EMPTY_SEGMENT, allowEmptyPathSegments);
        }

        static List<String> getPathSegments(CharSequence path, boolean allowEmptyPathSegments) {
            List<String> pathSegments = splitSegments(path, allowEmptyPathSegments);
            if (pathSegments.size() == 1 && pathSegments.get(0).isEmpty()) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(pathSegments);
        }

        static List<String> splitSegments(CharSequence path, boolean allowEmptyPathSegments) {
            List<String> result = new ArrayList<>();
            int max = path.length();
            int end = 0;
            do {
                int start = indexOfSegmentStart(path, end, allowEmptyPathSegments);
                end = indexOfSegmentEnd(path, start);
                result.add(path.subSequence(start, end).toString());
                if (end == start) {
                    ++end;
                }
            } while (end < max);
            return result;
        }

        static int indexOfSegmentEnd(CharSequence path, int from) {
            int max = path.length();
            int idx = from;
            while (idx < max && path.charAt(idx) != '/') {
                ++idx;
            }
            return idx;
        }

        static int indexOfSegmentStart(CharSequence path, int from, boolean allowEmptyPathSegments) {
            int max = path.length();
            int idx = from;
            if (allowEmptyPathSegments) {
                if (idx < max && path.charAt(idx) == '/') {
                    return idx + 1;
                }
                return idx;
            }
            while (idx < max && path.charAt(idx) == '/') {
                ++idx;
            }
            return idx;
        }
    }
}
