/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.gradle;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemOperations;
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
 * <p>This plugin requires the user to apply a Java plugin ({@code java}, {@code java-library}, or
 * {@code application}) and then applies {@code software.amazon.smithy.gradle.smithy-base}. It
 * automatically:
 * <ul>
 *     <li>Parses {@code smithy-build.json} to determine codegen modes (client, server, types)</li>
 *     <li>Adds required runtime and codegen dependencies based on detected modes</li>
 *     <li>Wires generated source and resource directories into the main source set</li>
 *     <li>Sets up task dependencies (compileJava, processResources, sourcesJar)</li>
 *     <li>Merges META-INF/services files when multiple projections or plugin outputs are used</li>
 * </ul>
 *
 * <p>Dependency configuration selection:
 * <ul>
 *     <li>{@code java-library}: uses {@code api} unless mode is server-only</li>
 *     <li>{@code java} or {@code application}: uses {@code implementation}</li>
 * </ul>
 *
 * <p>Users who need full control over dependency configurations can set
 * {@code smithyJava.autoAddDependencies = false}.
 *
 * @see SmithyJavaExtension
 */
public abstract class SmithyJavaPlugin implements Plugin<Project> {

    private static final String SMITHY_JAVA_GROUP = "software.amazon.smithy.java";
    private static final String JAVA_CODEGEN_PLUGIN_NAME = "java-codegen";
    private static final String SMITHY_BUILD_TASK_NAME = "smithyBuild";
    private static final String CLEAN_SMITHY_OUTPUT_TASK_NAME = "cleanSmithyOutput";
    private static final String MERGE_SERVICE_FILES_TASK_NAME = "mergeSmithyServiceFiles";

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @Override
    public void apply(Project project) {
        project.getPlugins().apply("software.amazon.smithy.gradle.smithy-base");

        SmithyJavaExtension ext = project.getExtensions()
                .create("smithyJava", SmithyJavaExtension.class);

        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            SmithyExtension smithyExt = project.getExtensions()
                    .getByType(SmithyExtension.class);

            configureDependencies(project, smithyExt, ext);
            configureCleanOutput(project, smithyExt);

            project.afterEvaluate(p -> {
                wireGeneratedSources(p, smithyExt, ext);
                configureTaskDependencies(p);
                configureServiceFileMerging(p, smithyExt, ext);
            });
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
        Provider<Set<File>> configFiles = smithyExt.getSmithyBuildConfigs()
                .map(FileCollection::getFiles);
        Provider<Set<String>> inferredModes = project.getProviders().of(
                SmithyBuildModesValueSource.class,
                spec -> spec.getParameters().getSmithyBuildConfigs().set(configFiles));
        Provider<Set<String>> modes = ext.getModes().map(declared ->
                declared.isEmpty() ? inferredModes.get() : declared);

        Configuration smithyBuild = project.getConfigurations().getByName("smithyBuild");
        smithyBuild.withDependencies(deps -> {
            if (!ext.getAutoAddDependencies().getOrElse(true)) {
                return;
            }
            String version = SmithyJavaVersion.VERSION;
            Set<String> resolved = modes.get();
            addIfAbsent(deps, project.getDependencies(), "codegen-plugin", version);
            if (resolved.contains("client")) {
                addIfAbsent(deps, project.getDependencies(), "client-core", version);
            }
            if (resolved.contains("server")) {
                addIfAbsent(deps, project.getDependencies(), "server-api", version);
            }
        });

        Configuration implementation = project.getConfigurations().getByName("implementation");
        implementation.withDependencies(deps -> {
            if (!ext.getAutoAddDependencies().getOrElse(true)) {
                return;
            }
            String version = SmithyJavaVersion.VERSION;
            Set<String> resolved = modes.get();
            boolean hasApi = project.getPlugins().hasPlugin(JavaLibraryPlugin.class);
            boolean serverOnly = resolved.contains("server")
                    && !resolved.contains("client") && !resolved.contains("types");
            if (hasApi && !serverOnly) {
                return;
            }
            addIfAbsent(deps, project.getDependencies(), "core", version);
            addIfAbsent(deps, project.getDependencies(), "framework-errors", version);
            if (resolved.contains("client")) {
                addIfAbsent(deps, project.getDependencies(), "client-core", version);
            }
            if (resolved.contains("server")) {
                addIfAbsent(deps, project.getDependencies(), "server-api", version);
            }
        });

        project.getPlugins().withType(JavaLibraryPlugin.class, plugin -> {
            Configuration api = project.getConfigurations().getByName("api");
            api.withDependencies(deps -> {
                if (!ext.getAutoAddDependencies().getOrElse(true)) {
                    return;
                }
                String version = SmithyJavaVersion.VERSION;
                Set<String> resolved = modes.get();
                boolean serverOnly = resolved.contains("server")
                        && !resolved.contains("client") && !resolved.contains("types");
                if (serverOnly) {
                    return;
                }
                addIfAbsent(deps, project.getDependencies(), "core", version);
                addIfAbsent(deps, project.getDependencies(), "framework-errors", version);
                if (resolved.contains("client")) {
                    addIfAbsent(deps, project.getDependencies(), "client-core", version);
                }
                if (resolved.contains("server")) {
                    addIfAbsent(deps, project.getDependencies(), "server-api", version);
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
            sourceSet.getJava().srcDir(project.files((Callable<Object>) () ->
                    resolveCodegenPaths(smithyExt, ext, "java")));

            sourceSet.getResources().srcDir(project.files((Callable<Object>) () ->
                    resolveCodegenPaths(smithyExt, ext, "resources")));

            Provider<String> projection = smithyExt.getSourceProjection();
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
        Provider<File> outputDirectory = smithyExt.getOutputDirectory().getAsFile();
        project.getTasks().register(CLEAN_SMITHY_OUTPUT_TASK_NAME, Delete.class, task -> {
            task.setGroup("smithy");
            task.setDescription("Cleans the Smithy output directory");
            task.delete(outputDirectory);
        });
        FileSystemOperations fs = getFileSystemOperations();
        project.getTasks().named(SMITHY_BUILD_TASK_NAME, task ->
                task.doFirst(CLEAN_SMITHY_OUTPUT_TASK_NAME, ignored ->
                        fs.delete(spec -> spec.delete(outputDirectory))));
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

        TaskProvider<MergeServiceFilesTask> mergeTask = project.getTasks()
                .register(MERGE_SERVICE_FILES_TASK_NAME, MergeServiceFilesTask.class, task -> {
                    task.dependsOn(SMITHY_BUILD_TASK_NAME);
                    task.setGroup("smithy");

                    task.getServiceDirectories().from(project.files((Callable<Object>) () ->
                            resolveCodegenPaths(smithyExt, ext, "resources/META-INF/services")));

                    task.getServiceDirectories().from(project.files((Callable<Object>) () ->
                            ext.getGeneratedPluginOutputs().get().stream()
                                    .map(name -> smithyExt.getPluginProjectionPath(
                                                    projection.get(), name)
                                            .get()
                                            .resolve("META-INF/services")
                                            .toFile())
                                    .collect(Collectors.toList())));

                    task.onlyIf(t -> {
                        if (!ext.getMergeServiceFiles().getOrElse(true)) {
                            return false;
                        }
                        return ext.getProjections().get().size() > 1
                                || !ext.getGeneratedPluginOutputs().getOrElse(List.of()).isEmpty();
                    });
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
        if (!ext.getMergeServiceFiles().getOrElse(true)) {
            return false;
        }
        return ext.getProjections().get().size() > 1
                || !ext.getGeneratedPluginOutputs().getOrElse(List.of()).isEmpty();
    }

    private static List<File> resolveCodegenPaths(SmithyExtension smithyExt, SmithyJavaExtension ext, String subpath) {
        List<String> explicitProjections = ext.getProjections().get();
        if (explicitProjections.isEmpty()) {
            return List.of(smithyExt.getPluginProjectionPath(
                    smithyExt.getSourceProjection().get(), JAVA_CODEGEN_PLUGIN_NAME)
                    .get().resolve(subpath).toFile());
        }
        return explicitProjections.stream()
                .map(name -> smithyExt.getPluginProjectionPath(name, JAVA_CODEGEN_PLUGIN_NAME)
                        .get().resolve(subpath).toFile())
                .collect(Collectors.toList());
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
