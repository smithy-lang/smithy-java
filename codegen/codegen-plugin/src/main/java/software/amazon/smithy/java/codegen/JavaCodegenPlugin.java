/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.stream.Collectors;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.framework.knowledge.ImplicitErrorIndex;
import software.amazon.smithy.framework.transform.AddFrameworkErrorsTransform;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.Prelude;
import software.amazon.smithy.model.metadata.ShapeClosure;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.selector.Selector;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.MixinTrait;
import software.amazon.smithy.model.traits.TraitDefinition;
import software.amazon.smithy.model.transform.ModelTransformer;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Unified plugin for Java code generation. Supports modes: client, server, types.
 *
 * <p>The {@code modes} setting selects what is generated and how generation is driven:
 * <ul>
 *     <li><b>Service mode</b> ({@code ["client"]}, {@code ["server"]}, or both) - generation is
 *         driven by the {@code service}, which is required. Produces the client and/or server for
 *         that service and every shape in its closure.</li>
 *     <li><b>Types mode</b> ({@code ["types"]}) - generation is driven by a shape closure built
 *         from the {@code selector}/{@code shapes} settings, with no {@code service}. Produces only
 *         data shapes (structures, unions, enums, intEnums, lists, maps). A {@code name} is used
 *         here only as a label and is not required.</li>
 *     <li><b>Combined mode</b> ({@code ["types"]} alongside {@code ["client"]} and/or
 *         {@code ["server"]}) - generates the service (as in service mode) <em>plus</em> any
 *         standalone data shapes that are not reachable from the service. The {@code service} is
 *         still required and remains the primary service.</li>
 * </ul>
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

    // Id of the shape closure that drives types and combined-mode generation. Namespaced so it
    // cannot collide with a model shape id or a model-authored closure.
    private static final String CLOSURE_ID = "software.amazon.smithy.java.codegen#types";

    // The `closure` setting references a pre-authored shape closure by id; the settings below define
    // a closure inline. The two are mutually exclusive.
    private static final String CLOSURE = "closure";
    private static final List<String> INLINE_CLOSURE_SETTINGS = List.of("selector", "shapes", "renames");

    @Override
    public String getName() {
        return "java-codegen";
    }

    @Override
    public void execute(PluginContext context) {
        var settingsNode = context.getSettings();
        var modes = parseModes(settingsNode);
        LOGGER.info("Running java-codegen with modes: {}", modes);

        validateClosureSettings(settingsNode, modes);

        if (modes.contains(CodegenMode.TYPES)
                && !modes.contains(CodegenMode.CLIENT)
                && !modes.contains(CodegenMode.SERVER)) {
            executeTypesMode(context, settingsNode, modes);
        } else {
            executeServiceMode(context, settingsNode, modes);
        }
    }

    // Validates the closure-related settings uniformly across all modes (types and combined), since
    // a closure can drive either. Done against the raw settings node so the rules apply before the
    // mode-specific settings objects are built.
    private static void validateClosureSettings(ObjectNode settingsNode, Set<CodegenMode> modes) {
        boolean hasClosure = settingsNode.getMember(CLOSURE).isPresent();

        // A `closure` only drives the set of generated types, so it is meaningless without TYPES mode.
        if (hasClosure && !modes.contains(CodegenMode.TYPES)) {
            throw new CodegenException("The `closure` setting requires the `types` mode, but modes were: " + modes);
        }

        if (!hasInlineClosureSettings(settingsNode)) {
            return;
        }

        // A pre-authored closure fully defines what to generate, so the inline settings must not also
        // be set; otherwise nudge users that the inline definition could live in the model instead.
        if (hasClosure) {
            throw new CodegenException("The `closure` setting references a pre-authored shape closure and "
                    + "cannot be combined with the inline " + INLINE_CLOSURE_SETTINGS + " setting(s).");
        }
        LOGGER.warn("The {} setting(s) define a shape closure inline. Consider authoring a `shapeClosures` "
                + "entry in your model and referencing it with the `closure` setting so the closure travels "
                + "with the model.", INLINE_CLOSURE_SETTINGS);
    }

    // True if any inline closure setting (selector/shapes/renames) is present.
    private static boolean hasInlineClosureSettings(ObjectNode settingsNode) {
        for (var setting : INLINE_CLOSURE_SETTINGS) {
            if (settingsNode.getMember(setting).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private void setIntegrationsSettings(CodegenDirector runner, ObjectNode settingsNode) {
        settingsNode.asObjectNode()
                .flatMap(node -> node.getObjectMember("integrations"))
                .ifPresent(runner::integrationSettings);
    }

    private void executeServiceMode(PluginContext context, ObjectNode settingsNode, Set<CodegenMode> modes) {
        CodegenDirector<JavaWriter, JavaCodegenIntegration, CodeGenerationContext, JavaCodegenSettings> runner =
                new CodegenDirector<>();

        var settings = JavaCodegenSettings.fromNode(settingsNode);
        runner.settings(settings);
        setIntegrationsSettings(runner, settingsNode);
        runner.directedCodegen(new DirectedJavaCodegen(modes));
        runner.fileManifest(context.getFileManifest());
        runner.service(settings.service());
        // TODO: use built-in once this has been upstreamed
        var model = AddFrameworkErrorsTransform.transform(ModelTransformer.create(), context.getModel());
        if (modes.contains(CodegenMode.TYPES)) {
            // Combined mode: the service stays the primary service (so client/server generation is
            // unchanged) but the generated set also includes standalone types. Either drive from a
            // pre-authored closure referenced by id (the director enforces the service is a member),
            // or build a closure of the service plus any standalone types not reachable from it.
            var closure = settings.getClosure();
            if (closure.isPresent()) {
                runner.shapeClosure(closure.get());
            } else {
                runner.shapeClosure(combinedClosure(model, settings.service()));
            }
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
        setIntegrationsSettings(runner, settingsNode);
        runner.directedCodegen(new DirectedJavaCodegen(modes));
        runner.fileManifest(context.getFileManifest());

        // Drive generation from a pre-authored closure when one is referenced by id, otherwise build
        // the closure from the inline selector/shapes settings. Either way only data shapes are
        // generated, and the director resolves the closure's transitive data shapes.
        var closure = codegenSettings.getClosure();
        if (closure.isPresent()) {
            runner.shapeClosure(closure.get());
        } else {
            runner.shapeClosure(typesClosure(settings));
        }
        runner.generateDataShapesOnly();
        runner.model(context.getModel());
        runner.integrationClass(JavaCodegenIntegration.class);
        DefaultTransforms.apply(runner, codegenSettings);
        runner.run();
        LOGGER.info("Successfully generated Java class files.");
    }

    /**
     * Builds the generation closure for combined TYPES + CLIENT/SERVER mode: the full closure of
     * the primary service plus any standalone types not reachable from the service. The service is
     * included by id so directed traversal pulls in its operations, I/O, and connected shapes; the
     * extra standalone types are folded in by id. The service's renames carry over so naming is
     * unchanged from a plain service build.
     */
    private static ShapeClosure combinedClosure(Model model, ShapeId serviceId) {
        var service = model.expectShape(serviceId, ServiceShape.class);
        var serviceClosure = new Walker(model).walkShapes(service);
        var implicitErrorIndex = ImplicitErrorIndex.of(model);

        // Default types selector: all structures, unions, enums, and intEnums not already reachable
        // from the service. Mixins, trait definitions, and implicit (framework) errors are excluded
        // so they are not emitted as standalone POJOs.
        var standaloneTypes = new TreeSet<ShapeId>();
        for (var shape : Selector.parse(":is(structure, union, enum, intEnum)").select(model)) {
            if (shape.isMemberShape()
                    || Prelude.isPreludeShape(shape)
                    || shape.hasTrait(MixinTrait.class)
                    || shape.hasTrait(TraitDefinition.class)
                    || implicitErrorIndex.isImplicitError(shape.getId())
                    || serviceClosure.contains(shape)) {
                continue;
            }
            standaloneTypes.add(shape.getId());
        }

        LOGGER.info("Generating service closure plus {} standalone type shapes for TYPES mode",
                standaloneTypes.size());

        // Match the service (which pulls in its full directed closure) and each standalone type by id.
        var selector = new StringBuilder(":is([id='").append(serviceId).append("']");
        for (var id : standaloneTypes) {
            selector.append(", [id='").append(id).append("']");
        }
        selector.append(")");

        return ShapeClosure.builder()
                .id(CLOSURE_ID)
                .includeBySelector(selector.toString())
                .rename(service.getRename())
                .build();
    }

    /**
     * Builds the types-only generation closure from the configured selector, explicitly listed
     * shapes, and renames. Explicit shapes are folded into the selector as id-equality clauses and
     * trait definitions are excluded so only data shapes are generated.
     */
    private static ShapeClosure typesClosure(TypeCodegenSettings settings) {
        // Fold the explicit shapes into the configured selector as id-equality alternatives,
        // e.g. :is(<selector>, [id='ns#A'], [id='ns#B']).
        String base = settings.selector().toString();
        if (!settings.shapes().isEmpty()) {
            var joiner = new StringJoiner(", ", ":is(", ")");
            joiner.add(base);
            for (var shape : settings.shapes()) {
                joiner.add("[id='" + shape + "']");
            }
            base = joiner.toString();
        }
        return ShapeClosure.builder()
                .id(CLOSURE_ID)
                // Exclude trait definitions so only data shapes are generated.
                .includeBySelector(base + " :not([trait|trait])")
                .rename(settings.renames())
                .build();
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
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(CodegenMode.class)));
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
}
