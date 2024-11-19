/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocols.restjson.router;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import software.amazon.smithy.java.io.uri.QueryStringParser;

class QueryPattern {
    private final Map<String, String> labelKeys = new HashMap<>(); // label -> query-key
    private final Map<String, String> keyLabels = new HashMap<>(); // query-key -> label
    private final Map<String, String> requiredLiteralQueryParams = new HashMap<>();

    public QueryPattern(String queryPattern) {
        if (queryPattern == null)
            throw new IllegalArgumentException();

        Map<String, Set<QueryParamValue>> paramToValue = new TreeMap<>();
        QueryStringParser.parse(queryPattern, (key, value) -> {
            String label = null;
            if (value != null)
                label = LabelHelper.getLabel(value);

            QueryParamValue paramValue;
            if (label != null) {
                paramValue = forLabel(label);
                keyLabels.put(key, label);
                labelKeys.put(label, key);
            } else {
                paramValue = forLiteral(value);
                requiredLiteralQueryParams.put(key, value);
            }
            paramToValue.computeIfAbsent(key, x -> new LinkedHashSet<>())
                .add(paramValue);

            return true;
        });
        validateQueryParams(paramToValue);
    }

    public Iterable<String> getLabels() {
        return labelKeys.keySet();
    }

    public String getLabelForKey(String key) {
        return keyLabels.get(key);
    }

    public String getKeyForLabel(String label) {
        return labelKeys.get(label);
    }

    public Collection<String> getRequiredLiteralKeys() {
        return requiredLiteralQueryParams.keySet();
    }

    public String getRequiredLiteralValue(String key) {
        return requiredLiteralQueryParams.get(key);
    }

    public boolean hasRequiredLiteralQueryParams() {
        return !requiredLiteralQueryParams.isEmpty();
    }

    public boolean conflictsWith(QueryPattern queryPattern) {
        return doConflictsWith(
            requiredLiteralQueryParams,
            queryPattern.requiredLiteralQueryParams,
            queryPattern.keyLabels.keySet()
        )
            ||
            doConflictsWith(queryPattern.requiredLiteralQueryParams, requiredLiteralQueryParams, keyLabels.keySet());
    }

    private void validateQueryParams(Map<String, Set<QueryParamValue>> paramToValue) {
        StringBuilder buf = new StringBuilder();
        for (Map.Entry<String, Set<QueryParamValue>> kvp : paramToValue.entrySet()) {
            if (kvp.getValue().size() > 1) {
                buf.append("Query param with key ")
                    .append("\"")
                    .append(kvp.getKey())
                    .append("\" defined multiple times: ")
                    .append(
                        kvp.getValue()
                            .stream()
                            .map(QueryParamValue::toString)
                            .collect(Collectors.joining(", "))
                    )
                    .append('\n');
            }
        }
        if (!buf.isEmpty()) {
            throw new IllegalArgumentException(buf.toString());
        }
    }

    private static boolean doConflictsWith(
        Map<String, String> a,
        Map<String, String> b,
        Collection<String> bLabels
    ) {
        if (a.size() == 0 && b.size() == 0)
            return true;

        for (Map.Entry<String, String> entryA : a.entrySet()) {
            // if a's literals are labels in B, it's always ambiguous
            if (bLabels.contains(entryA.getKey()))
                return true;

            // for each member of a, see if it exists in b
            // if not, no conflict
            // if so, make sure both have distinct keys
            if (!b.containsKey(entryA.getKey()))
                return false;

            String valueA = entryA.getValue();
            String valueB = b.get(entryA.getKey());

            // if both have the same key, they must each specify a value, and they must be distinct
            if (valueA.equalsIgnoreCase(valueB)) {
                return true;
            }
        }

        return false;
    }

    private static QueryParamValue forLabel(String label) {
        return new QueryParamValue(true, label);
    }

    private static QueryParamValue forLiteral(String value) {
        return new QueryParamValue(false, value);
    }

    static class QueryParamValue {
        private final boolean isLabel;
        private final String value;

        QueryParamValue(boolean isLabel, String value) {
            this.isLabel = isLabel;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            QueryParamValue that = (QueryParamValue) o;
            return isLabel == that.isLabel && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(isLabel, value);
        }

        @Override
        public String toString() {
            if (isLabel) {
                return "label bound to \"" + value + "\"";
            }
            if (value != null) {
                return "literal with value \"" + value + "\"";
            }
            return "literal without a value";
        }
    }
}
