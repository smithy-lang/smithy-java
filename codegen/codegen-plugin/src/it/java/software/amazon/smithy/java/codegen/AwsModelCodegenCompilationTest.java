/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.abort;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Tests that Java code generation produces compilable code for all AWS service models.
 *
 * <p>Set the {@code API_MODELS_AWS_DIR} environment variable to the root of a local
 * checkout of {@code aws/api-models-aws} to enable this test.
 */
@EnabledIfEnvironmentVariable(named = "API_MODELS_AWS_DIR", matches = ".*")
class AwsModelCodegenCompilationTest {

    private static final Set<String> IGNORED_SDK_IDS = Set.of(
            "timestream-write",
            "timestream-query",
            "clouddirectory"

    );

    private static Path getModelsDir() {
        var dir = System.getenv("API_MODELS_AWS_DIR");
        return Paths.get(dir, "models");
    }

    static Stream<Named<Path>> awsModels() throws IOException {
        var modelsDir = getModelsDir();
        return Files.find(modelsDir,
                4,
                (p, a) -> p.toString().endsWith(".json")
                        && p.getParent().getParent().getFileName().toString().equals("service"))
                .map(p -> Named.of(sdkId(modelsDir, p), p));
    }

    @ParameterizedTest(name = "client: {0}")
    @MethodSource("awsModels")
    @Execution(ExecutionMode.CONCURRENT)
    void compileGeneratedCode(Path modelPath) {
        generateAndCompile(modelPath, "client", "server");
    }

    private void generateAndCompile(Path modelPath, String... modes) {
        // 1. Load model
        Model model = Model.assembler()
                .addImport(modelPath)
                .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .disableValidation()
                .assemble()
                .unwrap();

        ServiceShape service = model.getServiceShapes()
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find service shape"));

        // 2. Run codegen with MockManifest (in-memory)
        MockManifest manifest = new MockManifest();
        PluginContext context = PluginContext.builder()
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", service.getId().toString())
                        .withMember("namespace", "test." + sanitize(service.getId().getName()))
                        .withMember("modes", ArrayNode.fromStrings(modes))
                        .build())
                .model(model)
                .build();
        try {
            new JavaCodegenPlugin().execute(context);

            // 3. Validate all generated files
            assertFalse(manifest.getFiles().isEmpty(), "No files generated for " + service.getId());

            // 4. Collect generated files — Java sources for compilation, others for validation
            List<JavaFileObject> sources = new ArrayList<>();
            for (Path p : manifest.getFiles()) {
                String content = manifest.expectFileString(p);
                assertFalse(content.isBlank(), "Empty generated file: " + p);
                if (p.toString().endsWith(".java")) {
                    sources.add(new InMemoryJavaSource(p.toString(), content));
                }
            }

            // 5. In-memory compile all generated Java sources
            if (!sources.isEmpty()) {
                JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
                DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
                try (var stdFm = compiler.getStandardFileManager(diagnostics, null, null)) {
                    var fm = new InMemoryFileManager(stdFm);
                    boolean ok = compiler.getTask(null,
                            fm,
                            diagnostics,
                            List.of("-classpath", System.getProperty("java.class.path")),
                            null,
                            sources).call();
                    if (!ok) {
                        String errors = diagnostics.getDiagnostics()
                                .stream()
                                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                                .map(Object::toString)
                                .collect(Collectors.joining("\n"));
                        Path dumpFile = dumpGeneratedSources(service.getId().toString(), sources, errors);
                        fail(Arrays.toString(modes) + " compilation failed for " + service.getId()
                                + ". Generated sources dumped to: " + dumpFile + "\n" + errors);
                    }
                }
            }
        } catch (Throwable t) {
            if (IGNORED_SDK_IDS.contains(sdkId(getModelsDir(), modelPath))) {
                abort("Known failure for " + service.getId() + ": " + t.getMessage());
            }
            sneakyThrow(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable throwable) throws T {
        throw (T) throwable;
    }

    private static Path dumpGeneratedSources(String serviceId, List<JavaFileObject> sources, String errors) {
        try {
            var dir = Files.createTempDirectory("codegen-fail-" + sanitize(serviceId) + "-");
            for (var source : sources) {
                // Strip the MockManifest "/test/" base dir prefix to get a clean relative path
                var fileName = Path.of(source.getName()).getFileName();
                var name = fileName != null ? fileName.toString() : source.getName();
                Files.writeString(dir.resolve(name), source.getCharContent(true));
            }
            Files.writeString(dir.resolve("ERRORS.txt"), errors);
            return dir;
        } catch (IOException e) {
            return Path.of("<dump failed: " + e.getMessage() + ">");
        }
    }

    private static String sdkId(Path modelsDir, Path modelPath) {
        return modelsDir.relativize(modelPath).getName(0).toString();
    }

    private static String sanitize(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /**
     * In-memory source file — wraps generated code string as JavaFileObject.
     */
    private static class InMemoryJavaSource extends SimpleJavaFileObject {
        private final String code;

        InMemoryJavaSource(String path, String code) {
            super(URI.create("string:///" + path.replace('\\', '/')), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    /**
     * In-memory output manager — discards .class bytes.
     */
    private static class InMemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        InMemoryFileManager(StandardJavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                JavaFileManager.Location location,
                String className,
                JavaFileObject.Kind kind,
                FileObject sibling
        ) {
            return new SimpleJavaFileObject(
                    URI.create("mem:///" + className.replace('.', '/') + kind.extension),
                    kind) {
                @Override
                public OutputStream openOutputStream() {
                    return new ByteArrayOutputStream();
                }
            };
        }
    }
}
