/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.util.List;
import java.util.Objects;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
public record JavaCodegenSettings(
        ShapeId service
) {
    private static final String SERVICE = "service";

    public JavaCodegenSettings {
        Objects.requireNonNull(service);
    }

    /**
     * Creates a settings object from a plugin settings node
     *
     * @param settingsNode Settings node to load
     * @return Parsed settings
     */
    public static JavaCodegenSettings fromNode(ObjectNode settingsNode) {
        settingsNode.warnIfAdditionalProperties(List.of(SERVICE));
        return new JavaCodegenSettings(settingsNode.expectStringMember(SERVICE).expectShapeId());
    }
}
