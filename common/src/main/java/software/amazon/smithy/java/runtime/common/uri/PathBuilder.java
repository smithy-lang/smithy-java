/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.common.uri;

public final class PathBuilder {

    private final StringBuilder builder = new StringBuilder();

    public String build() {
        return builder.toString();
    }

    public PathBuilder addSegment(String segment) {
        if (segment.isEmpty()) {
            throw new IllegalArgumentException("Cannot add an empty path segment");
        }
        builder.append('/');
        URLEncoding.encodeUnreserved(segment, builder);
        return this;
    }
}
