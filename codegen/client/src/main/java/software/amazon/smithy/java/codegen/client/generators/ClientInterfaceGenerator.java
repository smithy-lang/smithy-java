/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.generators;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.client.core.ClientPlugin;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.runtime.client.core.annotations.Configuration;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.OperationIndex;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.utils.SmithyInternalApi;
import software.amazon.smithy.utils.StringUtils;

@SmithyInternalApi
public final class ClientInterfaceGenerator
    implements Consumer<GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings>> {

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
                            private Builder() {}

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
                writer.putContext("clientPlugin", ClientPlugin.class);
                writer.putContext("client", Client.class);
                writer.putContext("interface", symbol);
                writer.putContext("impl", symbol.expectProperty(ClientSymbolProperties.CLIENT_IMPL));
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
                var defaultPlugins = resolveDefaultPlugins(directive.settings());
                writer.putContext("hasDefaults", !defaultPlugins.isEmpty());
                writer.putContext("defaultPlugins", new PluginPropertyWriter(writer, defaultPlugins));
                writer.putContext("pluginSetters", new DefaultPluginSetterGenerator(writer, defaultPlugins));
                writer.write(template);
                writer.popState();
            });
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
                        writer.putContext("pluginName", pluginEntry.getKey());
                        writer.putContext("name", method.getName());
                        writer.putContext("args", getParamMap(method));
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

        private static Map<String, Parameter> getParamMap(Method method) {
            Map<String, java.lang.reflect.Parameter> parameterMap = new LinkedHashMap<>();
            for (var param : method.getParameters()) {
                var paramName = param.isAnnotationPresent(
                    software.amazon.smithy.java.runtime.client.core.annotations.Parameter.class
                )
                    ? param.getAnnotation(software.amazon.smithy.java.runtime.client.core.annotations.Parameter.class)
                        .value()
                    : param.getName();
                parameterMap.put(paramName, param);
            }
            return parameterMap;
        }
    }

    private static Map<String, Class<? extends ClientPlugin>> resolveDefaultPlugins(JavaCodegenSettings settings) {
        Map<String, Class<? extends ClientPlugin>> pluginMap = new LinkedHashMap<>();
        Map<String, Integer> frequencyMap = new HashMap<>();

        for (var pluginFqn : settings.defaultPlugins()) {
            var pluginClass = getPluginClass(pluginFqn);
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

    private static Class<? extends ClientPlugin> getPluginClass(String name) {
        try {
            var instance = Class.forName(name).getDeclaredConstructor().newInstance();
            if (instance instanceof ClientPlugin cp) {
                return cp.getClass();
            } else {
                throw new CodegenException("Class " + name + " is not a `ClientPlugin`");
            }
        } catch (ClassNotFoundException exc) {
            throw new CodegenException("Could not find class " + name + ". Check your dependencies.", exc);
        } catch (NoSuchMethodException exc) {
            throw new CodegenException("Could not find no-arg constructor for " + name, exc);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new CodegenException("Could not invoke constructor for " + name, e);
        }
    }
}
