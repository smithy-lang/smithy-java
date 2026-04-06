/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;
import org.gradle.language.jvm.tasks.ProcessResources;
import software.amazon.smithy.gradle.SmithyExtension;
import software.amazon.smithy.java.gradle.tasks.MergeServiceFilesTask;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;

/**
 * Gradle plugin that simplifies Java code generation from Smithy models.
 *
 * <p>This plugin applies {@code java-library} (if no Java plugin is already applied)
 * and {@code software.amazon.smithy.gradle.smithy-base}, then automatically:
 * <ul>
 *     <li>Parses {@code smithy-build.json} to determine codegen modes</li>
 *     <li>Adds required dependencies based on detected modes</li>
 *     <li>Wires generated source and resource directories into the main source set</li>
 *     <li>Sets up task dependencies (compileJava, processResources, sourcesJar)</li>
 *     <li>Optionally merges META-INF/services files from multiple plugin outputs</li>
 * </ul>
 */
public class SmithyJavaCodegenPlugin implements Plugin<Project> {

    private static final String SMITHY_JAVA_GROUP = "software.amazon.smithy.java";
    private static final String JAVA_CODEGEN_PLUGIN_NAME = "java-codegen";
    private static final String SMITHY_BUILD_TASK_NAME = "smithyBuild";
    private static final String MERGE_SERVICE_FILES_TASK_NAME = "mergeSmithyServiceFiles";

    @Override
    public void apply(Project project) {
        // 1. Apply prerequisite plugins. Only apply java-library if no Java plugin is present,
        // so users can apply application or java themselves before this plugin.
        if (!project.getPlugins().hasPlugin(JavaPlugin.class)) {
            project.getPlugins().apply(JavaLibraryPlugin.class);
        }
        project.getPlugins().apply("software.amazon.smithy.gradle.smithy-base");

        // 2. Create our extension
        SmithyJavaCodegenExtension ext = project.getExtensions()
                .create("smithyJava", SmithyJavaCodegenExtension.class);

        // 3. Get the smithy extension (created by smithy-base)
        SmithyExtension smithyExt = project.getExtensions()
                .getByType(SmithyExtension.class);

        // 4. Auto-add dependencies by parsing smithy-build.json
        configureDependencies(project, smithyExt, ext);

        // 5. Wire generated sources into main source set (all lazy, no afterEvaluate)
        wireGeneratedSources(project, smithyExt, ext);

        // 6. Set up task dependencies
        configureTaskDependencies(project);

        // 7. Set up service file merging
        configureServiceFileMerging(project, smithyExt, ext);
    }

    /**
     * Parses smithy-build.json to determine codegen modes and adds appropriate
     * dependencies to both smithyBuild and api configurations.
     */
    private void configureDependencies(
            Project project,
            SmithyExtension smithyExt,
            SmithyJavaCodegenExtension ext
    ) {
        Configuration smithyBuild = project.getConfigurations().getByName("smithyBuild");
        Configuration api = project.getConfigurations().getByName("api");

        // Add codegen-plugin and mode-specific deps to smithyBuild
        smithyBuild.withDependencies(deps -> {
            if (!ext.getAutoAddDependencies().getOrElse(true)) {
                return;
            }
            String version = SmithyJavaVersion.VERSION;
            Set<String> modes = parseModes(smithyExt);
            addIfAbsent(deps, project.getDependencies(), "codegen-plugin", version);
            if (modes.contains("client")) {
                addIfAbsent(deps, project.getDependencies(), "client-core", version);
            }
            if (modes.contains("server")) {
                addIfAbsent(deps, project.getDependencies(), "server-api", version);
            }
        });

        // Add runtime deps to api configuration
        api.withDependencies(deps -> {
            if (!ext.getAutoAddDependencies().getOrElse(true)) {
                return;
            }
            String version = SmithyJavaVersion.VERSION;
            Set<String> modes = parseModes(smithyExt);
            addIfAbsent(deps, project.getDependencies(), "core", version);
            addIfAbsent(deps, project.getDependencies(), "framework-errors", version);
            if (modes.contains("client")) {
                addIfAbsent(deps, project.getDependencies(), "client-core", version);
            }
            if (modes.contains("server")) {
                addIfAbsent(deps, project.getDependencies(), "server-api", version);
            }
        });
    }

