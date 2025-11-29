/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.SmithyBuild;
import software.amazon.smithy.build.model.SmithyBuildConfig;
import software.amazon.smithy.model.Model;

public class PluginTestRunner {

    private PluginTestRunner() {}

    public static List<TestCase> addTestCasesFromUrl(URL url) {
        if (!"file".equals(url.getProtocol())) {
            throw new IllegalArgumentException("Only file URLs are supported: " + url);
        }
        try {
            return addTestCasesFromDirectory(Paths.get(url.toURI()));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<TestCase> addTestCasesFromDirectory(Path rootDir) {
        try (Stream<Path> files = Files.walk(rootDir, 1)) {
            var testCases = new ArrayList<TestCase>();
            files.map(Path::toFile)
                    .filter(File::isDirectory)
                    .filter(dir -> new File(dir, "smithy-build.json").exists())
                    .map(PluginTestRunner::fromDirectory)
                    .forEach(testCases::add);
            return testCases;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static TestCase fromDirectory(File dir) {
        var configFile = new File(dir, "smithy-build.json");
        var modelDir = new File(dir, "model");
        var model = Model.assembler()
                .addImport(modelDir.toPath())
                .discoverModels()
                .assemble()
                .unwrap();
        var manifests = new ArrayList<MockManifest>();
        Function<Path, FileManifest> fileManifestFactory = pluginBaseDir -> {
            var fileManifest = new MockManifest(pluginBaseDir);
            manifests.add(fileManifest);
            return fileManifest;
        };
        var config = SmithyBuildConfig.builder()
                .load(configFile.toPath())
                .outputDirectory("build")
                .build();
        var builder = new SmithyBuild()
                .fileManifestFactory(fileManifestFactory)
                .config(config)
                .model(model);
        var expectedFiles = new ArrayList<Path>();
        var expectedDir = new File(dir, "expected").toPath();

        try {
            Files.walkFileTree(expectedDir, new ExpectedFileVisitor(expectedFiles));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var expectedToContents = getExpectedContents(expectedDir, expectedFiles);
        return builder()
                .name(dir.toPath().getFileName().toString())
                .builder(builder)
                .manifests(manifests)
                .expectedContents(expectedToContents)
                .build();
    }

    static Map<String, String> getExpectedContents(Path base, List<Path> paths) {
        var result = new HashMap<String, String>();
        try {
            for (var path : paths) {
                var relative = base.relativize(path).toString();
                var contents = Files.readString(path);
                result.put(relative, contents);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

    public static TestCaseBuilder builder() {
        return new TestCaseBuilder();
    }

    public static class TestCase {
        private final String name;
        private final SmithyBuild builder;
        private final List<MockManifest> manifests;
        private final Map<String, String> expectedContent;
        private Map<String, String> actualContent;

        TestCase(TestCaseBuilder builder) {
            this.name = Objects.requireNonNull(builder.name, "name");
            this.builder = Objects.requireNonNull(builder.builder, "builder");
            this.manifests = Objects.requireNonNull(builder.manifests, "manifest");
            this.expectedContent = Objects.requireNonNull(builder.expectedContents, "expected");
        }

        public String name() {
            return name;
        }

        public Map<String, String> expectedContents() {
            return expectedContent;
        }

        public String getActualContent(String name) {
            if (actualContent == null) {
                builder.build();
                actualContent = new HashMap<>();
                for (var manifest : manifests) {
                    for (var path : manifest.getFiles()) {
                        actualContent.put(path.toFile().getName(),
                                manifest.expectFileString(manifest.getBaseDir().relativize(path)));
                    }
                }
            }
            return actualContent.get(name);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class TestCaseBuilder {
        private String name;
        private SmithyBuild builder;
        private List<MockManifest> manifests;
        private Map<String, String> expectedContents;

        public TestCaseBuilder name(String name) {
            this.name = name;
            return this;
        }

        public TestCaseBuilder manifests(List<MockManifest> manifests) {
            this.manifests = manifests;
            return this;
        }

        public TestCaseBuilder builder(SmithyBuild builder) {
            this.builder = builder;
            return this;
        }

        public TestCaseBuilder expectedContents(Map<String, String> expected) {
            this.expectedContents = expected;
            return this;
        }

        public TestCase build() {
            return new TestCase(this);
        }
    }

    public static class ExpectedFileVisitor extends SimpleFileVisitor<Path> {
        private final List<Path> expectedFiles;

        public ExpectedFileVisitor(List<Path> expectedFiles) {
            this.expectedFiles = expectedFiles;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (Files.isRegularFile(file)) {
                expectedFiles.add(file);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            // Handle the error and continue
            exc.printStackTrace();
            return FileVisitResult.CONTINUE;
        }
    }
}
