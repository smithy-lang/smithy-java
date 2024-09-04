/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocols.restjson.router;

import java.util.HashSet;
import java.util.Set;
import software.amazon.smithy.java.runtime.io.uri.QueryStringParser;

class QueryStringRouteMatcher implements RouteMatcher {

    private final QueryPattern queryPattern;

    public QueryStringRouteMatcher(QueryPattern queryPattern) {
        if (queryPattern == null) {
            throw new IllegalArgumentException();
        }

        this.queryPattern = queryPattern;
    }

    @Override
    public int getRank() {
        return queryPattern.getRequiredLiteralKeys().size();
    }

    @Override
    public Match match(String query) {
        final LabelValues labelValues = new LabelValues();
        final Set<CharSequence> unfulfilledRequiredLiterals = new HashSet<>(queryPattern.getRequiredLiteralKeys());

        if (!QueryStringParser.parse(query, (key, value) -> {
            // see if the key corresponds to a label mapping
            String label = queryPattern.getLabelForKey(key);
            if (label != null) {
                labelValues.addQueryParamLabelValue(label, value); // already url-decoded by parser
            } else if (queryPattern.getRequiredLiteralKeys().contains(key)) {
                CharSequence requiredValue = queryPattern.getRequiredLiteralValue(key);
                if (requiredValue != null &&
                    !requiredValue.toString().equalsIgnoreCase(value)) {
                    return false;
                }

                unfulfilledRequiredLiterals.remove(key);
            }
            // otherwise it's a parameter we don't care about, so passthrough

            return true;
        })) {
            return null;
        }

        if (!unfulfilledRequiredLiterals.isEmpty()) {
            return null;
        }

        return new LabelValuesMatch(labelValues);
    }
}
