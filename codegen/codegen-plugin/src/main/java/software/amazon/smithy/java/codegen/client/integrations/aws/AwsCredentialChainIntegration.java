/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.integrations.aws;

import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
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

    private static final String PLUGIN_CLASS = "software.amazon.smithy.java.aws.client.core.AwsCredentialChainPlugin";
    private static final ShapeId SIGV4 = ShapeId.from("aws.auth#sigv4");
    private static final ShapeId SIGV4A = ShapeId.from("aws.auth#sigv4a");

    @Override
    public void customize(CodeGenerationContext context) {
        Model model = context.model();
        JavaCodegenSettings settings = context.settings();
        var service = model.expectShape(settings.service(), ServiceShape.class);
        var authSchemes = ServiceIndex.of(model).getEffectiveAuthSchemes(service);
        if (authSchemes.containsKey(SIGV4) || authSchemes.containsKey(SIGV4A)) {
            settings.addDefaultPlugin(PLUGIN_CLASS);
        }
    }
}
