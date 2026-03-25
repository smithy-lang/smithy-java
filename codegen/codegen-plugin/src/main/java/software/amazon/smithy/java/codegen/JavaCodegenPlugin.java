/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.framework.knowledge.ImplicitErrorIndex;
import software.amazon.smithy.framework.transform.AddFrameworkErrorsTransform;
import software.amazon.smithy.java.codegen.generators.SyntheticTrait;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.Prelude;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.selector.Selector;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.MixinTrait;
import software.amazon.smithy.model.traits.TraitDefinition;
import software.amazon.smithy.model.transform.ModelTransformer;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Unified plugin for Java code generation. Supports modes: client, server, types.
 *
 * <p>Configure via {@code smithy-build.json}:
 * <pre>{@code
 * {
 *   "plugins": {
 *     "java-codegen": {
 *       "service": "com.example#MyService",
 *       "namespace": "com.example",
 *       "modes": ["client", "server"]
 *     }
 *   }
 * }
 * }</pre>
 */
@SmithyInternalApi
public final class JavaCodegenPlugin implements SmithyBuildPlugin {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(JavaCodegenPlugin.class);

    @Override
    public String getName() {
        return "java-codegen";
    }

    @Override
    public void execute(PluginContext context) {
        var settingsNode = context.getSettings();
        var modes = parseModes(settingsNode);
        LOGGER.info("Running java-codegen with modes: {}", modes);

        if (modes.contains(CodegenMode.TYPES)
                && !modes.contains(CodegenMode.CLIENT)
                && !modes.contains(CodegenMode.SERVER)) {
            executeTypesMode(context, settingsNode, modes);
        } else {
            executeServiceMode(context, settingsNode, modes);
        }
    }

    private void executeServiceMode(PluginContext context, ObjectNode settingsNode, Set<CodegenMode> modes) {
        CodegenDirector<JavaWriter, JavaCodegenIntegration, CodeGenerationContext, JavaCodegenSettings> runner =
                new CodegenDirector<>();

        var settings = JavaCodegenSettings.fromNode(settingsNode);
        runner.settings(settings);
        runner.directedCodegen(new DirectedJavaCodegen(modes));
        runner.fileManifest(context.getFileManifest());
        runner.service(settings.service());
        // TODO: use built-in once this has been upstreamed
        var model = AddFrameworkErrorsTransform.transform(ModelTransformer.create(), context.getModel());
        if (modes.contains(CodegenMode.TYPES)) {
            model = expandServiceClosureForTypes(model, settings.service());
        }
        validateDependencies(modes, model, settings);
        runner.model(model);
        runner.integrationClass(JavaCodegenIntegration.class);
        DefaultTransforms.apply(runner, settings);
        runner.run();
        LOGGER.info("Smithy-Java code generation complete (modes: {})", modes);
    }

    private void executeTypesMode(PluginContext context, ObjectNode settingsNode, Set<CodegenMode> modes) {
        LOGGER.info("Generating Java types from smithy model.");
        CodegenDirector<JavaWriter, JavaCodegenIntegration, CodeGenerationContext, JavaCodegenSettings> runner =
                new CodegenDirector<>();

        var settings = TypeCodegenSettings.fromNode(settingsNode);
        var codegenSettings = settings.codegenSettings();
        runner.settings(codegenSettings);
        runner.directedCodegen(new DirectedJavaCodegen(modes));
        runner.fileManifest(context.getFileManifest());
        runner.service(codegenSettings.service());

        // Compute closure and create synthetic service
        var closure = getClosure(context.getModel(), settings);
        LOGGER.info("Found {} shapes in generation closure", closure.size());
        var model = SyntheticServiceTransform.transform(context.getModel(), closure, settings.renames());
        runner.model(model);
        runner.integrationClass(JavaCodegenIntegration.class);
        DefaultTransforms.apply(runner, codegenSettings);
        runner.run();
        LOGGER.info("Successfully generated Java class files.");
    }

