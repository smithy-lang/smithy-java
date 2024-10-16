/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.transforms;

import java.util.HashSet;
import java.util.Set;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.DeprecatedTrait;
import software.amazon.smithy.model.transform.ModelTransformer;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Filters out shapes that filters out shapes that have were deprecated before a specified version or date.
 *
 * <p><strong>Note:</strong>This transform should only be used to filter client-side shapes as services must often
 * continue to support shapes even after they are initially deprecated.
 *
 * @see <a href="https://smithy.io/2.0/guides/building-codegen/configuring-the-generator.html#relativedate-client-and-type-codegen-only">Codegen Deprecated Shape Filtering</a>
 */
// TODO: Upstream to Directed codegen standard transforms.
@SmithyInternalApi
public final class RemoveDeprecatedShapesTransformer {

    public static Model transform(Model model, JavaCodegenSettings settings) {
        var relativeDate = settings.relativeDate();
        var relativeVersion = settings.relativeVersion();

        // If there are no filters. Exit without traversing shapes
        if (relativeVersion == null && relativeDate == null) {
            return model;
        }

        var serviceShape = model.expectShape(settings.service());
        Set<Shape> shapesToRemove = new HashSet<>();
        for (var shape : new Walker(model).walkShapes(serviceShape)) {
            if (shape.hasTrait(DeprecatedTrait.class)) {
                var sinceOptional = shape.expectTrait(DeprecatedTrait.class).getSince();

                if (sinceOptional.isEmpty()) {
                    continue;
                }

                var since = sinceOptional.get();
                // Remove any shapes that were deprecated before the specified date
                if (relativeDate != null && CodegenUtils.isISO8601Date(since)) {
                    if (relativeDate.compareTo(since) > 0) {
                        shapesToRemove.add(shape);
                    }
                }

                // Remove any shapes that were deprecated before the specified version.
                if (relativeVersion != null && CodegenUtils.isSemVer(since)) {
                    if (compareSemVer(relativeVersion, since) > 0) {
                        shapesToRemove.add(shape);
                    }
                }
            }
        }

        return ModelTransformer.create().removeShapes(model, shapesToRemove);
    }

    static int compareSemVer(String semVer1, String semVer2) {
        String[] versionComponents1 = semVer1.split("\\.");
        String[] versionComponents2 = semVer2.split("\\.");

        int maxLength = Math.max(versionComponents1.length, versionComponents2.length);
        for (int i = 0; i < maxLength; i++) {
            // Treat all implicit components as 0's
            var component1 = i >= versionComponents1.length ? "0" : versionComponents1[i];
            var component2 = i >= versionComponents2.length ? "0" : versionComponents2[i];
            var comparison = component1.compareTo(component2);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }
}
