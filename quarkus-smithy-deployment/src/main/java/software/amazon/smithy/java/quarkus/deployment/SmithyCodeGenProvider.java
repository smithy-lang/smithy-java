/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.quarkus.deployment;

import io.quarkus.bootstrap.prebuild.CodeGenException;
import io.quarkus.deployment.CodeGenContext;
import io.quarkus.deployment.CodeGenProvider;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.build.SmithyBuild;
import software.amazon.smithy.build.SmithyBuildResult;
import software.amazon.smithy.build.model.SmithyBuildConfig;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;

/**
 * Quarkus {@link CodeGenProvider} that runs Smithy code generation in-process
 * during {@code quarkusGenerateCode}.
 *
 * <p>Activated when Quarkus's build pipeline finds a non-empty
 * {@code src/main/smithy/} directory in the project. Locates
 * {@code smithy-build.json} at the project root, runs {@link SmithyBuild} with
 * the deployment classloader (so {@code java-codegen} plugin is discovered via
 * SPI), and lays generated Java sources into Quarkus's expected output
 * directory.
 *
 * <p>Layout note: {@code SmithyBuild} writes to
 * {@code <output>/<projection>/<plugin>/{java,resources}/...}. To avoid that
 * extra nesting confusing Quarkus's source-set wiring, we point SmithyBuild at
 * a sibling working directory and copy the generated {@code java/} and
 * {@code resources/} payloads up to {@link CodeGenContext#outDir()}.
 */
public final class SmithyCodeGenProvider implements CodeGenProvider {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(SmithyCodeGenProvider.class);

    private static final String PROVIDER_ID = "smithy";
    private static final String SMITHY_BUILD_JSON = "smithy-build.json";
    private static final String JAVA_CODEGEN_PLUGIN = "java-codegen";
    private static final String DEFAULT_PROJECTION = "source";

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public String[] inputExtensions() {
        return new String[] {"smithy"};
    }

    @Override
    public String inputDirectory() {
        return PROVIDER_ID;
    }

    @Override
    public boolean trigger(CodeGenContext context) throws CodeGenException {
        Path projectRoot = resolveProjectRoot(context.inputDir());
        Path smithyBuildJson = projectRoot.resolve(SMITHY_BUILD_JSON);
        if (!Files.exists(smithyBuildJson)) {
            LOGGER.info(
                    "No {} at project root {} — skipping Smithy code generation",
                    SMITHY_BUILD_JSON,
                    projectRoot);
            return false;
        }

        SmithyBuildConfig config;
        try {
            config = SmithyBuildConfig.load(smithyBuildJson);
        } catch (RuntimeException e) {
            throw new CodeGenException("Failed to load " + smithyBuildJson, e);
        }

        if (!config.getPlugins().containsKey(JAVA_CODEGEN_PLUGIN)) {
            LOGGER.info(
                    "{} does not configure the '{}' plugin — skipping Smithy code generation",
                    SMITHY_BUILD_JSON,
                    JAVA_CODEGEN_PLUGIN);
            return false;
        }

        Path stagingDir = context.workDir().resolve("smithy-build-staging");
        try {
            // Always start with a clean staging area so removed shapes do not
            // leave stale generated files behind.
            deleteRecursive(stagingDir);
            Files.createDirectories(stagingDir);
        } catch (IOException e) {
            throw new CodeGenException("Failed to prepare staging directory " + stagingDir, e);
        }

        SmithyBuildResult result;
        try {
            ClassLoader classLoader = SmithyCodeGenProvider.class.getClassLoader();
            SmithyBuild build = SmithyBuild.create(classLoader)
                    .config(config)
                    .importBasePath(projectRoot)
                    .outputDirectory(stagingDir);

            // Resolve `sources` from smithy-build.json relative to the project
            // root and register them as absolute paths. SmithyBuild's config
            // path resolution otherwise depends on the JVM working directory.
            List<Path> resolvedSources = new ArrayList<>();
            for (String source : config.getSources()) {
                Path resolved = projectRoot.resolve(source).toAbsolutePath();
                if (Files.exists(resolved)) {
                    resolvedSources.add(resolved);
                } else {
                    LOGGER.warn("Smithy source path {} does not exist; skipping", resolved);
                }
            }
            // If smithy-build.json had no `sources`, fall back to {projectRoot}/model/
            // (Smithy CLI's documented default).
            if (resolvedSources.isEmpty()) {
                Path defaultModelDir = projectRoot.resolve("model");
                if (Files.isDirectory(defaultModelDir)) {
                    resolvedSources.add(defaultModelDir.toAbsolutePath());
                }
            }
            if (!resolvedSources.isEmpty()) {
                build.registerSources(resolvedSources.toArray(new Path[0]));
            }

            // Also explicitly load every .smithy file under each source root into
            // a Model and pass that to SmithyBuild so the projection has shapes
            // to project. registerSources() alone is not enough — it tags shapes
            // as "source" but does not always preload them when an explicit
            // SmithyBuildConfig is also provided.
            Model model = loadModel(classLoader, resolvedSources);
            build.model(model);

            result = build.build();
        } catch (RuntimeException e) {
            throw new CodeGenException("Smithy code generation failed", e);
        } catch (IOException e) {
            throw new CodeGenException("Failed to walk Smithy source directories", e);
        }

        if (!result.getProjectionResult(DEFAULT_PROJECTION).isPresent()) {
            LOGGER.warn(
                    "Smithy build produced no '{}' projection result; nothing to copy",
                    DEFAULT_PROJECTION);
            return false;
        }

        Path generatedRoot = stagingDir
                .resolve(DEFAULT_PROJECTION)
                .resolve(JAVA_CODEGEN_PLUGIN);
        try {
            return mergeGeneratedSources(generatedRoot, context.outDir());
        } catch (IOException e) {
            throw new CodeGenException("Failed to copy generated sources to " + context.outDir(), e);
        }
    }