    /**
     * Expands the service closure to include all model types not already reachable from the service.
     * This is used when TYPES mode is combined with CLIENT or SERVER mode, so that standalone types
     * outside the service closure are also generated.
     */
    private static Model expandServiceClosureForTypes(Model model, ShapeId serviceId) {
        var service = model.expectShape(serviceId, ServiceShape.class);
        var walker = new Walker(model);
        var serviceClosure = walker.walkShapes(service);
        var implicitErrorIndex = ImplicitErrorIndex.of(model);

        // Default types selector: all structures, unions, enums, and intEnums
        var selector = Selector.parse(":is(structure, union, enum, intEnum)");
        var allTypes = selector.shapes(model)
                .filter(s -> !s.isMemberShape())
                .filter(s -> !Prelude.isPreludeShape(s))
                .filter(s -> !s.hasTrait(MixinTrait.class))
                .filter(s -> !s.hasTrait(TraitDefinition.class))
                .filter(s -> !s.hasTrait(SyntheticTrait.class))
                .filter(s -> !implicitErrorIndex.isImplicitError(s.getId()))
                .filter(s -> !serviceClosure.contains(s))
                .collect(Collectors.toSet());

        if (allTypes.isEmpty()) {
            return model;
        }

        LOGGER.info("Expanding service closure with {} additional type shapes for TYPES mode", allTypes.size());
        return SyntheticServiceTransform.expandServiceClosure(model, serviceId, allTypes);
    }

    private static Set<CodegenMode> parseModes(ObjectNode settingsNode) {
        var modesNode = settingsNode.getArrayMember("modes");
        if (modesNode.isEmpty()) {
            throw new CodegenException("java-codegen plugin requires a 'modes' property. "
                    + "Valid modes: client, server, types");
        }
        var modes = modesNode.get()
                .getElements()
                .stream()
                .map(n -> {
                    var value = n.expectStringNode().getValue().toUpperCase(Locale.ENGLISH);
                    try {
                        return CodegenMode.valueOf(value);
                    } catch (IllegalArgumentException e) {
                        throw new CodegenException(
                                "Invalid codegen mode: '" + n.expectStringNode().getValue()
                                        + "'. Valid modes: client, server, types");
                    }
                })
                .collect(Collectors.toCollection(() -> java.util.EnumSet.noneOf(CodegenMode.class)));
        if (modes.isEmpty()) {
            throw new CodegenException("java-codegen plugin requires at least one mode. "
                    + "Valid modes: client, server, types");
        }
        return modes;
    }

    private static void validateDependencies(Set<CodegenMode> modes, Model model, JavaCodegenSettings settings) {
        if (modes.contains(CodegenMode.CLIENT)) {
            requireDependency(
                    "software.amazon.smithy.java.client.core.Client",
                    "client-api",
                    "client");
            var service = model.expectShape(settings.service(), ServiceShape.class);
            if (service.hasTrait(ShapeId.from("smithy.rules#endpointBdd"))
                    || service.hasTrait(ShapeId.from("smithy.rules#endpointRuleSet"))) {
                requireDependency(
                        "software.amazon.smithy.java.rulesengine.RulesEngineBuilder",
                        "client-rulesengine",
                        "client (with endpoint rules)");
            }
            if (!model.getShapesWithTrait(ShapeId.from("smithy.waiters#waitable")).isEmpty()) {
                requireDependency(
                        "software.amazon.smithy.java.client.waiters.Waiter",
                        "client-waiters",
                        "client (with waiters)");
            }
        }
        if (modes.contains(CodegenMode.SERVER)) {
            requireDependency(
                    "software.amazon.smithy.java.server.Service",
                    "server-api",
                    "server");
        }
    }

    private static void requireDependency(String className, String moduleName, String modeDescription) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new CodegenException(
                    "Codegen mode '" + modeDescription + "' requires the '" + moduleName + "' dependency. "
                            + "Add 'software.amazon.smithy.java:" + moduleName + "' to your smithyBuild dependencies.");
        }
    }

    private static Set<Shape> getClosure(Model model, TypeCodegenSettings settings) {
        Set<Shape> closure = new HashSet<>();
        settings.shapes()
                .stream()
                .map(model::expectShape)
                .forEach(closure::add);
        settings.selector()
                .shapes(model)
                .filter(s -> !s.isMemberShape())
                .filter(s -> !Prelude.isPreludeShape(s))
                .forEach(closure::add);

        if (closure.isEmpty()) {
            throw new CodegenException("Could not generate types. No shapes found in closure");
        }
        LOGGER.info("Found {} shapes in generation closure.", closure.size());

        return closure;
    }
}
