/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocols.restjson.router;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link UriMatcherMapBuilder} implementation that builds a tree
 * based matcher.
 *
 * @param <T> The type that the URI patterns map to.
 */
final class UriTreeMatcherMapBuilder<T> implements UriMatcherMapBuilder<T> {
    private TreeNodeBuilder<T> root = new TreeNodeBuilder<>();
    private Boolean allowEmptyPathSegments;

    /**
     * {@inheritDoc}
     */
    @Override
    public void add(UriPattern pattern, T mapping) {
        if (allowEmptyPathSegments == null) {
            allowEmptyPathSegments = pattern.getAllowEmptyPathSegments();
        } else {
            // Paranoid check, allowEmptyPathSegments is a per-service setting
            // so, it must never change from path to path.
            if (allowEmptyPathSegments != pattern.getAllowEmptyPathSegments()) {
                throw new RuntimeException("Two different patterns disagree on `allowEmptyPathSegments`");
            }
        }
        root = root.addPattern(pattern, mapping);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UriMatcherMap<T> build() {
        return new UriTreeMatcherMap<>(root.seal(), allowEmptyPathSegments);
    }

    /**
     * Tree node builder that holds all the state needed during the
     * build process.
     *
     * @param <T> The type that the URI patterns map to.
     */
    static final class TreeNodeBuilder<T> {
        final String content;
        final List<TreeNode.QueryMatcher<T>> queryMatchers;
        final Map<String, TreeNodeBuilder<T>> children;
        final Map<String, TreeNodeBuilder<T>> placeholdersMap;
        final Map<String, TreeNodeBuilder<T>> greedyPlaceholdersMap;

        final boolean isGreedyPlaceholder;
        final boolean isPlaceholder;

        TreeNodeBuilder() {
            this("<root>", new ArrayList<>(), false, false);
        }

        TreeNodeBuilder(
            CharSequence content,
            List<TreeNode.QueryMatcher<T>> queryMatchers,
            Map<String, TreeNodeBuilder<T>> children,
            Map<String, TreeNodeBuilder<T>> placeholdersMap,
            Map<String, TreeNodeBuilder<T>> greedyPlaceholdersMap,
            boolean isPlaceholder,
            boolean isGreedyPlaceholder
        ) {
            this.content = Objects.requireNonNull(content).toString();
            this.queryMatchers = Objects.requireNonNull(queryMatchers);
            this.children = Objects.requireNonNull(children);
            this.placeholdersMap = Objects.requireNonNull(placeholdersMap);
            this.greedyPlaceholdersMap = Objects.requireNonNull(greedyPlaceholdersMap);
            this.isPlaceholder = isPlaceholder;
            this.isGreedyPlaceholder = isGreedyPlaceholder;
        }

        TreeNodeBuilder(
            CharSequence content,
            List<TreeNode.QueryMatcher<T>> queryMatcher,
            boolean isPlaceholder,
            boolean isGreedyPlaceholder
        ) {
            this(
                content,
                queryMatcher,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                isPlaceholder,
                isGreedyPlaceholder
            );
        }

        TreeNodeBuilder<T> addPattern(UriPattern pattern, T mapping) {
            if (pattern.getPathPattern().getSegments().isEmpty()) {
                return toLeaf(pattern.getQueryPattern(), 0, mapping);
            } else {
                return addAllSegments(pattern, 0, mapping);
            }
        }

        /**
         * Adds all the segments of the path pattern starting at the
         * given index to the tree builder.
         *
         * @param pattern The pattern to get the segments from
         * @param start   The index where we start adding from
         * @param mapping The mapped value to be returned when a URI matches this pattern.
         * @return The updated tree node
         */
        TreeNodeBuilder<T> addAllSegments(UriPattern pattern, int start, T mapping) {
            List<PathPattern.Segment> segments = pattern.getPathPattern().getSegments();
            if (segments.size() - 1 == start) {
                addLeaf(
                    segments.get(start),
                    pattern.getQueryPattern(),
                    getPathPatternRank(segments),
                    mapping
                );
            } else {
                addSegment(segments.get(start)).addAllSegments(pattern, start + 1, mapping);
            }
            return this;
        }

        /**
         * The path pattern rank is computed by the total number of
         * non-label segments.
         *
         * @param segments The path pattern segments
         * @return The rank of the segments
         */
        static int getPathPatternRank(List<PathPattern.Segment> segments) {
            int result = 0;
            for (PathPattern.Segment segment : segments) {
                if (!segment.isLabel()) {
                    ++result;
                }
            }
            return result;
        }

        /**
         * Adds a single segment to the tree node.
         *
         * @param segment The segment to be added
         * @return The updated tree node
         */
        TreeNodeBuilder<T> addSegment(PathPattern.Segment segment) {
            return getMapForSegment(segment)
                .computeIfAbsent(
                    segment.getContent().toString(),
                    k -> from(segment)
                );
        }

        /**
         * Converts the node to a leaf node using the query pattern, path rank and mapped value.
         *
         * @param pattern        The query pattern for this leaf node
         * @param pathRank       The path rank for this leaf node
         * @param mapping        The mapped value to be returned when a URI matches this pattern.
         * @return The updated tree node
         */
        TreeNodeBuilder<T> toLeaf(QueryPattern pattern, int pathRank, T mapping) {
            TreeNode.QueryMatcher<T> queryMatcher = new TreeNode.QueryMatcher<>(pattern, pathRank, mapping);
            queryMatchers.add(queryMatcher);
            return this;
        }

        /**
         * Adds a leaf node to this node.
         *
         * @param segment        The leaf path segment
         * @param pattern        The query pattern for this leaf node
         * @param pathRank       The path rank for this leaf node
         * @param mapping        The mapped value to be returned when a URI matches this pattern.
         * @return The updated tree node
         */
        TreeNodeBuilder<T> addLeaf(
            PathPattern.Segment segment,
            QueryPattern pattern,
            int pathRank,
            T mapping
        ) {
            String key = segment.getContent().toString();
            Map<String, TreeNodeBuilder<T>> contentToNode = getMapForSegment(segment);
            TreeNodeBuilder<T> result = contentToNode.get(key);
            if (result == null) {
                result = from(segment, pattern, pathRank, mapping);
                contentToNode.put(key, result);
            } else {
                result.toLeaf(pattern, pathRank, mapping);
            }
            return result;
        }

        /**
         * Gets the correct map to add the leaf to depending on whether the
         * segment is label, greedy label or child.
         *
         * @param segment The segment to select the map for
         * @return The map to add the leaf to.
         */
        Map<String, TreeNodeBuilder<T>> getMapForSegment(PathPattern.Segment segment) {
            if (segment.isLabel()) {
                if (segment.isGreedyLabel()) {
                    return greedyPlaceholdersMap;
                }
                return placeholdersMap;
            }
            return children;
        }

        /**
         * Creates a tree node from the given path segment.
         *
         * @param segment The path segment to create the node from
         * @param <T>     The type that the URI patterns map to.
         * @return A new tree node from the given path segment.
         */
        static <T> TreeNodeBuilder<T> from(PathPattern.Segment segment) {
            return new TreeNodeBuilder<>(
                segment.getContent(),
                new ArrayList<>(),
                segment.isLabel(),
                segment.isGreedyLabel()
            );
        }

        /**
         * Creates a leaf tree node from the given path segment.
         *
         * @param segment        The path segment to create the node from
         * @param pattern        The query pattern for this leaf node
         * @param pathRank       The path rank for this leaf node
         * @param mapping        The mapped value to be returned when an URI matches this pattern.
         * @param <T>            The type that the URI patterns map to.
         * @return A new leaf tree node from the given path segment.
         */
        static <T> TreeNodeBuilder<T> from(
            PathPattern.Segment segment,
            QueryPattern pattern,
            int pathRank,
            T mapping
        ) {
            List<TreeNode.QueryMatcher<T>> queryMatchers = new ArrayList<>();
            queryMatchers.add(new TreeNode.QueryMatcher<>(pattern, pathRank, mapping));
            return new TreeNodeBuilder<>(
                segment.getContent(),
                queryMatchers,
                segment.isLabel(),
                segment.isGreedyLabel()
            );
        }

        /**
         * Finalizes the build process by converting all collections
         * to immutable and creating a new TreeNode instance with the
         * data collected during the build process.
         *
         * @return The newly built {@link TreeNode} instance.
         */
        TreeNode<T> seal() {
            List<TreeNode.QueryMatcher<T>> newQueryMatchers;
            if (queryMatchers.isEmpty()) {
                newQueryMatchers = Collections.emptyList();
            } else {
                newQueryMatchers = Collections.unmodifiableList(queryMatchers);
            }
            return new TreeNode<>(
                content,
                newQueryMatchers,
                sealAll(children),
                sealAllPlaceholders(placeholdersMap),
                sealAllPlaceholders(greedyPlaceholdersMap),
                isPlaceholder,
                isGreedyPlaceholder
            );
        }

        /**
         * Recursively seals all the mapped children and returns an
         * immutable map of segments to tree-nodes.
         *
         * @return The newly built map of children.
         */
        static <T> Map<String, TreeNode<T>> sealAll(Map<String, TreeNodeBuilder<T>> children) {
            if (children.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<String, TreeNode<T>> newNodes = new HashMap<>(children.size());
            children.forEach((k, v) -> newNodes.put(k, v.seal()));
            return Collections.unmodifiableMap(newNodes);
        }

        /**
         * Recursively seals all the nodes and returns an immutable
         * list of tree-nodes.
         *
         * @param nodes The map of children to seal
         * @return The new unmodifiable list with all the
         * mapped child nodes sealed.
         */
        static <T> List<TreeNode<T>> sealAllPlaceholders(Map<String, TreeNodeBuilder<T>> nodes) {
            if (nodes.isEmpty()) {
                return Collections.emptyList();
            }
            List<TreeNode<T>> newNodes = new ArrayList<>(nodes.size());
            for (TreeNodeBuilder<T> node : nodes.values()) {
                newNodes.add(node.seal());
            }
            return Collections.unmodifiableList(newNodes);
        }
    }
}
