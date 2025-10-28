/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.generators;

import static java.lang.String.format;

import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.java.client.rulesengine.Bytecode;
import software.amazon.smithy.java.client.rulesengine.RulesEngineBuilder;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.rulesengine.traits.EndpointBddTrait;
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait;

public class BddFileGenerator
        implements Consumer<GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings>> {
    @Override
    public void accept(GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        try {
            var baseDir = directive.fileManifest().getBaseDir();
            var serviceName = directive.service().toShapeId().getName();
            var fileDir = baseDir.resolve(format("resources/META-INF/endpoints/%s.bdd", serviceName));
            var parentDir = fileDir.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            var bytecode = compileBytecode(directive.service());
            Files.write(fileDir, bytecode.getBytecode());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write BDD bytecode binary file", e);
        }
    }

    private Bytecode compileBytecode(ServiceShape serviceShape) {
        var engineBuilder = new RulesEngineBuilder();
        if (serviceShape.hasTrait(EndpointBddTrait.ID)) {
            return engineBuilder.compile(serviceShape.expectTrait(EndpointBddTrait.class));
        } else {
            return engineBuilder.compile(serviceShape.expectTrait(EndpointRuleSetTrait.class).getEndpointRuleSet());
        }
    }
}
