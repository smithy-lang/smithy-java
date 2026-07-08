/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.integrations.settings;

import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Test integration used to verify the lifecycle ordering of settings customization.
 *
 * <p>It registers one default plugin from {@link #customizeSettings(CodeGenerationContext)} (which
 * runs before the client builder is generated and should take effect) and another from
 * {@link #customize(CodeGenerationContext)} (which runs after generation and should be a no-op).
 *
 * <p>This integration is discovered via {@code ServiceLoader} for every codegen run in this module,
 * so it only acts when the marker service is being generated to avoid perturbing other tests.
 */
public final class DefaultPluginSettingsIntegration implements JavaCodegenIntegration {

    private static final ShapeId MARKER_SERVICE =
            ShapeId.from("smithy.java.codegen.settings#DefaultPluginSettingsService");

    @Override
    public String name() {
        return "default-plugin-settings-test";
    }

    @Override
    public void customizeSettings(CodeGenerationContext context) {
        var serviceId = context.settings().getService().orElse(null);
        if (MARKER_SERVICE.equals(serviceId)) {
            context.settings().addDefaultPlugin(EarlyDefaultPlugin.class.getCanonicalName());
        }
    }

    @Override
    public void customize(CodeGenerationContext context) {
        var serviceId = context.settings().getService().orElse(null);
        if (MARKER_SERVICE.equals(serviceId)) {
            context.settings().addDefaultPlugin(LateDefaultPlugin.class.getCanonicalName());
        }
    }
}
