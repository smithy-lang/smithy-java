/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.generators;

import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.java.client.rulesengine.RulesEngineBuilder;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.rulesengine.traits.EndpointBddTrait;

public class BddInfoGenerator
        implements Consumer<GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings>> {
    @Override
    public void accept(GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        try {
            var baseDir = directive.fileManifest().getBaseDir();
            var fileDir = baseDir.resolve("resources/META-INF/endpoints/bdd-info.bin");
            var parentDir = fileDir.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            var engineBuilder = new RulesEngineBuilder();
            var bddTrait = directive.service().expectTrait(EndpointBddTrait.class);
            var bytecode = engineBuilder.compile(bddTrait);
            Files.write(fileDir, bytecode.getBytecode());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write BDD bytecode binary file", e);
        }
    }
}
