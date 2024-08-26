/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.generators;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
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
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.client.ClientSymbolProperties;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.runtime.auth.api.AuthSchemeFactory;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.client.core.ClientPlugin;
import software.amazon.smithy.java.runtime.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.runtime.client.core.ClientTransport;
import software.amazon.smithy.java.runtime.client.core.ProtocolSettings;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.runtime.client.core.annotations.Configuration;
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
                            ${defaultPlugins:C|}
                            ${?hasDefaultProtocol}${defaultProtocol:C|}
                            ${/hasDefaultProtocol}${?defaultSchemes}${defaultAuth:C|}
                            ${/defaultSchemes}private Builder() {
                                ${?hasDefaultProtocol}configBuilder().protocol(factory.createProtocol(settings, protocolTrait));${/hasDefaultProtocol}
                                ${?defaultSchemes}configBuilder().putSupportedAuthSchemes(${#defaultSchemes}${value:L}.createAuthScheme(${key:L})${^key.last}, ${/key.last}${/defaultSchemes});${/defaultSchemes}
                                ${?transport}configBuilder().transport(new ${transport:T}());${/transport}
                            }

                            ${pluginSetters:C|}

                            @Override
                            public ${interface:T} build() {
                                ${?hasDefaults}for (var plugin : defaultPlugins) {
                                    plugin.configureClient(configBuilder());
                                }
                                ${/hasDefaults}return new ${impl:T}(this);
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
                writer.putContext("clientPlugin", ClientPlugin.class);
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
                writer.putContext("pluginSetters", new DefaultPluginSetterGenerator(writer, defaultPlugins));
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

    private static Map<Trait, Class<? extends AuthSchemeFactory>> getAuthFactoryMapping(
        Model model,
        ToShapeId service
    ) {
        var index = ServiceIndex.of(model);
        var schemes = index.getAuthSchemes(service);
        Map<Trait, Class<? extends AuthSchemeFactory>> result = new HashMap<>();
        for (var schemeFactory : ServiceLoader.load(
            AuthSchemeFactory.class,
            ClientInterfaceGenerator.class.getClassLoader()
        )) {
            if (schemes.containsKey(schemeFactory.schemeId())) {
                var existing = result.put(schemes.get(schemeFactory.schemeId()), schemeFactory.getClass());
                if (existing != null) {
                    throw new CodegenException(
                        "Multiple auth scheme factory implementations found for scheme: " + schemeFactory.schemeId()
                            + "Found: " + schemeFactory + " and " + existing
                    );
                }
            } else {
                LOGGER.warn("Could not find implementation for auth scheme {}", schemeFactory);
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
                // TODO: figure out how to tell if trait initializer needs casting?
                var template = """
                    private static final ${trait:T} ${traitName:L} = (${trait:T}) ${initializer:C};
                    private static final ${authFactory:T}<${trait:T}> ${traitName:L}Factory = new ${?outer}${outer:T}.${authFactoryImplName:L}${/outer}${^outer}${authFactoryImpl:T}${/outer}();
                    """;
                var trait = entry.getKey();
                writer.putContext("trait", trait.getClass());
                writer.putContext("traitName", getAuthTraitPropertyName(trait));
                var initializer = context.getInitializer(entry.getKey());
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

    private record DefaultPluginSetterGenerator(JavaWriter writer, Map<String, Class<? extends ClientPlugin>> pluginMap)
        implements Runnable {

        @Override
        public void run() {
            for (var pluginEntry : pluginMap.entrySet()) {
                for (var method : pluginEntry.getValue().getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Configuration.class)) {
                        writer.pushState();
                        if (!method.getReturnType().equals(Void.TYPE)) {
                            throw new CodegenException("Default plugin setters cannot return a value");
                        }
                        var configurationAnnotation = method.getAnnotation(Configuration.class);
                        var methodName = configurationAnnotation.value().isEmpty()
                            ? method.getName()
                            : configurationAnnotation.value();
                        writer.putContext("pluginName", pluginEntry.getKey());
                        writer.putContext("name", methodName);
                        writer.putContext("args", getParamMap(method, methodName));
                        writer.write("""
                            public Builder ${name:L}(${#args}${value:P} ${key:L}${^key.last}, ${/key.last}${/args}) {
                                ${pluginName:L}.${name:L}(${#args}${key:L}${^key.last}, ${/key.last}${/args});
                                return this;
                            }
                            """);
                        writer.popState();
                    }
                }
            }
        }

        private static Map<String, Parameter> getParamMap(Method method, String methodName) {
            Map<String, java.lang.reflect.Parameter> parameterMap = new LinkedHashMap<>();
            var parameters = method.getParameters();
            for (int idx = 0; idx < parameters.length; idx++) {
                var param = parameters[idx];
                var paramName = methodName;
                if (param.isAnnotationPresent(
                    software.amazon.smithy.java.runtime.client.core.annotations.Parameter.class
                )) {
                    paramName = param.getAnnotation(
                        software.amazon.smithy.java.runtime.client.core.annotations.Parameter.class
                    )
                        .value();
                } else if (idx != 0) {
                    paramName += idx;
                }
                parameterMap.put(paramName, param);
            }
            return parameterMap;
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
}
