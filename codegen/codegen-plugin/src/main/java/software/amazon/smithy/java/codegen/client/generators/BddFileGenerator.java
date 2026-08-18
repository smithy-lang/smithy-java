/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.generators;

import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.rulesengine.Bytecode;
import software.amazon.smithy.java.rulesengine.JavaEndpointResolverGenerator;
import software.amazon.smithy.java.rulesengine.RulesEngineBuilder;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.rulesengine.traits.EndpointBddTrait;
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class BddFileGenerator
        implements Consumer<GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings>> {
    @Override
    public void accept(GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        var service = directive.expectService();
        var serviceName = service.toShapeId().getName();
        var bytecode = compileBytecode(service);
        var clientSymbol = directive.symbol();
        var namespace = clientSymbol.getNamespace();
        var resolverName = serviceName + "EndpointResolver";
        var definitionFile = clientSymbol.getDefinitionFile();
        var separator = definitionFile.lastIndexOf('/');
        var resolverFile = definitionFile.substring(0, separator + 1) + resolverName + ".java";
        var generated = new JavaEndpointResolverGenerator(bytecode).generate(namespace, resolverName);
        var body = generated.substring(generated.indexOf("\n\n") + 2);
        directive.context()
                .writerDelegator()
                .useFileWriter(resolverFile, namespace, writer -> writer.write("$L", body));
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
