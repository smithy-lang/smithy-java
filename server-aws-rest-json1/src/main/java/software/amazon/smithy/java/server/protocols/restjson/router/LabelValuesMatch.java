/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocols.restjson.router;

import java.util.List;

class LabelValuesMatch implements Match {
    private final LabelValues labelValues;

    public LabelValuesMatch(LabelValues labelValues) {
        if (labelValues == null) {
            throw new IllegalArgumentException();
        }

        this.labelValues = labelValues;
    }

    @Override
    public List<String> getLabelValues(String label) {
        return labelValues.getLabelValues(label);
    }

    @Override
    public boolean isPathLabel(String label) {
        return labelValues.isPathLabel();
    }
}
