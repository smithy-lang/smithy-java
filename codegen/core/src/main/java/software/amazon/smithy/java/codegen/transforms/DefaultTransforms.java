/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.transforms;

import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.model.Model;

/**
 * Default transformations applied to Smithy models before code generation.
 */
public final class DefaultTransforms {
    public static Model transform(Model model, JavaCodegenSettings settings) {
        model = RemoveDeprecatedShapesTransformer.transform(model, settings);
        model = MakeIdempotencyTokenClientOptional.transform(model);
        return model;
    }
}
