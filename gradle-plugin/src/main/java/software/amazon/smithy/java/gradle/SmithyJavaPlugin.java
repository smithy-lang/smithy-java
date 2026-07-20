/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.gradle;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;
import org.gradle.language.jvm.tasks.ProcessResources;
import software.amazon.smithy.gradle.SmithyExtension;
import software.amazon.smithy.java.gradle.tasks.MergeServiceFilesTask;

/**
 * Gradle plugin that simplifies Java code generation from Smithy models.
 *
 * <p>This plugin requires a Java plugin ({@code java}, {@code java-library}, or {@code application})
 * to be applied by the user, then applies {@code software.amazon.smithy.gradle.smithy-base} and
 * automatically:
 * <ul>
 *     <li>Parses {@code smithy-build.json} to determine codegen modes</li>
 *     <li>Adds required dependencies based on detected modes</li>
 *     <li>Wires generated source and resource directories into the main source set</li>
 *     <li>Sets up task dependencies (compileJava, processResources, sourcesJar)</li>
 *     <li>Optionally merges META-INF/services files from multiple plugin outputs</li>
 * </ul>
 *
 * <p>When {@code java-library} is applied, types/client dependencies are added to the {@code api}
 * configuration so they are transitively visible to consumers, while server dependencies use
 * {@code implementation}. When only {@code java} or {@code application} is applied, all
 * dependencies are added to {@code implementation}.
 *
 * <p>Users who need full control over dependency configurations can set
 * {@code smithyJava.autoAddDependencies = false} and manage dependencies manually.
 */
public class SmithyJavaPlugin implements Plugin<Project> {

    private static final String SMITHY_JAVA_GROUP = "software.amazon.smithy.java";
    private static final String JAVA_CODEGEN_PLUGIN_NAME = "java-codegen";
    private static final String SMITHY_BUILD_TASK_NAME = "smithyBuild";
    private static final String CLEAN_SMITHY_OUTPUT_TASK_NAME = "cleanSmithyOutput";
    private static final String MERGE_SERVICE_FILES_TASK_NAME = "mergeSmithyServiceFiles";