    private static Model loadModel(ClassLoader classLoader, List<Path> sources) throws IOException {
        ModelAssembler assembler = Model.assembler(classLoader).discoverModels(classLoader);
        for (Path src : sources) {
            if (Files.isDirectory(src)) {
                try (var paths = Files.walk(src)) {
                    paths.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".smithy"))
                            .forEach(assembler::addImport);
                }
            } else {
                assembler.addImport(src);
            }
        }
        return assembler.assemble().unwrap();
    }

    /**
     * Smithy's {@code java-codegen} plugin writes Java sources at
     * {@code <pluginRoot>/java/<package>/...} and resources at
     * {@code <pluginRoot>/resources/<package>/...}. Quarkus's compileJava
     * picks up everything under {@link CodeGenContext#outDir()} as sources, so
     * we flatten one level.
     *
     * @return {@code true} if at least one file was copied.
     */
    private static boolean mergeGeneratedSources(Path pluginRoot, Path destRoot) throws IOException {
        if (!Files.isDirectory(pluginRoot)) {
            return false;
        }
        boolean copiedAny = false;
        Path javaDir = pluginRoot.resolve("java");
        if (Files.isDirectory(javaDir)) {
            copiedAny |= copyTree(javaDir, destRoot);
        }
        Path resourcesDir = pluginRoot.resolve("resources");
        if (Files.isDirectory(resourcesDir)) {
            copiedAny |= copyTree(resourcesDir, destRoot);
        }
        return copiedAny;
    }

    private static boolean copyTree(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        boolean[] copied = new boolean[1];
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path resolved = target.resolve(source.relativize(file).toString());
                Files.copy(file, resolved, StandardCopyOption.REPLACE_EXISTING);
                copied[0] = true;
                return FileVisitResult.CONTINUE;
            }
        });
        return copied[0];
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * {@link CodeGenContext#inputDir()} resolves to
     * {@code <project>/src/main/smithy} for the main code generation pass.
     * The project root is therefore three levels up. (For tests it would be
     * {@code <project>/src/test/smithy}.)
     */
    private static Path resolveProjectRoot(Path inputDir) {
        Path root = inputDir;
        for (int i = 0; i < 3; i++) {
            Path parent = root.getParent();
            if (parent == null) {
                return inputDir;
            }
            root = parent;
        }
        return root;
    }
}
