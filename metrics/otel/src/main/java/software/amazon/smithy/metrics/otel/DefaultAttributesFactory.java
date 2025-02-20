/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.metrics.otel;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.util.Map;
import software.amazon.smithy.java.metrics.api.Dimension;
import software.amazon.smithy.java.metrics.api.MetricsReporter;

/**
 * The default AttributeFactory that adds attributes for:
 *
 * <ul>
 *     <li>"group": Uses the group name, if not empty</li>
 *     <li>Each attribute associated with the group, set via {@link MetricsReporter#addProperty}</li>
 *     <li>Each {@link Dimension}</li>
 * </ul>
 */
public final class DefaultAttributesFactory implements AttributesFactory {
    @Override
    public Attributes newAttributes(
            String group,
            String name,
            MeasurementKind kind,
            Map<String, String> attributes,
            Dimension... dimensions
    ) {
        AttributesBuilder builder = Attributes.builder();

        if (!group.isEmpty()) {
            builder.put("group", group);
        }

        for (var entry : attributes.entrySet()) {
            builder.put(entry.getKey(), entry.getValue());
        }

        for (Dimension dimension : dimensions) {
            builder.put(dimension.name(), dimension.value());
        }

        return builder.build();
    }
}
