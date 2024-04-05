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
    ShapeId service,
    String packageNamespace
) {
    private static final String SERVICE = "service";
    private static final String NAMESPACE = "namespace";

    public JavaCodegenSettings {
        Objects.requireNonNull(service);
        Objects.requireNonNull(packageNamespace);
    }

    /**
     * Creates a settings object from a plugin settings node
     *
     * @param settingsNode Settings node to load
     * @return Parsed settings
     */
    public static JavaCodegenSettings fromNode(ObjectNode settingsNode) {
        settingsNode.warnIfAdditionalProperties(List.of(SERVICE, NAMESPACE));
        return new JavaCodegenSettings(
            settingsNode.expectStringMember(SERVICE).expectShapeId(),
            settingsNode.expectStringMember(NAMESPACE).getValue()
        );
    }

    // TODO: actually have this return lines, not placeholder
    public List<String> headerLines() {
        return List.of("Header Line 1", "Header Line 2");
    }
}
