/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.integrations.aws;

import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.ServiceIndex;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Codegen integration that adds {@code AwsCredentialChainPlugin} as a default plugin for any
 * service that uses an AWS auth scheme (sigv4 or sigv4a).
 */
@SmithyInternalApi
public final class AwsCredentialChainIntegration implements JavaCodegenIntegration {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(AwsCredentialChainIntegration.class);
    private static final String PLUGIN_CLASS = "software.amazon.smithy.java.aws.client.core.AwsCredentialChainPlugin";
    private static final ShapeId SIGV4 = ShapeId.from("aws.auth#sigv4");
    private static final ShapeId SIGV4A = ShapeId.from("aws.auth#sigv4a");

    @Override
    public void customizeSettings(CodeGenerationContext context) {
        Model model = context.model();
        JavaCodegenSettings settings = context.settings();
        var serviceId = settings.getService().orElse(null);
        if (serviceId == null) {
            return;
        }
        var service = model.expectShape(serviceId, ServiceShape.class);
        var authSchemes = ServiceIndex.of(model).getEffectiveAuthSchemes(service);
        if (authSchemes.containsKey(SIGV4) || authSchemes.containsKey(SIGV4A)) {
            // Only register the plugin if it is actually on the codegen classpath. A model can carry AWS auth
            // traits without depending on the AWS client runtime (e.g. serde benchmarks), in which case the
            // plugin cannot be resolved and we skip it rather than failing the build.
            if (isPluginOnClasspath()) {
                settings.addDefaultPlugin(PLUGIN_CLASS);
            } else {
                LOGGER.warn(
                        "Service {} uses an AWS auth scheme but {} is not on the classpath; "
                                + "skipping the default credential chain plugin. Add a dependency on "
                                + "'aws-client-core' to enable it.",
                        service.getId(),
                        PLUGIN_CLASS);
            }
        }
    }

    private static boolean isPluginOnClasspath() {
        try {
            Class.forName(PLUGIN_CLASS, false, AwsCredentialChainIntegration.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
