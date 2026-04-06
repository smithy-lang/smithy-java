/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.gradle.tasks;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Merges {@code META-INF/services} files from multiple Smithy build plugin outputs.
 *
 * <p>When multiple plugins (e.g., java-codegen and trait-codegen) each produce
 * service provider configuration files, they must be merged to avoid one overwriting
 * the other. This task collects all service files, groups them by service interface
 * name, deduplicates entries, sorts them, and writes merged files to a single output
 * directory.
 */
@CacheableTask
public abstract class MergeServiceFilesTask extends DefaultTask {

    @Inject
    public MergeServiceFilesTask(ProjectLayout layout) {
        getOutputDirectory().convention(
                layout.getBuildDirectory().dir("merged-services/META-INF/services"));
        setDescription("Merges META-INF/services files from multiple Smithy build plugins.");
    }

    /**
     * Directories containing {@code META-INF/services} files to merge.
     *
     * @return the file collection of service directories
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getServiceDirectories();

    /**
     * Output directory for merged service files.
     *
     * @return the output directory property
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void merge() {
        Map<String, Set<String>> entries = new TreeMap<>();

        for (File serviceDir : getServiceDirectories()) {
            if (!serviceDir.exists() || !serviceDir.isDirectory()) {
                continue;
            }
            File[] files = serviceDir.listFiles();
            if (files == null) {
                continue;
            }

            for (File serviceFile : files) {
                if (!serviceFile.isFile()) {
                    continue;
                }
                try {
                    Set<String> lines = entries.computeIfAbsent(
                            serviceFile.getName(),
                            k -> new TreeSet<>());
                    Files.readAllLines(serviceFile.toPath())
                            .stream()
                            .map(String::trim)
                            .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                            .forEach(lines::add);
                } catch (IOException e) {
                    throw new GradleException(
                            "Failed to read service file: " + serviceFile,
                            e);
                }
            }
        }

        File outputDir = getOutputDirectory().getAsFile().get();
        if (!outputDir.mkdirs() && !outputDir.isDirectory()) {
            throw new GradleException("Failed to create output directory: " + outputDir);
        }

        entries.forEach((name, lines) -> {
            Path outFile = outputDir.toPath().resolve(name);
            try {
                Files.write(outFile,
                        (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new GradleException(
                        "Failed to write merged service file: " + outFile,
                        e);
            }
        });
    }

}