    @Override
    public void apply(Project project) {
        project.getPlugins().apply("software.amazon.smithy.gradle.smithy-base");

        SmithyJavaExtension ext = project.getExtensions()
                .create("smithyJava", SmithyJavaExtension.class);

        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            SmithyExtension smithyExt = project.getExtensions()
                    .getByType(SmithyExtension.class);

            configureDependencies(project, smithyExt, ext);
            wireGeneratedSources(project, smithyExt, ext);
            configureCleanOutput(project, smithyExt);
            configureTaskDependencies(project);
            configureServiceFileMerging(project, smithyExt, ext);
        });

        project.afterEvaluate(p -> {
            if (!p.getPlugins().hasPlugin(JavaPlugin.class)) {
                throw new GradleException(
                        "The smithy-java plugin requires a Java plugin (java, java-library, or application) to be applied.");
            }
        });
    }

    private void configureDependencies(
            Project project,
            SmithyExtension smithyExt,
            SmithyJavaExtension ext
    ) {
        Configuration smithyBuild = project.getConfigurations().getByName("smithyBuild");

        // Resolve modes via ValueSource for configuration cache compatibility.
        // If explicit modes are set in the DSL, use those directly; otherwise
        // read smithy-build.json through a tracked ValueSource.
        Provider<Set<File>> configFiles = smithyExt.getSmithyBuildConfigs()
                .map(FileCollection::getFiles);
        Provider<Set<String>> inferredModes = project.getProviders().of(
                SmithyBuildModesValueSource.class,
                spec -> spec.getParameters().getSmithyBuildConfigs().set(configFiles));
        Provider<Set<String>> modes = ext.getModes().map(declared ->
                declared.isEmpty() ? inferredModes.get() : declared);

        smithyBuild.withDependencies(deps -> {
            if (!ext.getAutoAddDependencies().getOrElse(true)) {
                return;
            }
            addIfAbsent(deps, project.getDependencies(), "codegen-plugin", SmithyJavaVersion.VERSION);
        });

        // Wire runtime deps to implementation by default. The withDependencies callback
        // checks at resolution time whether java-library has been applied (api exists)
        // and skips deps that belong on api instead.
        Configuration implementation = project.getConfigurations().getByName("implementation");
        implementation.withDependencies(deps -> {
            if (!ext.getAutoAddDependencies().getOrElse(true)) {
                return;
            }
            boolean hasApi = project.getPlugins().hasPlugin(JavaLibraryPlugin.class);
            String version = SmithyJavaVersion.VERSION;
            Set<String> resolved = modes.get();
            if (!hasApi) {
                addIfAbsent(deps, project.getDependencies(), "core", version);
                addIfAbsent(deps, project.getDependencies(), "framework-errors", version);
                if (resolved.contains("client")) {
                    addIfAbsent(deps, project.getDependencies(), "client-core", version);
                }
            }
            if (resolved.contains("server")) {
                addIfAbsent(deps, project.getDependencies(), "server-api", version);
            }
        });

        // When java-library is applied, types/client deps go to api.
        // Use withType to react regardless of plugin application order.
        project.getPlugins().withType(JavaLibraryPlugin.class, plugin -> {
            Configuration api = project.getConfigurations().getByName("api");
            api.withDependencies(deps -> {
                if (!ext.getAutoAddDependencies().getOrElse(true)) {
                    return;
                }
                String version = SmithyJavaVersion.VERSION;
                Set<String> resolved = modes.get();
                addIfAbsent(deps, project.getDependencies(), "core", version);
                addIfAbsent(deps, project.getDependencies(), "framework-errors", version);
                if (resolved.contains("client")) {
                    addIfAbsent(deps, project.getDependencies(), "client-core", version);
                }
            });
        });
    }

    private void wireGeneratedSources(
            Project project,
            SmithyExtension smithyExt,
            SmithyJavaExtension ext
    ) {
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        sourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME, sourceSet -> {
            Provider<String> projection = smithyExt.getSourceProjection();

            Provider<Path> codegenPath = projection.flatMap(
                    p -> smithyExt.getPluginProjectionPath(p, JAVA_CODEGEN_PLUGIN_NAME));
            sourceSet.getJava().srcDir(codegenPath.map(p -> p.resolve("java").toFile()));
            sourceSet.getResources().srcDir(codegenPath.map(p -> p.resolve("resources").toFile()));

            // Callable defers evaluation until the source set is resolved, so generatedPluginOutputs is finalized
            sourceSet.getJava().srcDir(project.files((Callable<Object>) () ->
                    ext.getGeneratedPluginOutputs().get().stream()
                            .map(name -> smithyExt.getPluginProjectionPath(
                                    projection.get(), name).get().toFile())
                            .collect(Collectors.toList())));

            sourceSet.getResources().srcDir(project.files((Callable<Object>) () ->
                    ext.getGeneratedPluginOutputs().get().stream()
                            .map(name -> smithyExt.getPluginProjectionPath(
                                    projection.get(), name).get().toFile())
                            .collect(Collectors.toList())));

            // trait-codegen mixes .java and resource files in the same output directory
            sourceSet.getResources().exclude("**/*.java");
        });
    }

    private void configureCleanOutput(Project project, SmithyExtension smithyExt) {
        project.getTasks().register(CLEAN_SMITHY_OUTPUT_TASK_NAME, Delete.class, task -> {
            task.setGroup("smithy");
            task.setDescription("Cleans the Smithy output directory before code generation");
            task.delete(smithyExt.getOutputDirectory());
        });
        project.getTasks().named(SMITHY_BUILD_TASK_NAME, task ->
                task.dependsOn(CLEAN_SMITHY_OUTPUT_TASK_NAME));
    }

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

    private void configureServiceFileMerging(
            Project project,
            SmithyExtension smithyExt,
            SmithyJavaExtension ext
    ) {
        Provider<String> projection = smithyExt.getSourceProjection();

        Provider<Path> codegenPath = projection.flatMap(
                p -> smithyExt.getPluginProjectionPath(p, JAVA_CODEGEN_PLUGIN_NAME));
        Provider<File> codegenServicesDir = codegenPath.map(
                p -> p.resolve("resources/META-INF/services").toFile());

        TaskProvider<MergeServiceFilesTask> mergeTask = project.getTasks()
                .register(MERGE_SERVICE_FILES_TASK_NAME, MergeServiceFilesTask.class, task -> {
                    task.dependsOn(SMITHY_BUILD_TASK_NAME);
                    task.setGroup("smithy");
                    task.getServiceDirectories().from(codegenServicesDir);

                    task.getServiceDirectories().from(project.files((Callable<Object>) () ->
                            ext.getGeneratedPluginOutputs().get().stream()
                                    .map(name -> smithyExt.getPluginProjectionPath(
                                                    projection.get(), name)
                                            .get()
                                            .resolve("META-INF/services")
                                            .toFile())
                                    .collect(Collectors.toList())));

                    task.onlyIf(t -> !ext.getGeneratedPluginOutputs()
                            .getOrElse(List.of()).isEmpty()
                            && ext.getMergeServiceFiles().getOrElse(true));
                });

        Provider<File> mergedServicesDir = project.getLayout().getBuildDirectory()
                .dir("merged-services").map(d -> d.getAsFile());
        String mergedPathSegment = "merged-services" + File.separator;

        project.getTasks().named("processResources", ProcessResources.class, task -> {
            task.dependsOn(mergeTask);
            task.from(project.files((Callable<Object>) () -> {
                if (isMergingActive(ext)) {
                    return mergedServicesDir.get();
                }
                return List.of();
            }), spec -> spec.into("."));
            // Files added via from() above bypass eachFile, so only originals are excluded
            task.eachFile(details -> {
                if (isMergingActive(ext)
                        && details.getRelativePath().getPathString().startsWith("META-INF/services/")
                        && !details.getFile().getPath().contains(mergedPathSegment)) {
                    details.exclude();
                }
            });
        });

        project.getTasks().withType(Jar.class).configureEach(jar -> {
            if ("sourcesJar".equals(jar.getName())) {
                jar.dependsOn(mergeTask);
                jar.from(project.files((Callable<Object>) () -> {
                    if (isMergingActive(ext)) {
                        return mergedServicesDir.get();
                    }
                    return List.of();
                }), spec -> spec.into("."));
                jar.eachFile(details -> {
                    if (isMergingActive(ext)
                            && details.getRelativePath().getPathString().startsWith("META-INF/services/")
                            && !details.getFile().getPath().contains(mergedPathSegment)) {
                        details.exclude();
                    }
                });
            }
        });
    }

    private static boolean isMergingActive(SmithyJavaExtension ext) {
        return !ext.getGeneratedPluginOutputs().getOrElse(List.of()).isEmpty()
                && ext.getMergeServiceFiles().getOrElse(true);
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
