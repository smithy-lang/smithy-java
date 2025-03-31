/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.server.restjson.router;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class LabelValues {
    private final Map<String, List<String>> map = new HashMap<>();
    private boolean isPathLabel;

    public List<String> getLabelValues(String label) {
        return map.get(label);
    }

    public Iterable<String> getKeys() {
        return map.keySet();
    }

    public void addUriPathLabelValue(String label, String value) {
        addLabelValue(label, value, true);
    }

    public void addQueryParamLabelValue(String label, String value) {
        addLabelValue(label, value, false);
    }

    private void addLabelValue(String label, String value, boolean isPathLabel) {
        List<String> values = map.computeIfAbsent(label, k -> new ArrayList<>());
        values.add(value);
        this.isPathLabel = isPathLabel;
    }

    public boolean isPathLabel() {
        return isPathLabel;
    }
}
