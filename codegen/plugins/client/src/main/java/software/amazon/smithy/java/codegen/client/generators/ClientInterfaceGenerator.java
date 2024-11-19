/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.generators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.java.client.core.Client;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.client.core.ClientSetting;
import software.amazon.smithy.java.client.core.ClientTransportFactory;
import software.amazon.smithy.java.client.core.ProtocolSettings;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeFactory;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.client.ClientSymbolProperties;
import software.amazon.smithy.java.codegen.integrations.core.GenericTraitInitializer;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.sections.OperationSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.OperationIndex;
import software.amazon.smithy.model.knowledge.ServiceIndex;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.BooleanNode;
import software.amazon.smithy.model.node.NodeVisitor;
import software.amazon.smithy.model.node.NullNode;
import software.amazon.smithy.model.node.NumberNode;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ToShapeId;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.utils.SmithyInternalApi;
import software.amazon.smithy.utils.StringUtils;

@SmithyInternalApi
@SuppressWarnings("rawtypes")
public final class ClientInterfaceGenerator
    implements Consumer<GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings>> {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(ClientInterfaceGenerator.class);

    private static final Map<ShapeId, Class<? extends AuthSchemeFactory>> authSchemeFactories = new HashMap<>();
    private static final Map<String, Class<? extends ClientTransportFactory>> clientTransportFactories = new HashMap<>();

    static {
        // Add all trait services to a map, so they can be queried for a provider class
        ServiceLoader.load(AuthSchemeFactory.class, ClientInterfaceGenerator.class.getClassLoader())
            .forEach((service) -> authSchemeFactories.put(service.schemeId(), service.getClass()));
        // Add all transport services to a map, so they can be queried for a provider class
        ServiceLoader.load(ClientTransportFactory.class, ClientInterfaceGenerator.class.getClassLoader())
            .forEach((service) -> clientTransportFactories.put(service.name(), service.getClass()));
    }

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

                        /**
                         * @return Configuration in use by client.
                         */
                         ${clientConfig:T} config();

                        /**
                         * Create a Builder for {@link ${interface:T}}.
                         */
                        static Builder builder() {
                            return new Builder();
                        }

                        /**
                         * Create a {@link ${requestOverride:T}} builder for this client.
                         */
                        static RequestOverrideBuilder requestOverrideBuilder() {
                            return new RequestOverrideBuilder();
                        }

                        final class Builder extends ${client:T}.Builder<${interface:T}, Builder>${?settings}
                            implements ${#settings}${value:T}<Builder>${^key.last}, ${/key.last}${/settings}${/settings} {
                            ${?hasDefaults}${defaultPlugins:C|}
                            ${/hasDefaults}${?hasDefaultProtocol}${defaultProtocol:C|}
                            ${/hasDefaultProtocol}${?hasTransportSettings}${transportSettings:C|}
                            ${/hasTransportSettings}${?defaultSchemes}${defaultAuth:C|}
                            ${/defaultSchemes}
                            private Builder() {${?defaultSchemes}
                                configBuilder().putSupportedAuthSchemes(${#defaultSchemes}${value:L}.createAuthScheme(${key:L})${^key.last}, ${/key.last}${/defaultSchemes});
                            ${/defaultSchemes}}

                            @Override
                            public ${interface:T} build() {
                                ${?hasDefaults}for (var plugin : defaultPlugins) {
                                    plugin.configureClient(configBuilder());
                                }
                                ${/hasDefaults}${?hasDefaultProtocol}if (configBuilder().protocol() == null) {
                                    configBuilder().protocol(${protocolFactory:C}.createProtocol(protocolSettings, protocolTrait));
                                }
                                ${/hasDefaultProtocol}${?hasDefaultTransport}if (configBuilder().transport() == null) {
                                    configBuilder().transport(${transportFactory:C}.createTransport(${?hasTransportSettings}transportSettings${/hasTransportSettings}));
                                }
                                ${/hasDefaultTransport}
                                return new ${impl:T}(this);
                            }
                        }

                        final class RequestOverrideBuilder extends ${requestOverride:T}.OverrideBuilder<RequestOverrideBuilder>${?settings}
                            implements ${#settings}${value:T}<RequestOverrideBuilder>${^key.last}, ${/key.last}${/settings}${/settings} {}
                    }
                    """;
                var settings = directive.settings();
                var defaultProtocolTrait = getDefaultProtocolTrait(directive.model(), settings);
                writer.putContext("hasDefaultProtocol", defaultProtocolTrait != null);
                writer.putContext("protocolFactory", new FactoryGenerator(writer, getFactory(defaultProtocolTrait)));
                writer.putContext(
                    "defaultProtocol",
                    new DefaultProtocolGenerator(
                        writer,
                        settings.service(),
                        defaultProtocolTrait,
                        directive.context()
                    )
                );
                writer.putContext("clientPlugin", ClientPlugin.class);
                writer.putContext("client", Client.class);
                writer.putContext("requestOverride", RequestOverrideConfig.class);
                writer.putContext("clientConfig", ClientConfig.class);
                writer.putContext("interface", symbol);
                writer.putContext("impl", symbol.expectProperty(ClientSymbolProperties.CLIENT_IMPL));
                writer.putContext("hasDefaultTransport", settings.transport() != null);
                var hasTransportSettings = settings.transportSettings() != null && !settings.transportSettings()
                    .isEmpty();
                writer.putContext("hasTransportSettings", hasTransportSettings);
                writer.putContext(
                    "transportFactory",
                    new FactoryGenerator(writer, getTransportFactory(directive.settings()))
                );
                writer.putContext(
                    "transportSettings",
                    new TransportSettingsGenerator(writer, settings.transportSettings())
                );
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
                var defaultAuth = getAuthFactoryMapping(directive.model(), directive.service());
                writer.putContext(
                    "defaultAuth",
                    new AuthInitializerGenerator(writer, directive.context(), defaultAuth)
                );
                var schemes = getAuthSchemes(defaultAuth.keySet());
                writer.putContext("defaultSchemes", schemes);
                var defaultPlugins = resolveDefaultPlugins(directive.settings());
                writer.putContext("hasDefaults", !defaultPlugins.isEmpty());
                writer.putContext("defaultPlugins", new PluginPropertyWriter(writer, defaultPlugins));
                writer.putContext("settings", getBuilderSettings(directive.settings()));
                writer.write(template);
                writer.popState();
            });
    }

    private static Class<? extends ClientTransportFactory> getTransportFactory(JavaCodegenSettings settings) {
        if (settings.transport() == null) {
            return null;
        }
        var factory = clientTransportFactories.get(settings.transport());
        if (factory == null) {
            throw new CodegenException(
                "Transport " + settings.transport() + " request, but no matching factory was found."
            );
        }
        return factory;
    }

    private record TransportSettingsGenerator(JavaWriter writer, ObjectNode settings) implements Runnable {
        @Override
        public void run() {
            writer.pushState();
            writer.putContext("document", Document.class);
            writer.putContext("nodeWriter", new NodeDocumentWriter(writer, settings));
            writer.write("private static final ${document:T} transportSettings = ${nodeWriter:C};");
            writer.popState();
        }
    }

    private record NodeDocumentWriter(JavaWriter writer, ObjectNode node) implements NodeVisitor<Void>, Runnable {

        @Override
        public void run() {
            node.accept(this);
        }

        @Override
        public Void arrayNode(ArrayNode arrayNode) {
            var consumers = arrayNode.getElements().stream().map(n -> (Runnable) () -> n.accept(this)).toList();
            writer.pushState();
            writer.putContext("nodes", consumers);
            writer.putContext("list", List.class);
            writer.write(
                "${document:T}.createList(${list:T}.of(${#nodes}${value:C}${^key.last}, ${/key.last}${/nodes}))"
            );
            writer.popState();
            return null;
        }

        @Override
        public Void objectNode(ObjectNode objectNode) {
            writer.pushState();
            writer.putContext("map", Map.class);
            writer.openBlock("${document:T}.createStringMap(${map:T}.of(", "))", () -> {
                var iter = objectNode.getMembers().entrySet().iterator();
                while (iter.hasNext()) {
                    var entry = iter.next();
                    writer.writeInline(
                        "$S, $C",
                        entry.getKey().getValue(),
                        (Runnable) () -> entry.getValue().accept(this)
                    );
                    if (iter.hasNext()) {
                        writer.writeInline(",");
                    }
                    writer.newLine();
                }
            });
            writer.popState();
            return null;
        }

        @Override
        public Void booleanNode(BooleanNode booleanNode) {
            writer.writeInline("${document:T}.createBoolean($L)", booleanNode.getValue());
            return null;
        }

        @Override
        public Void numberNode(NumberNode numberNode) {
            writer.writeInline("${document:T}.createNumber($L)", numberNode.getValue());
            return null;
        }

        @Override
        public Void stringNode(StringNode stringNode) {
            writer.writeInline("${document:T}.createString($S)", stringNode.getValue());
            return null;
        }

        @Override
        public Void nullNode(NullNode nullNode) {
            throw new IllegalArgumentException("Null nodes not supported in transport settings.");
        }
    }

    private record OperationMethodGenerator(
        JavaWriter writer, ServiceShape service, SymbolProvider symbolProvider, Symbol symbol, Model model
    ) implements Runnable {

        @Override
        public void run() {
            var templateDefault = """
                default ${?async}${future:T}<${/async}${output:T}${?async}>${/async} ${name:L}(${input:T} input) {
                    return ${name:L}(input, null);
                }
                """;
            var templateBase = """
                ${?async}${future:T}<${/async}${output:T}${?async}>${/async} ${name:L}(${input:T} input, ${overrideConfig:T} overrideConfig);
                """;
            writer.pushState();
            writer.putContext("async", symbol.expectProperty(ClientSymbolProperties.ASYNC));
            writer.putContext("overrideConfig", RequestOverrideConfig.class);
            writer.putContext("future", CompletableFuture.class);

            var opIndex = OperationIndex.of(model);
            for (var operation : TopDownIndex.of(model).getContainedOperations(service)) {
                writer.pushState();
                writer.putContext("name", StringUtils.uncapitalize(CodegenUtils.getDefaultName(operation, service)));
                writer.putContext("input", symbolProvider.toSymbol(opIndex.expectInputShape(operation)));
                writer.putContext("output", symbolProvider.toSymbol(opIndex.expectOutputShape(operation)));
                writer.pushState(new OperationSection(operation, symbolProvider, model));
                writer.write(templateDefault);
                writer.popState();
                writer.newLine();
                writer.pushState(new OperationSection(operation, symbolProvider, model));
                writer.write(templateBase);
                writer.popState();
                writer.popState();
            }
            writer.popState();
        }
    }

    private record FactoryGenerator(JavaWriter writer, Class<?> factoryClass) implements Runnable {
        @Override
        public void run() {
            writer.pushState();
            if (factoryClass.isMemberClass()) {
                writer.putContext("outer", factoryClass.getEnclosingClass());
            }
            writer.putContext("name", factoryClass.getSimpleName());
            writer.putContext("type", factoryClass);
            writer.write("new ${?outer}${outer:T}.${name:L}${/outer}${^outer}${type:T}${/outer}()");
            writer.popState();
        }
    }

    private record DefaultProtocolGenerator(
        JavaWriter writer, ShapeId service, Trait defaultProtocolTrait, CodeGenerationContext context
    ) implements
        Runnable {
        @Override
        public void run() {
            if (defaultProtocolTrait == null) {
                return;
            }
            writer.pushState();
            var template = """
                private static final ${protocolSettings:T} protocolSettings = ${protocolSettings:T}.builder()
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
                + settings.service() + ". Expected one of: " + protocols.keySet() + "."
        );
    }

    private static Class<? extends ClientProtocolFactory> getFactory(Trait defaultProtocolTrait) {
        if (defaultProtocolTrait == null) {
            return null;
        }
        for (var factory : ServiceLoader.load(
            ClientProtocolFactory.class,
            ClientInterfaceGenerator.class.getClassLoader()
        )) {
            if (factory.id().equals(defaultProtocolTrait.toShapeId())) {
                return factory.getClass();
            }
        }
        throw new CodegenException("Could not find factory for " + defaultProtocolTrait);
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
                            + "Found: " + schemeFactoryClass + " and " + existing
                    );
                }
            } else {
                LOGGER.warn("Could not find implementation for auth scheme {}", schemeEntry.getKey());
            }
        }
        return result;
    }

    private record AuthInitializerGenerator(
        JavaWriter writer,
        CodeGenerationContext context,
        Map<Trait, Class<? extends AuthSchemeFactory>> authMapping
    ) implements Runnable {

        @Override
        public void run() {
            writer.pushState();
            for (var entry : authMapping.entrySet()) {
                var template = """
                    private static final ${trait:T} ${traitName:L} = ${?cast}(${trait:T}) ${/cast}${initializer:C};
                    private static final ${authFactory:T}<${trait:T}> ${traitName:L}Factory = new ${?outer}${outer:T}.${authFactoryImplName:L}${/outer}${^outer}${authFactoryImpl:T}${/outer}();
                    """;
                var trait = entry.getKey();
                writer.putContext("trait", trait.getClass());
                writer.putContext("traitName", getAuthTraitPropertyName(trait));
                var initializer = context.getInitializer(entry.getKey());
                // Traits using the default initializer need to be cast to the correct Trait output class.
                writer.putContext("cast", initializer.getClass().equals(GenericTraitInitializer.class));
                writer.putContext("initializer", writer.consumer(w -> initializer.accept(w, entry.getKey())));
                writer.putContext("authFactory", AuthSchemeFactory.class);
                writer.putContext("authFactoryImpl", entry.getValue());
                if (entry.getValue().isMemberClass()) {
                    writer.putContext("outer", entry.getValue().getEnclosingClass());
                }
                writer.putContext("authFactoryImplName", entry.getValue().getSimpleName());
                writer.write(template);
            }
            writer.popState();
        }
    }

    private static Map<String, String> getAuthSchemes(Collection<Trait> traits) {
        Map<String, String> authSchemes = new HashMap<>();
        for (var trait : traits) {
            var traitName = getAuthTraitPropertyName(trait);
            authSchemes.put(traitName, traitName + "Factory");
        }
        return authSchemes;
    }

    private static String getAuthTraitPropertyName(Trait trait) {
        return StringUtils.uncapitalize(trait.toShapeId().getName()) + "Scheme";
    }

    private record PluginPropertyWriter(JavaWriter writer, Map<String, Class<? extends ClientPlugin>> pluginMap)
        implements Runnable {
        @Override
        public void run() {
            if (pluginMap.isEmpty()) {
                return;
            }
            writer.pushState();
            writer.putContext("list", List.class);
            writer.putContext("plugins", pluginMap);
            writer.write(
                """
                    ${#plugins}private final ${value:T} ${key:L} = new ${value:T}();
                    ${/plugins}
                    private final ${list:T}<${clientPlugin:T}> defaultPlugins = List.of(${#plugins}${key:L}${^key.last}, ${/key.last}${/plugins});
                    """
            );
            writer.popState();
        }

    }

    private static Map<String, Class<? extends ClientPlugin>> resolveDefaultPlugins(JavaCodegenSettings settings) {
        Map<String, Class<? extends ClientPlugin>> pluginMap = new LinkedHashMap<>();
        Map<String, Integer> frequencyMap = new HashMap<>();

        for (var pluginFqn : settings.defaultPlugins()) {
            var pluginClass = CodegenUtils.getImplementationByName(ClientPlugin.class, pluginFqn);
            // Ensure plugin names used as properties never clash
            var pluginName = StringUtils.uncapitalize(pluginClass.getSimpleName());
            int val = frequencyMap.getOrDefault(pluginName, 0);
            if (val != 0) {
                pluginName += val;
            }
            frequencyMap.put(pluginName, val + 1);
            pluginMap.put(pluginName, pluginClass);
        }

        return pluginMap;
    }

    private static List<Class<?>> getBuilderSettings(JavaCodegenSettings settings) {
        var result = new ArrayList<Class<?>>();
        for (var settingName : settings.defaultSettings()) {
            var clazz = CodegenUtils.getClassForName(settingName);
            if (clazz.isAssignableFrom(ClientSetting.class)) {
                throw new CodegenException(
                    "Settings must extend from `ClientSetting` interface. Could not"
                        + " cast class `" + settingName + "` to `ClientSetting"
                );
            }
            result.add(clazz);
        }
        return result;
    }
}