    /**
     * Wires generated sources into the main source set. Uses lazy providers and Callables
     * so that additional plugin sources are resolved when the source set is queried, not
     * at configuration time. This avoids afterEvaluate.
     */
    private void wireGeneratedSources(
            Project project,
            SmithyExtension smithyExt,
            SmithyJavaCodegenExtension ext
    ) {
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        sourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME, sourceSet -> {
            String projection = smithyExt.getSourceProjection().getOrElse("source");

            // Wire java-codegen output (always present)
            Provider<Path> codegenPath = smithyExt.getPluginProjectionPath(projection,
                    JAVA_CODEGEN_PLUGIN_NAME);
            sourceSet.getJava().srcDir(codegenPath.map(p -> p.resolve("java").toFile()));
            sourceSet.getResources().srcDir(codegenPath.map(p -> p.resolve("resources").toFile()));

            // Wire additional plugin outputs lazily via Callable — evaluated when the
            // source set is resolved, so the additionalSmithyBuildPlugins list is finalized.
            sourceSet.getJava().srcDir(project.files((Callable<Object>) () ->
                    ext.getAdditionalSmithyBuildPlugins().get().stream()
                            .map(name -> smithyExt.getPluginProjectionPath(projection, name)
                                    .get().toFile())
                            .collect(Collectors.toList())));

            sourceSet.getResources().srcDir(project.files((Callable<Object>) () ->
                    ext.getAdditionalSmithyBuildPlugins().get().stream()
                            .map(name -> smithyExt.getPluginProjectionPath(projection, name)
                                    .get().toFile())
                            .collect(Collectors.toList())));

            // Exclude java files from resources when additional plugins are present
            // (trait-codegen mixes .java and resource files in the same directory)
            sourceSet.getResources().exclude("**/*.java");
        });
    }

    /**
     * Sets up task dependencies so that code generation runs before compilation.
     */
    private void configureTaskDependencies(Project project) {
        project.getTasks().named("compileJava", task -> task.dependsOn(SMITHY_BUILD_TASK_NAME));
        project.getTasks().named("processResources", task -> task.dependsOn(SMITHY_BUILD_TASK_NAME));

        project.getTasks().withType(Jar.class).configureEach(jar -> {
            if ("sourcesJar".equals(jar.getName())) {
                jar.mustRunAfter(project.getTasks().named("compileJava"));
                jar.dependsOn(SMITHY_BUILD_TASK_NAME);
            }
        });
    }

    /**
     * Registers the service file merge task and wires it into processResources.
     * The merge task only runs when additional smithy build plugins are configured.
     */
    private void configureServiceFileMerging(
            Project project,
            SmithyExtension smithyExt,
            SmithyJavaCodegenExtension ext
    ) {
        String projection = smithyExt.getSourceProjection().getOrElse("source");

        // java-codegen services directory
        Provider<Path> codegenPath = smithyExt.getPluginProjectionPath(projection,
                JAVA_CODEGEN_PLUGIN_NAME);
        Provider<File> codegenServicesDir = codegenPath.map(
                p -> p.resolve("resources/META-INF/services").toFile());

        TaskProvider<MergeServiceFilesTask> mergeTask = project.getTasks()
                .register(MERGE_SERVICE_FILES_TASK_NAME, MergeServiceFilesTask.class, task -> {
                    task.dependsOn(SMITHY_BUILD_TASK_NAME);
                    task.setGroup("smithy");
                    task.getServiceDirectories().from(codegenServicesDir);

                    // Additional plugin service dirs — resolved lazily via Callable
                    task.getServiceDirectories().from(project.files((Callable<Object>) () ->
                            ext.getAdditionalSmithyBuildPlugins().get().stream()
                                    .map(name -> smithyExt.getPluginProjectionPath(projection, name)
                                            .get()
                                            .resolve("META-INF/services")
                                            .toFile())
                                    .collect(Collectors.toList())));

                    // Only enable when there are additional plugins and merging is on
                    task.onlyIf(t -> !ext.getAdditionalSmithyBuildPlugins()
                            .getOrElse(List.of()).isEmpty()
                            && ext.getMergeServiceFiles().getOrElse(true));
                });

        // Wire merged output into processResources
        project.getTasks().named("processResources", ProcessResources.class, task -> {
            task.dependsOn(mergeTask);
            task.from(project.getLayout().getBuildDirectory().dir("merged-services"),
                    spec -> spec.into("."));
            task.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
        });

        // sourcesJar should also include merged services
        project.getTasks().withType(Jar.class).configureEach(jar -> {
            if ("sourcesJar".equals(jar.getName())) {
                jar.dependsOn(mergeTask);
                jar.from(project.getLayout().getBuildDirectory().dir("merged-services"));
                jar.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
            }
        });
    }

    /**
     * Parses smithy-build.json to extract the codegen modes from the java-codegen plugin
     * configuration.
     *
     * <p>Note: This reads smithy-build.json during configuration (inside a withDependencies
     * callback). A future improvement could use a Gradle ValueSource for full configuration
     * cache compatibility.
     */
    private static Set<String> parseModes(SmithyExtension smithyExt) {
        for (File config : smithyExt.getSmithyBuildConfigs().get()) {
            if (!config.exists()) {
                continue;
            }
            try {
                String content = Files.readString(config.toPath());
                ObjectNode root = Node.parseJsonWithComments(content).expectObjectNode();
                return root.getObjectMember("plugins")
                        .flatMap(plugins -> plugins.getObjectMember(JAVA_CODEGEN_PLUGIN_NAME))
                        .flatMap(codegen -> codegen.getArrayMember("modes"))
                        .map(SmithyJavaCodegenPlugin::extractModes)
                        .orElse(Set.of("types"));
            } catch (IOException e) {
                // If we can't read it, skip — smithyBuild task will report the error
                continue;
            }
        }
        return Set.of("types");
    }

    private static Set<String> extractModes(ArrayNode modesArray) {
        Set<String> modes = new HashSet<>();
        for (Node element : modesArray.getElements()) {
            element.asStringNode().map(StringNode::getValue).ifPresent(modes::add);
        }
        return modes;
    }

    private static void addIfAbsent(
            DependencySet deps,
            DependencyHandler handler,
            String artifactName,
            String version
    ) {
        boolean alreadyPresent = deps.stream()
                .anyMatch(d -> SMITHY_JAVA_GROUP.equals(d.getGroup())
                        && artifactName.equals(d.getName()));
        if (!alreadyPresent) {
            deps.add(handler.create(SMITHY_JAVA_GROUP + ":" + artifactName + ":" + version));
        }
    }
}
