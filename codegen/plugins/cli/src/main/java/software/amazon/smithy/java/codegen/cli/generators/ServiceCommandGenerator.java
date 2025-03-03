/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.cli.generators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.java.cli.CliUtils;
import software.amazon.smithy.java.cli.commands.AggregateCommand;
import software.amazon.smithy.java.cli.commands.Command;
import software.amazon.smithy.java.cli.commands.OperationCommand;
import software.amazon.smithy.java.client.core.Client;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.ClientTransportFactory;
import software.amazon.smithy.java.client.core.ProtocolSettings;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeFactory;
import software.amazon.smithy.java.client.http.JavaHttpClientTransport;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.integrations.core.GenericTraitInitializer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.ServiceIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ToShapeId;
import software.amazon.smithy.model.traits.TitleTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.utils.SmithyGenerated;
import software.amazon.smithy.utils.StringUtils;

// TODO: support extra commands for single-service clients
// TODO: Could these just be generated more flat and package private? Does it matter?
public class ServiceCommandGenerator
        implements Consumer<GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings>> {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(ServiceCommandGenerator.class);

    private static final Map<ShapeId, Class<? extends AuthSchemeFactory>> authSchemeFactories = new HashMap<>();
    private static final Map<String, Class<? extends ClientTransportFactory>> clientTransportFactories =
            new HashMap<>();
    static {
        // Add all trait services to a map, so they can be queried for a provider class
        ServiceLoader.load(AuthSchemeFactory.class, ServiceCommandGenerator.class.getClassLoader())
                .forEach((service) -> authSchemeFactories.put(service.schemeId(), service.getClass()));
        // Add all transport services to a map, so they can be queried for a provider class
        ServiceLoader.load(ClientTransportFactory.class, ServiceCommandGenerator.class.getClassLoader())
                .forEach((service) -> clientTransportFactories.put(service.name(), service.getClass()));
    }

    @Override
    public void accept(GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        directive.context()
                .writerDelegator()
                .useSymbolWriter(directive.symbol(), writer -> {
                    writer.pushState();
                    var template =
                            """
                                    @${smithyGenerated:T}
                                    public class ${shape:T} extends ${aggregateCmd:T} {
                                        ${defaultProtocolTrait:C|}${?hasDefaultSchemes}
                                        ${defaultAuthTraits:C|}${/hasDefaultSchemes}

                                        private final ${list:T}<${cmd:T}> commands = new ${arraylist:T}<>();
                                        private final ${string:T} parent;

                                        public ${shape:T}(${string:T} parent) {
                                            this.parent = parent;${#operations}
                                            addCommand(new ${operationCmd:T}<>(parent == null ? ${name:S} : parent + " " + ${name:S}, ${key:S}, ${value:T}.instance()));${/operations}
                                        }

                                        @Override
                                        public ${string:T} parent() {
                                            return parent;
                                        }

                                        @Override
                                        public ${string:T} name() {
                                            return ${name:S};
                                        }

                                        ${?description}
                                        @Override
                                        public String description() {
                                            return ${description:S};
                                        }
                                        ${/description}
                                        @Override
                                        public void modifyEnv(${cmd:T}.Env env) {
                                            env.clientBuilder()
                                                .putConfig(${client:T}.SERVICE_KEY, ${name:S})${?hasDefaultProtocol}
                                                .protocol(${protocolFactory:C}.createProtocol(settings, protocolTrait))${/hasDefaultProtocol}${?hasDefaultTransport}
                                                .transport(new ${transport:T}())${/hasDefaultTransport}${?hasDefaultSchemes}
                                                .putSupportedAuthSchemes(${authFactories:C})${/hasDefaultSchemes}${?hasDefaultPlugins}
                                                ${plugins:C|}${/hasDefaultPlugins};
                                        }
                                    }
                                    """;
                    writer.putContext("shape", directive.symbol());
                    writer.putContext("string", String.class);
                    writer.putContext("aggregateCmd", AggregateCommand.class);
                    writer.putContext("cmd", Command.class);
                    writer.putContext("list", List.class);
                    writer.putContext("client", Client.class);
                    writer.putContext("arraylist", ArrayList.class);
                    writer.putContext("name", directive.settings().name().toLowerCase(Locale.ENGLISH));
                    writer.putContext("helpPath", "/" + directive.settings().name());
                    writer.putContext("operationCmd", OperationCommand.class);
                    writer.putContext("smithyGenerated", SmithyGenerated.class);
                    writer.putContext("hasDefaultTransport", directive.settings().transport() != null);
                    var operationMap = getOperationMap(
                            directive.operations(),
                            directive.symbolProvider(),
                            directive.service());
                    writer.putContext("operations", operationMap);
                    writer.putContext(
                            "description",
                            directive.shape().getTrait(TitleTrait.class).map(TitleTrait::getValue));

                    // TODO: Consolidate logic for this with client generator?
                    // DEFAULT PROTOCOLS
                    var defaultProtocolTrait = getDefaultProtocolTrait(directive.model(), directive.settings());
                    writer.putContext("hasDefaultProtocol", defaultProtocolTrait != null);
                    writer.putContext(
                            "defaultProtocolTrait",
                            new DefaultProtocolTraitGenerator(
                                    writer,
                                    directive.settings().service(),
                                    defaultProtocolTrait,
                                    directive.context()));
                    writer.putContext("protocolFactory", new ProtocolFactoryGenerator(writer, defaultProtocolTrait));

                    // DEFAULT TRANSPORT
                    writer.putContext("transport", getTransportClass(directive.settings()));

                    // DEFAULT PLUGINS
                    var defaultPlugins = resolveDefaultPlugins(directive.settings());
                    writer.putContext("hasDefaultPlugins", !defaultPlugins.isEmpty());
                    writer.putContext("plugins", new DefaultPluginGenerator(writer, defaultPlugins));

                    // DEFAULT AUTH
                    var defaultAuthMap = getAuthFactoryMapping(directive.model(), directive.service());
                    writer.putContext(
                            "defaultAuthTraits",
                            new AuthTraitInitializerGenerator(writer, directive.context(), defaultAuthMap.keySet()));
                    writer.putContext("hasDefaultSchemes", !defaultAuthMap.isEmpty());
                    writer.putContext("authFactories", new AuthFactorGenerator(writer, defaultAuthMap));

                    writer.write(template);
                    writer.popState();
                });
    }

    private static Map<String, Symbol> getOperationMap(
            Set<OperationShape> operations,
            SymbolProvider symbolProvider,
            ServiceShape serviceShape
    ) {
        var result = new LinkedHashMap<String, Symbol>();
        for (var operation : operations) {
            result.put(
                    CliUtils.kebabCase(serviceShape.getContextualName(operation)),
                    symbolProvider.toSymbol(operation));
        }
        return result;
    }

    private record ProtocolFactoryGenerator(JavaWriter writer, Trait defaultProtocolTrait) implements Runnable {
        @Override
        public void run() {
            writer.pushState();
            var factoryClass = getFactory(defaultProtocolTrait.toShapeId());
            if (factoryClass.isMemberClass()) {
                writer.putContext("outer", factoryClass.getEnclosingClass());
            }
            writer.putContext("name", factoryClass.getSimpleName());
            writer.putContext("type", factoryClass);
            writer.write("new ${?outer}${outer:T}.${name:L}${/outer}${^outer}${type:T}${/outer}()");
            writer.popState();
        }
    }

    private record DefaultProtocolTraitGenerator(
            JavaWriter writer,
            ShapeId service,
            Trait defaultProtocolTrait,
            CodeGenerationContext context) implements
            Runnable {
        @Override
        public void run() {
            if (defaultProtocolTrait == null) {
                return;
            }
            writer.pushState();
            var template = """
                    private static final ${protocolSettings:T} settings = ${protocolSettings:T}.builder()
                            .service(${shapeId:T}.from(${service:S}))
                            .build();
                    private static final ${trait:T} protocolTrait = ${initializer:C};
                    """;
            writer.putContext("protocolSettings", ProtocolSettings.class);
            writer.putContext("trait", defaultProtocolTrait.getClass());
            var initializer = context.getInitializer(defaultProtocolTrait);
            writer.putContext("initializer", writer.consumer(w -> initializer.accept(w, defaultProtocolTrait)));
            writer.putContext("shapeId", ShapeId.class);
            writer.putContext("service", service);
            writer.write(template);
            writer.popState();
        }
    }

    private static Trait getDefaultProtocolTrait(Model model, JavaCodegenSettings settings) {
        var defaultProtocol = settings.defaultProtocol();
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
                        + settings.service() + ". Expected one of: " + protocols.keySet() + ".");
    }

    @SuppressWarnings("rawtypes")
    private static Class<? extends ClientProtocolFactory> getFactory(ShapeId defaultProtocol) {
        for (var factory : ServiceLoader.load(
                ClientProtocolFactory.class,
                ServiceCommandGenerator.class.getClassLoader())) {
            if (factory.id().equals(defaultProtocol)) {
                return factory.getClass();
            }
        }
        throw new CodegenException("Could not find factory for " + defaultProtocol);
    }

    @SuppressWarnings("rawtypes")
    private static Class<? extends ClientTransport> getTransportClass(JavaCodegenSettings settings) {
        if (settings.transport() == null) {
            return null;
        }
        // TODO: Actually instantiate with factory

        throw new UnsupportedOperationException("Custom default transports not yet supported");
    }

    @SuppressWarnings("rawtypes")
    private static Map<Trait, Class<? extends AuthSchemeFactory>> getAuthFactoryMapping(
            Model model,
            ToShapeId service
    ) {
        var index = ServiceIndex.of(model);
        var schemes = index.getAuthSchemes(service);
        Map<Trait, Class<? extends AuthSchemeFactory>> result = new HashMap<>();
        for (var schemeEntry : schemes.entrySet()) {
            var schemeFactoryClass = authSchemeFactories.get(schemeEntry.getKey());
            if (schemeFactoryClass != null) {
                var existing = result.put(schemeEntry.getValue(), schemeFactoryClass);
                if (existing != null) {
                    throw new CodegenException(
                            "Multiple auth scheme factory implementations found for scheme: " + schemeEntry.getKey()
                                    + "Found: " + schemeFactoryClass + " and " + existing);
                }
            } else {
                LOGGER.warn("Could not find implementation for auth scheme {}", schemeEntry.getKey());
            }
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    private record AuthTraitInitializerGenerator(
            JavaWriter writer,
            CodeGenerationContext context,
            Set<Trait> authTraits) implements Runnable {
        @Override
        public void run() {
            writer.pushState();
            for (var trait : authTraits) {
                writer.putContext("trait", trait.getClass());
                writer.putContext("traitName", getAuthTraitPropertyName(trait));
                var initializer = context.getInitializer(trait);
                // Traits using the default initializer need to be cast to the correct Trait output class.
                writer.putContext("cast", initializer.getClass().equals(GenericTraitInitializer.class));
                writer.putContext("initializer", writer.consumer(w -> initializer.accept(w, trait)));
                writer.write(
                        "private static final ${trait:T} ${traitName:L} = ${?cast}(${trait:T}) ${/cast}${initializer:C};");
            }
            writer.popState();
        }
    }

    @SuppressWarnings("rawtypes")
    private record AuthFactorGenerator(
            JavaWriter writer,
            Map<Trait, Class<? extends AuthSchemeFactory>> authMap) implements Runnable {

        @Override
        public void run() {
            writer.pushState();
            var iter = authMap.entrySet().iterator();
            while (iter.hasNext()) {
                var entry = iter.next();
                writer.putContext("authFactory", AuthSchemeFactory.class);
                writer.putContext("authFactoryImpl", entry.getValue());
                if (entry.getValue().isMemberClass()) {
                    writer.putContext("outer", entry.getValue().getEnclosingClass());
                }
                writer.putContext("authFactoryImplName", entry.getValue().getSimpleName());
                writer.putContext("trait", getAuthTraitPropertyName(entry.getKey()));
                writer.writeInline(
                        "new ${?outer}${outer:T}.${authFactoryImplName:L}${/outer}${^outer}${authFactoryImpl:T}${/outer}().createAuthScheme(${trait:L})");
                if (iter.hasNext()) {
                    writer.writeInline(",\n");
                }
            }
            writer.popState();
        }
    }

    private static String getAuthTraitPropertyName(Trait trait) {
        return StringUtils.uncapitalize(trait.toShapeId().getName()) + "Scheme";
    }

    private record DefaultPluginGenerator(JavaWriter writer, List<Class<? extends ClientPlugin>> plugins)
            implements Runnable {
        @Override
        public void run() {
            writer.pushState();
            for (var plugin : plugins) {
                writer.write(".addPlugin(new $T())", plugin);
            }
            writer.popState();
        }
    }

    private static List<Class<? extends ClientPlugin>> resolveDefaultPlugins(JavaCodegenSettings settings) {
        List<Class<? extends ClientPlugin>> result = new ArrayList<>();
        for (var pluginFqn : settings.defaultPlugins()) {
            result.add(CodegenUtils.getImplementationByName(ClientPlugin.class, pluginFqn));
        }
        return result;
    }
}
