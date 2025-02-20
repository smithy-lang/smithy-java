/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.metrics.otel;

import io.opentelemetry.api.common.Attributes;
import java.util.Map;
import software.amazon.smithy.java.metrics.api.Dimension;

public interface AttributesFactory {

    enum MeasurementKind {
        DATE,
        TIME,
        LEVEL,
        COUNT,
        METRIC,
        RATIO
    }

    Attributes newAttributes(
            String group,
            String name,
            MeasurementKind measurementKind,
            Map<String, String> attributes,
            Dimension... dimensions
    );
}
