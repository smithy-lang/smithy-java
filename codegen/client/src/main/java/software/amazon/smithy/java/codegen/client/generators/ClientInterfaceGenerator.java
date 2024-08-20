/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.generators;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.client.ClientSymbolProperties;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthScheme;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.runtime.client.core.ClientTransport;
import software.amazon.smithy.java.runtime.client.core.ProtocolSettings;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.runtime.client.http.JavaHttpClientTransport;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.OperationIndex;
import software.amazon.smithy.model.knowledge.ServiceIndex;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ToShapeId;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.utils.SmithyInternalApi;
import software.amazon.smithy.utils.StringUtils;

@SmithyInternalApi
public final class ClientInterfaceGenerator
    implements Consumer<GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings>> {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(ClientInterfaceGenerator.class);

    @Override
    public void accept(GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        // Write synchronous interface
        writeForSymbol(directive.symbol(), directive);
        // Write async interface
        writeForSymbol(directive.symbol().expectProperty(ClientSymbolProperties.ASYNC_SYMBOL), directive);
    }

    private static void writeForSymbol(
        Symbol symbol,
        GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive
    ) {
        directive.context()
            .writerDelegator()
            .useFileWriter(symbol.getDefinitionFile(), symbol.getNamespace(), writer -> {
                writer.pushState(new ClassSection(directive.shape()));
                var template = """
                    public interface ${interface:T} {

                        ${operations:C|}

                        static Builder builder() {
                            return new Builder();
                        }

                        final class Builder extends ${client:T}.Builder<${interface:T}, Builder> {
                            ${?hasDefaultProtocol}${defaultProtocol:C|}
                            ${/hasDefaultProtocol}private Builder() {
                                ${?hasDefaultProtocol}configBuilder().protocol(factory.createProtocol(settings, protocolTrait));${/hasDefaultProtocol}
                                ${?authSchemes}configBuilder().putSupportedAuthSchemes(${#authSchemes}new ${value:T}()${^key.last}, ${/key.last}${/authSchemes});${/authSchemes}
                                ${?transport}configBuilder().transport(new ${transport:T}());${/transport}
                            }

                            @Override
                            public ${interface:T} build() {
                                return new ${impl:T}(this);
                            }
                        }
                    }
                    """;
                var defaultProtocolTrait = getDefaultProtocolTrait(directive.model(), directive.settings());
                writer.putContext("hasDefaultProtocol", defaultProtocolTrait != null);
                writer.putContext(
                    "defaultProtocol",
                    new DefaultProtocolGenerator(
                        writer,
                        directive.settings().packageNamespace(),
                        defaultProtocolTrait,
                        directive.context()
                    )
                );
                writer.putContext("client", Client.class);
                writer.putContext("interface", symbol);
                writer.putContext("impl", symbol.expectProperty(ClientSymbolProperties.CLIENT_IMPL));
                writer.putContext("transport", getTransportClass(directive.settings()));
                writer.putContext(
                    "operations",
                    new OperationMethodGenerator(
                        writer,
                        directive.shape(),
                        directive.symbolProvider(),
                        symbol,
                        directive.model()
                    )
                );
                writer.putContext("authSchemes", getAuthSchemes(directive.model(), directive.service()));
                writer.write(template);
                writer.popState();
            });
    }

    private static Class<? extends ClientTransport> getTransportClass(JavaCodegenSettings settings) {
        if (settings.transport() == null) {
            return null;
        }
        // Use one of the built-in transports
        if (settings.transport().equals("http-java")) {
            return JavaHttpClientTransport.class;
        } else if (settings.transport().equals("http-netty")) {
            // TODO: Add netty transport once supported
            throw new CodegenException("Netty default transport not yet supported");
        }

        // TODO: Handle custom transports
        throw new UnsupportedOperationException("Custom default transports not yet supported");
    }

    private record OperationMethodGenerator(
        JavaWriter writer, ServiceShape service, SymbolProvider symbolProvider, Symbol symbol, Model model
    ) implements Runnable {

        @Override
        public void run() {
            writer.pushState();
            var template = """
                default ${?async}${future:T}<${/async}${output:T}${?async}>${/async} ${name:L}(${input:T} input) {
                    return ${name:L}(input, null);
                }

                ${?async}${future:T}<${/async}${output:T}${?async}>${/async} ${name:L}(${input:T} input, ${overrideConfig:T} overrideConfig);
                """;
            writer.putContext("async", symbol.expectProperty(ClientSymbolProperties.ASYNC));
            writer.putContext("overrideConfig", RequestOverrideConfig.class);
            writer.putContext("future", CompletableFuture.class);

            var opIndex = OperationIndex.of(model);
            for (var operation : TopDownIndex.of(model).getContainedOperations(service)) {
                writer.pushState();
                writer.putContext("name", StringUtils.uncapitalize(CodegenUtils.getDefaultName(operation, service)));
                writer.putContext("input", symbolProvider.toSymbol(opIndex.expectInputShape(operation)));
                writer.putContext("output", symbolProvider.toSymbol(opIndex.expectOutputShape(operation)));
                writer.write(template);
                writer.popState();
            }
            writer.popState();
        }
    }

    private record DefaultProtocolGenerator(
        JavaWriter writer, String namespace, Trait defaultProtocolTrait, CodeGenerationContext context
    ) implements
        Runnable {
        @Override
        public void run() {
            if (defaultProtocolTrait == null) {
                return;
            }
            writer.pushState();
            var template = """
                private static final ${protocolSettings:T} settings = ${protocolSettings:T}.builder()
                        .namespace(${serviceNamespace:S})
                        .build();
                private static final ${trait:T} protocolTrait = ${initializer:C};
                private static final ${clientProtocolFactory:T}<${trait:T}> factory = new ${?outer}${outer:T}.${name:L}${/outer}${^outer}${type:T}${/outer}();
                """;
            writer.putContext("protocolSettings", ProtocolSettings.class);
            writer.putContext("clientProtocolFactory", ClientProtocolFactory.class);
            writer.putContext("trait", defaultProtocolTrait.getClass());
            var initializer = context.getInitializer(defaultProtocolTrait);
            writer.putContext("initializer", writer.consumer(w -> initializer.accept(w, defaultProtocolTrait)));
            writer.putContext("serviceNamespace", namespace);
            var factoryClass = getFactory(defaultProtocolTrait.toShapeId());
            if (factoryClass.isMemberClass()) {
                writer.putContext("outer", factoryClass.getEnclosingClass());
            }
            writer.putContext("name", factoryClass.getSimpleName());
            writer.putContext("type", factoryClass);
            writer.write(template);
            writer.popState();
        }
    }

    private static Trait getDefaultProtocolTrait(Model model, JavaCodegenSettings settings) {
        var defaultProtocol = settings.getDefaultProtocol();
        if (defaultProtocol == null) {
            return null;
        }

        // Check that specified protocol matches one of the protocol traits on the service shape
        var index = ServiceIndex.of(model);
        var protocols = index.getProtocols(settings.service());
        if (protocols.containsKey(defaultProtocol)) {
            return protocols.get(defaultProtocol);
        }

        throw new UnsupportedOperationException(
            "Specified protocol `" + defaultProtocol + "` not found on service "
                + settings.service() + ". Expected one of: " + protocols.keySet() + "."
        );
    }

    private static Class<? extends ClientProtocolFactory> getFactory(ShapeId defaultProtocol) {
        for (var factory : ServiceLoader.load(
            ClientProtocolFactory.class,
            ClientInterfaceGenerator.class.getClassLoader()
        )) {
            if (factory.id().equals(defaultProtocol)) {
                return factory.getClass();
            }
        }
        throw new CodegenException("Could not find factory for " + defaultProtocol);
    }

    private static Collection<Class<? extends AuthScheme>> getAuthSchemes(Model model, ToShapeId service) {
        var index = ServiceIndex.of(model);
        var schemes = index.getAuthSchemes(service);
        Map<String, Class<? extends AuthScheme>> result = new HashMap<>();
        for (var scheme : ServiceLoader.load(AuthScheme.class, ClientInterfaceGenerator.class.getClassLoader())) {
            if (schemes.containsKey(ShapeId.from(scheme.schemeId()))) {
                var existing = result.put(scheme.schemeId(), scheme.getClass());
                if (existing != null) {
                    throw new CodegenException(
                        "Multiple auth scheme implementations found for scheme: " + scheme.schemeId()
                            + "Found: " + scheme + " and " + existing
                    );
                }
            } else {
                LOGGER.warn("Could not find implementation for auth scheme {}", scheme);
            }
        }
        return result.values();
    }
}
