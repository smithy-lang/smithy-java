/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.size;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.java.codegen.JavaCodegenPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.loader.ModelDiscovery;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Generates and compiles code for Smithy service models, then measures source, class, and jar sizes.
 *
 * <p>{@code report} writes JSON sizes for each service and records generation or compilation failures.
 * Sizes include only {@code --size-modes} output. {@code --detail} adds category and class sizes.
 * A single model always includes these details.
 *
 * <p>{@code compare} writes a Markdown comparison, or a head summary if the base is absent or has an incompatible format.
 * It returns status 3 for head failures unless they are known failures or also failed on base.
 * A service that compiled on base always causes a regression if it fails on head.
 * {@code report --baseline} compares reports after measuring.
 *
 * <p>Run this tool with the {@code codegenSize} and {@code codegenSizeCompare} Gradle tasks in {@code :codegen:codegen-plugin}.
 *
 * <pre>
 * report --models &lt;dir|model.jar|model.json&gt;[,...] --out &lt;report.json&gt; [--only ec2,s3] [--bom 1.0.337]
 *     [--modes client,server] [--size-modes client] [--detail ec2] [--threads 4]
 *     [--baseline base.json] [--markdown report.md] [--status status.txt] [--dump &lt;dir&gt;]
 * compare [base.json] head.json [--markdown report.md] [--status status.txt]
 * </pre>
 */
public final class CodegenSize {

    static final int FORMAT = 3;

    private static final int EXIT_USAGE = 2;

    private static final int EXIT_REGRESSION = 3;

    private static final int MAX_ERROR_LINES = 30;

    private static final int MAX_SERVICE_MOVERS = 15;

    private static final int MAX_CLASS_MOVERS = 15;

    private static final String DEFAULT_MODES = "client,server";

    private static final String DEFAULT_SIZE_MODES = "client";

    private static final String DEFAULT_DETAIL = "ec2";

    private static final Set<String> KNOWN_MODES = Set.of("client", "server");

    private static final Set<String> KNOWN_FAILURES = Set.of(
            "timestream-write",
            "timestream-query",
            "clouddirectory",
            "deadline");

    /**
     * Use a fixed local time within the DOS range to avoid timezone-dependent ZIP fields.
     */
    private static final LocalDateTime ENTRY_TIME = LocalDateTime.of(1980, 2, 1, 0, 0);

    private static final List<String> JAVAC_FLAGS = List.of("--release", "21", "-g", "-proc:none", "-nowarn");

    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern DECLARATION = Pattern.compile(
            "^(?:public\\s+)?(?:(?:final|abstract|sealed|non-sealed|static)\\s+)*(class|interface|record|enum)\\s+(\\w+)\\b[^{]*",
            Pattern.MULTILINE);
    private static final Pattern OPERATION = Pattern.compile(
            "\\b(?:extends|implements)\\s+(?:[\\w.]*\\.)?(?:Abstract)?ApiOperation\\b");
    private static final Pattern EXCEPTION = Pattern.compile("\\bextends\\s+[\\w.]*Exception\\b");
    private static final Pattern VERSIONED_JAR = Pattern.compile("-\\d[\\w.]*\\.jar$");

    private static final String USAGE = """
            usage:
              report --models <dir|model.jar|model.json>[,...] --out <report.json> [--only <name>[,...]]
                  [--bom <version>] [--modes client,server] [--size-modes client] [--detail ec2[,...]]
                  [--service <shape id>] [--threads <n>] [--baseline <base.json>] [--markdown <report.md>]
                  [--status <file>] [--dump <dir>]
              compare [<base.json>] <head.json> [--markdown <report.md>] [--status <file>]
            exit status: 0 success, 2 usage error, 3 a service fails on head but not on base""";

    private CodegenSize() {}

    public static void main(String[] args) throws IOException {
        System.exit(run(args));
    }

    static int run(String... args) throws IOException {
        try {
            Options options = Options.parse(args);
            return switch (options.command) {
                case "report" -> report(options);
                case "compare" -> compare(options);
                default -> throw new IllegalArgumentException("unknown command: " + options.command);
            };
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println(USAGE);
            return EXIT_USAGE;
        }
    }

    private static int report(Options options) throws IOException {
        String modelsArgument = options.require("models");
        Map<String, Path> models = keyModels(modelFiles(modelsArgument));
        if (options.named.containsKey("only")) {
            models = select(models, csv(options.named.get("only")));
        }
        Path out = options.requirePath("out");
        Path dump = options.path("dump");
        List<String> modes = csv(options.named.getOrDefault("modes", DEFAULT_MODES));
        Set<String> sizeModes = new TreeSet<>(csv(options.named.getOrDefault("size-modes", DEFAULT_SIZE_MODES)));
        for (String mode : modes) {
            if (!KNOWN_MODES.contains(mode)) {
                throw new IllegalArgumentException("unknown mode: " + mode);
            }
        }
        if (!modes.containsAll(sizeModes)) {
            throw new IllegalArgumentException("--size-modes must be a subset of --modes");
        }
        String serviceId = options.named.get("service");
        if (serviceId != null && models.size() > 1) {
            throw new IllegalArgumentException("--service requires a single model");
        }
        Set<String> detail = models.size() == 1
                ? null
                : new TreeSet<>(csv(options.named.getOrDefault("detail", DEFAULT_DETAIL)));
        int threads = options.named.containsKey("threads")
                ? Integer.parseInt(options.named.get("threads"))
                : Math.min(8, Runtime.getRuntime().availableProcessors());
        threads = Math.max(1, Math.min(threads, models.size()));

        System.err.printf(Locale.ROOT,
                "measuring %,d model(s) with modes %s on %d thread(s); sizes count %s output%n",
                models.size(),
                modes,
                threads,
                sizeModes);
        long start = System.nanoTime();
        Map<String, ObjectNode> services = new TreeMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            Map<String, Future<ObjectNode>> futures = new LinkedHashMap<>();
            AtomicInteger done = new AtomicInteger();
            int total = models.size();
            for (Map.Entry<String, Path> entry : models.entrySet()) {
                String key = entry.getKey();
                Path path = entry.getValue();
                boolean withDetail = detail == null || detail.contains(key);
                Path serviceDump = dump == null ? null : dump.resolve(key);
                futures.put(key,
                        pool.submit(() -> measure(key,
                                path,
                                serviceId,
                                modes,
                                sizeModes,
                                withDetail,
                                serviceDump,
                                done,
                                total)));
            }
            for (Map.Entry<String, Future<ObjectNode>> entry : futures.entrySet()) {
                services.put(entry.getKey(), entry.getValue().get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while measuring", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("measurement failed", e.getCause());
        } finally {
            pool.shutdownNow();
        }

        ObjectNode.Builder servicesNode = Node.objectNodeBuilder();
        services.forEach(servicesNode::withMember);
        ObjectNode.Builder modelsNode = Node.objectNodeBuilder()
                .withMember("source", modelsArgument)
                .withMember("count", models.size());
        if (options.named.containsKey("bom")) {
            modelsNode.withMember("bom", options.named.get("bom"));
        }
        ObjectNode report = Node.objectNodeBuilder()
                .withMember("tool", "codegen-size")
                .withMember("format", FORMAT)
                .withMember("modes", ArrayNode.fromStrings(modes))
                .withMember("sizeModes", ArrayNode.fromStrings(new ArrayList<>(sizeModes)))
                .withMember("javac",
                        Node.objectNodeBuilder()
                                .withMember("version", Runtime.version().toString())
                                .withMember("flags", ArrayNode.fromStrings(JAVAC_FLAGS))
                                .build())
                .withMember("models", modelsNode.build())
                .withMember("services", servicesNode.build())
                .build();
        writeString(out, Node.prettyPrintJson(report));

        long failed = services.values().stream().filter(s -> s.containsMember("failure")).count();
        long jarBytes = 0;
        for (ObjectNode service : services.values()) {
            if (!service.containsMember("failure")) {
                jarBytes += jarBytes(service);
            }
        }
        System.err.printf(Locale.ROOT,
                "measured %,d service(s) in %,d s: %,d failed, %s of jars in total; wrote %s%n",
                services.size(),
                (System.nanoTime() - start) / 1_000_000_000L,
                failed,
                bytes(jarBytes),
                out);

        Path baseline = options.path("baseline");
        if (baseline != null) {
            BaseReport base = readBaseReport(baseline);
            return finish(base.report, report, base.warning, options);
        }
        return 0;
    }

    private static Map<String, Path> select(Map<String, Path> models, List<String> names) {
        Map<String, Path> selected = new TreeMap<>();
        List<String> missing = new ArrayList<>();
        for (String name : names) {
            Path path = models.get(name);
            if (path == null) {
                missing.add(name);
            } else {
                selected.put(name, path);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("--only names unknown models: " + missing);
        }
        return selected;
    }

    private static ObjectNode measure(
            String key,
            Path modelPath,
            String serviceId,
            List<String> modes,
            Set<String> sizeModes,
            boolean detail,
            Path dump,
            AtomicInteger done,
            int total
    ) {
        ObjectNode.Builder modelNode = Node.objectNodeBuilder()
                .withMember("file", String.valueOf(modelPath.getFileName()));
        String artifact = modelArtifact(modelPath);
        if (artifact != null) {
            modelNode.withMember("artifact", artifact);
        }
        boolean known = KNOWN_FAILURES.contains(key);
        String stage = "load";
        try {
            Model model = loadModel(modelPath);
            ServiceShape service = selectService(model, serviceId);
            String namespace = "gen." + sanitize(service.getId().getName());
            modelNode.withMember("service", service.getId().toString()).withMember("namespace", namespace);

            stage = "codegen";
            long start = System.nanoTime();
            MockManifest manifest = new MockManifest();
            PluginContext context = PluginContext.builder()
                    .fileManifest(manifest)
                    .settings(ObjectNode.builder()
                            .withMember("service", service.getId().toString())
                            .withMember("namespace", namespace)
                            .withMember("modes", ArrayNode.fromStrings(modes))
                            .build())
                    .model(model)
                    .build();
            new JavaCodegenPlugin().execute(context);
            long codegenMillis = (System.nanoTime() - start) / 1_000_000;

            Map<String, String> sources = new TreeMap<>();
            Map<String, byte[]> resources = new TreeMap<>();
            for (Path file : manifest.getFiles()) {
                String path = file.toString().replace('\\', '/');
                if (path.endsWith(".java")) {
                    String content = manifest.expectFileString(file);
                    sources.put(qualifiedName(path, content), content);
                } else {
                    resources.put(resourcePath(path), manifest.expectFileBytes(file));
                }
            }
            if (sources.isEmpty()) {
                throw new IllegalStateException("no Java sources were generated");
            }
            if (dump != null) {
                dumpSources(dump, sources, resources);
            }

            stage = "compile";
            start = System.nanoTime();
            Map<String, byte[]> classes = compile(sources, dump);
            long compileMillis = (System.nanoTime() - start) / 1_000_000;

            stage = "measure";
            Measurement measurement = measureSizes(sources, resources, classes, namespace, sizeModes, detail);
            if (dump != null) {
                dumpClasses(dump, classes, measurement.jar);
            }

            ObjectNode.Builder node = Node.objectNodeBuilder()
                    .withMember("model", modelNode.build())
                    .withMember("timings",
                            Node.objectNodeBuilder()
                                    .withMember("codegenMillis", codegenMillis)
                                    .withMember("compileMillis", compileMillis)
                                    .build())
                    .withMember("totals", measurement.totals);
            if (detail) {
                node.withMember("categories", measurement.categories).withMember("classes", measurement.classes);
            }
            if (known) {
                node.withMember("knownFailure", true);
            }
            System.err.printf(Locale.ROOT,
                    "[%d/%d] %s: %s jar, %,d classes (%,d generated); codegen %,d ms, javac %,d ms%n",
                    done.incrementAndGet(),
                    total,
                    key,
                    bytes(number(measurement.totals, "jarBytes")),
                    number(measurement.totals, "classes"),
                    classes.size(),
                    codegenMillis,
                    compileMillis);
            return node.build();
        } catch (OutOfMemoryError e) {
            // The workers share the heap. Failed allocations may also affect other workers, so abort the run.
            throw new IllegalStateException(key + " ran out of memory during " + stage, e);
        } catch (Throwable t) {
            System.err.printf(Locale.ROOT,
                    "[%d/%d] %s: FAILED during %s%s: %s%n",
                    done.incrementAndGet(),
                    total,
                    key,
                    stage,
                    known ? " (known failure)" : "",
                    firstLine(t));
            ObjectNode.Builder node = Node.objectNodeBuilder()
                    .withMember("model", modelNode.build())
                    .withMember("failure", failureNode(stage, t));
            if (known) {
                node.withMember("knownFailure", true);
            }
            return node.build();
        }
    }

    private record Measurement(ObjectNode totals, ObjectNode categories, ObjectNode classes, byte[] jar) {}

    private static Measurement measureSizes(
            Map<String, String> sources,
            Map<String, byte[]> resources,
            Map<String, byte[]> classes,
            String namespace,
            Set<String> sizeModes,
            boolean detail
    ) throws IOException {
        String serverPackage = namespace + ".service.";
        boolean client = sizeModes.contains("client");
        boolean server = sizeModes.contains("server");

        Map<String, String> categories = new HashMap<>();
        if (detail) {
            sources.forEach((name, content) -> categories.put(name, categorize(name, content)));
        }

        Map<String, Sizes> byCategory = new TreeMap<>();
        Map<String, Sizes> byClass = new TreeMap<>();
        Map<String, String> classCategory = new TreeMap<>();
        long classBytes = 0;
        long classCount = 0;
        Manifest jarManifest = new Manifest();
        jarManifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        ByteArrayOutputStream jarBuffer = new ByteArrayOutputStream(1 << 20);
        try (JarOutputStream jar = new JarOutputStream(jarBuffer, jarManifest)) {
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                boolean serverClass = entry.getKey().startsWith(serverPackage);
                boolean counted = serverClass ? server : client;
                if (!counted) {
                    continue;
                }
                classBytes += entry.getValue().length;
                classCount++;
                long deflated = addEntry(jar, entry.getKey().replace('.', '/') + ".class", entry.getValue());
                if (detail) {
                    String top = topLevel(entry.getKey());
                    String category = serverClass ? "server" : categories.getOrDefault(top, "other");
                    add(byCategory, category, entry.getValue().length, deflated);
                    add(byClass, top, entry.getValue().length, deflated);
                    classCategory.put(top, category);
                }
            }
            if (client) {
                for (Map.Entry<String, byte[]> entry : resources.entrySet()) {
                    long deflated = addEntry(jar, entry.getKey(), entry.getValue());
                    if (detail) {
                        add(byCategory, "resources", entry.getValue().length, deflated);
                        add(byClass, entry.getKey(), entry.getValue().length, deflated);
                        classCategory.put(entry.getKey(), "resources");
                    }
                }
            }
        }

        long sourceFiles = 0;
        long sourceBytes = 0;
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            boolean serverSource = entry.getKey().startsWith(serverPackage);
            boolean counted = serverSource ? server : client;
            if (!counted) {
                continue;
            }
            sourceFiles++;
            sourceBytes += entry.getValue().getBytes(StandardCharsets.UTF_8).length;
        }

        ObjectNode totals = Node.objectNodeBuilder()
                .withMember("sourceFiles", sourceFiles)
                .withMember("sourceBytes", sourceBytes)
                .withMember("resourceFiles", client ? resources.size() : 0)
                .withMember("classes", classCount)
                .withMember("classBytes", classBytes)
                .withMember("jarBytes", jarBuffer.size())
                .build();
        return new Measurement(totals,
                detail ? sizesNode(byCategory, null) : null,
                detail ? sizesNode(byClass, classCategory) : null,
                jarBuffer.toByteArray());
    }

    private static ObjectNode failureNode(String stage, Throwable t) {
        String message;
        if (t instanceof CompilationFailure failure) {
            message = failure.errors;
        } else {
            StringBuilder sb = new StringBuilder(t.toString());
            StackTraceElement[] frames = t.getStackTrace();
            for (int i = 0; i < Math.min(8, frames.length); i++) {
                sb.append("\n\tat ").append(frames[i]);
            }
            message = sb.toString();
        }
        return Node.objectNodeBuilder()
                .withMember("stage", stage)
                .withMember("message", truncateLines(message, MAX_ERROR_LINES))
                .build();
    }

    private static String truncateLines(String text, int maxLines) {
        String[] lines = text.split("\n");
        if (lines.length <= maxLines) {
            return text;
        }
        return Stream.of(lines).limit(maxLines).collect(Collectors.joining("\n"))
                + String.format(Locale.ROOT, "%n... %,d more lines", lines.length - maxLines);
    }

    private static String firstLine(Throwable t) {
        String message = t instanceof CompilationFailure failure ? failure.errors : t.toString();
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    private static int compare(Options options) throws IOException {
        if (options.positional.isEmpty() || options.positional.size() > 2) {
            throw new IllegalArgumentException("compare takes one or two reports: [<base.json>] <head.json>");
        }
        ObjectNode head = readReport(Path.of(options.positional.get(options.positional.size() - 1)));
        BaseReport base = options.positional.size() == 2
                ? readBaseReport(Path.of(options.positional.get(0)))
                : new BaseReport(null, null);
        return finish(base.report, head, base.warning, options);
    }

    private static int finish(ObjectNode base, ObjectNode head, String baseWarning, Options options)
            throws IOException {
        Comparison comparison = Comparison.of(base, head);
        emitMarkdown(markdown(comparison, baseWarning), options.path("markdown"));
        String status = !comparison.regressions.isEmpty()
                ? "regression"
                : comparison.changed() ? "changed" : "unchanged";
        Path statusFile = options.path("status");
        if (statusFile != null) {
            writeString(statusFile, status + System.lineSeparator());
        }
        System.err.println("status: " + status);
        return status.equals("regression") ? EXIT_REGRESSION : 0;
    }

    private static void emitMarkdown(String markdown, Path target) throws IOException {
        if (target != null) {
            writeString(target, markdown);
        }
        System.out.print(markdown);
    }

    private static ObjectNode readReport(Path path) throws IOException {
        ObjectNode report = Node.parse(Files.readString(path)).expectObjectNode();
        int format = report.getNumberMember("format").map(n -> n.getValue().intValue()).orElse(0);
        if (format != FORMAT) {
            throw new IncompatibleReport(path, format);
        }
        return report;
    }

    private record BaseReport(ObjectNode report, String warning) {}

    private static BaseReport readBaseReport(Path path) throws IOException {
        try {
            return new BaseReport(readReport(path), null);
        } catch (IncompatibleReport e) {
            System.err.println("warning: " + e.getMessage() + "; reporting the head alone");
            return new BaseReport(null,
                    "the base report has format " + e.format + " but this tool reads format " + FORMAT);
        }
    }

    private static final class IncompatibleReport extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        private final int format;

        IncompatibleReport(Path path, int format) {
            super(path + " has report format " + format + ", expected " + FORMAT);
            this.format = format;
        }
    }

    private record Comparison(
            ObjectNode base,
            ObjectNode head,
            Map<String, ObjectNode> baseServices,
            Map<String, ObjectNode> headServices,
            List<String> measured,
            List<String> regressions,
            List<String> known,
            List<String> stale,
            List<String> fixed,
            List<String> preexisting,
            List<String> onlyBase,
            List<String> onlyHead) {
        private enum Kind {
            MEASURED,
            REGRESSION,
            KNOWN,
            FIXED,
            PREEXISTING,
            ONLY_BASE,
            NEW
        }

        static Comparison of(ObjectNode base, ObjectNode head) {
            Map<String, ObjectNode> baseServices = base == null ? Map.of() : services(base);
            Map<String, ObjectNode> headServices = services(head);
            Set<String> names = new TreeSet<>(baseServices.keySet());
            names.addAll(headServices.keySet());
            Map<Kind, List<String>> kinds = new EnumMap<>(Kind.class);
            for (Kind kind : Kind.values()) {
                kinds.put(kind, new ArrayList<>());
            }
            List<String> stale = new ArrayList<>();
            List<String> onlyHead = new ArrayList<>();
            for (String name : names) {
                ObjectNode b = baseServices.get(name);
                ObjectNode h = headServices.get(name);
                kinds.get(classify(b, h, base != null)).add(name);
                if (h != null && b == null && base != null) {
                    onlyHead.add(name);
                }
                if (h != null && isKnownFailure(h) && !h.containsMember("failure")) {
                    stale.add(name);
                }
            }
            return new Comparison(base,
                    head,
                    baseServices,
                    headServices,
                    kinds.get(Kind.MEASURED),
                    kinds.get(Kind.REGRESSION),
                    kinds.get(Kind.KNOWN),
                    stale,
                    kinds.get(Kind.FIXED),
                    kinds.get(Kind.PREEXISTING),
                    kinds.get(Kind.ONLY_BASE),
                    onlyHead);
        }

        private static Kind classify(ObjectNode b, ObjectNode h, boolean hasBase) {
            if (h == null) {
                return Kind.ONLY_BASE;
            }
            boolean baseFails = b != null && b.containsMember("failure");
            if (!h.containsMember("failure")) {
                if (baseFails) {
                    return Kind.FIXED;
                }
                return b == null && hasBase ? Kind.NEW : Kind.MEASURED;
            }
            if (b != null && !baseFails) {
                return Kind.REGRESSION;
            }
            if (isKnownFailure(h)) {
                return Kind.KNOWN;
            }
            return baseFails ? Kind.PREEXISTING : Kind.REGRESSION;
        }

        private static boolean isKnownFailure(ObjectNode service) {
            return service.getBooleanMemberOrDefault("knownFailure", false);
        }

        private static Map<String, ObjectNode> services(ObjectNode report) {
            Map<String, ObjectNode> services = new TreeMap<>();
            report.expectObjectMember("services")
                    .getStringMap()
                    .forEach((k, v) -> services.put(k, v.expectObjectNode()));
            return services;
        }

        boolean hasBase() {
            return base != null;
        }

        long delta(String name) {
            return jarBytes(headServices.get(name)) - jarBytes(baseServices.get(name));
        }

        String largestIncrease() {
            return measured.stream()
                    .filter(name -> delta(name) > 0)
                    .max(Comparator.comparingLong(this::delta))
                    .orElse(null);
        }

        String largestDecrease() {
            return measured.stream()
                    .filter(name -> delta(name) < 0)
                    .min(Comparator.comparingLong(this::delta))
                    .orElse(null);
        }

        boolean changed() {
            if (!regressions.isEmpty() || !fixed.isEmpty() || !onlyBase.isEmpty() || !onlyHead.isEmpty()) {
                return true;
            }
            if (!hasBase()) {
                return true;
            }
            for (String name : measured) {
                if (!sameTotals(totals(baseServices.get(name)), totals(headServices.get(name)))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String markdown(Comparison comparison, String baseWarning) {
        StringBuilder sb = new StringBuilder();
        ObjectNode head = comparison.head;
        ObjectNode javac = head.expectObjectMember("javac");
        boolean single = comparison.headServices.size() == 1 && comparison.baseServices.size() <= 1;

        sb.append("## Codegen size report\n\n");
        if (single) {
            ObjectNode service = comparison.headServices.values().iterator().next();
            ObjectNode model = service.expectObjectMember("model");
            sb.append("Code generated for `")
                    .append(model.getStringMemberOrDefault("service", "?"))
                    .append("` from `")
                    .append(modelSource(model))
                    .append('`');
        } else {
            sb.append(String.format(Locale.ROOT,
                    "Code generated for %,d AWS service models",
                    comparison.headServices.size()));
            head.expectObjectMember("models")
                    .getStringMember("bom")
                    .ifPresent(bom -> sb.append(" (aws-api-models ").append(bom.getValue()).append(')'));
        }
        sb.append(" with modes `")
                .append(joinStrings(head.expectArrayMember("modes")))
                .append("`; sizes count `")
                .append(joinStrings(head.expectArrayMember("sizeModes")))
                .append("` output only. Compiled with javac ")
                .append(javac.expectStringMember("version").getValue())
                .append(" (`")
                .append(joinStrings(javac.expectArrayMember("flags")))
                .append("`). Jar size is the size of a jar holding the compiled classes and generated resources ")
                .append("at default compression.\n\n");
        if (!comparison.hasBase()) {
            sb.append("> **Warning:** ")
                    .append(baseWarning == null ? "no base report was available" : baseWarning)
                    .append(", so the PR is reported without a comparison.\n\n");
        } else {
            List<String> differences = inputDifferences(comparison.base, head);
            if (!differences.isEmpty()) {
                sb.append("> **Warning:** the base report was produced with different inputs: ")
                        .append(String.join("; ", differences))
                        .append(".\n\n");
            }
        }

        if (!comparison.regressions.isEmpty()) {
            sb.append(comparison.hasBase()
                    ? "**Failing on the PR but compiling on the base:** "
                    : "**Failing on the PR:** ")
                    .append(codeList(comparison.regressions))
                    .append("\n\n");
        }
        appendHeadline(sb, comparison, single);
        appendCompatibility(sb, comparison, single);
        if (comparison.hasBase()) {
            appendServiceMovers(sb, comparison, single);
            appendDetails(sb, comparison, single);
        }
        return sb.toString();
    }

    private static void appendHeadline(StringBuilder sb, Comparison comparison, boolean single) {
        boolean hasBase = comparison.hasBase();
        List<ObjectNode> head = comparison.measured.stream().map(comparison.headServices::get).toList();
        if (head.isEmpty()) {
            sb.append(hasBase
                    ? "No service could be measured on both base and PR.\n\n"
                    : "No service could be measured on the PR.\n\n");
            return;
        }
        List<ObjectNode> base = hasBase
                ? comparison.measured.stream().map(comparison.baseServices::get).toList()
                : List.of();
        if (!comparison.changed()) {
            sb.append("**No change** in the size of the generated code.\n\n");
        }
        sb.append(
                hasBase ? "| Metric | Base | PR | Change |\n|---|---:|---:|---:|\n" : "| Metric | PR |\n|---|---:|\n");
        if (single) {
            metricRow(sb, hasBase, "Jar size", sum(base, "jarBytes"), sum(head, "jarBytes"), true);
        } else {
            if (hasBase) {
                moverRow(sb, comparison, "Largest jar increase", comparison.largestIncrease());
                moverRow(sb, comparison, "Largest jar decrease", comparison.largestDecrease());
            }
            metricRow(sb, hasBase, "Total jar size", sum(base, "jarBytes"), sum(head, "jarBytes"), true);
            metricRow(sb, hasBase, "Average jar size", average(base, "jarBytes"), average(head, "jarBytes"), true);
            metricRow(sb, hasBase, "Median jar size (p50)", median(base, "jarBytes"), median(head, "jarBytes"), true);
        }
        metricRow(sb, hasBase, "Class bytes", sum(base, "classBytes"), sum(head, "classBytes"), true);
        metricRow(sb, hasBase, "Classes", sum(base, "classes"), sum(head, "classes"), false);
        metricRow(sb, hasBase, "Source bytes", sum(base, "sourceBytes"), sum(head, "sourceBytes"), true);
        metricRow(sb, hasBase, "Source files", sum(base, "sourceFiles"), sum(head, "sourceFiles"), false);
        sb.append('\n');
    }

    private static void moverRow(StringBuilder sb, Comparison comparison, String label, String name) {
        if (name == null) {
            sb.append("| ").append(label).append(" | | | none |\n");
            return;
        }
        metricRow(sb,
                true,
                label + ": `" + name + "`",
                jarBytes(comparison.baseServices.get(name)),
                jarBytes(comparison.headServices.get(name)),
                true);
    }

    private static void metricRow(
            StringBuilder sb,
            boolean hasBase,
            String label,
            long base,
            long head,
            boolean asBytes
    ) {
        sb.append("| ").append(label).append(" | ");
        if (hasBase) {
            sb.append(asBytes ? bytes(base) : count(base)).append(" | ");
        }
        sb.append(asBytes ? bytes(head) : count(head));
        if (hasBase) {
            sb.append(" | ").append(change(base, head, asBytes));
        }
        sb.append(" |\n");
    }

    private static void appendCompatibility(StringBuilder sb, Comparison comparison, boolean single) {
        List<String> sentences = new ArrayList<>();
        int compiled = comparison.measured.size();
        if (!single && compiled > 0) {
            if (comparison.hasBase()) {
                sentences.add(compiled == 1
                        ? "Totals cover the one service that compiles on both base and PR."
                        : "Totals cover the " + count(compiled) + " services that compile on both base and PR.");
            } else {
                sentences.add(compiled == 1 ? "1 service compiles." : count(compiled) + " services compile.");
            }
        }
        if (!comparison.fixed.isEmpty()) {
            sentences.add("Compiling on the PR but failing on the base: " + codeList(comparison.fixed) + ".");
        }
        if (!comparison.preexisting.isEmpty()) {
            sentences.add("Failing on both base and PR: " + codeList(comparison.preexisting) + ".");
        }
        if (!comparison.onlyHead.isEmpty()) {
            sentences.add("Only in the PR model set: " + codeList(comparison.onlyHead) + ".");
        }
        if (!comparison.onlyBase.isEmpty()) {
            sentences.add("Only in the base model set: " + codeList(comparison.onlyBase) + ".");
        }
        if (!comparison.known.isEmpty()) {
            sentences.add("Known failures excluded: " + codeList(comparison.known) + ".");
        }
        if (!comparison.stale.isEmpty()) {
            sentences.add("Listed as known failures but compiling, so the entries can be removed: "
                    + codeList(comparison.stale) + ".");
        }
        if (!sentences.isEmpty()) {
            sb.append(String.join(" ", sentences)).append("\n\n");
        }

        List<String> failing = new ArrayList<>(comparison.regressions);
        failing.addAll(comparison.preexisting);
        if (!failing.isEmpty()) {
            sb.append("<details>\n<summary>Errors on the PR</summary>\n\n");
            for (String name : failing) {
                ObjectNode failure = comparison.headServices.get(name).expectObjectMember("failure");
                sb.append("**`")
                        .append(name)
                        .append("`** failed during ")
                        .append(failure.getStringMemberOrDefault("stage", "?"))
                        .append(":\n\n```text\n")
                        .append(failure.getStringMemberOrDefault("message", "").replace("```", "\'\'\'"))
                        .append("\n```\n\n");
            }
            sb.append("</details>\n\n");
        }
    }

    private static void appendServiceMovers(StringBuilder sb, Comparison comparison, boolean single) {
        if (single) {
            return;
        }
        List<String> movers = comparison.measured.stream()
                .filter(name -> comparison.delta(name) != 0)
                .sorted(Comparator.comparingLong((String name) -> -Math.abs(comparison.delta(name)))
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
        if (movers.isEmpty()) {
            return;
        }
        sb.append("<details>\n<summary>Largest changes by service</summary>\n\n");
        sb.append(String.format(Locale.ROOT,
                "%,d of %,d services changed jar size.%n%n",
                movers.size(),
                comparison.measured.size()));
        sb.append("| Service | Base | PR | Change |\n|---|---:|---:|---:|\n");
        for (String name : movers.stream().limit(MAX_SERVICE_MOVERS).toList()) {
            long b = jarBytes(comparison.baseServices.get(name));
            long h = jarBytes(comparison.headServices.get(name));
            sb.append("| `")
                    .append(name)
                    .append("` | ")
                    .append(bytes(b))
                    .append(" | ")
                    .append(bytes(h))
                    .append(" | ")
                    .append(change(b, h, true))
                    .append(" |\n");
        }
        sb.append("\n</details>\n\n");
    }

    private static void appendDetails(StringBuilder sb, Comparison comparison, boolean single) {
        for (String name : comparison.measured) {
            ObjectNode h = comparison.headServices.get(name);
            ObjectNode b = comparison.baseServices.get(name);
            if (!h.containsMember("classes") || !b.containsMember("classes")) {
                continue;
            }
            String prefix = single ? "" : "`" + name + "`: ";
            String categoryTitle = single ? "By category" : prefix + "by category";
            String classTitle = single ? "Largest changes by class" : prefix + "largest changes by class";
            String namespace = h.expectObjectMember("model").getStringMemberOrDefault("namespace", "");

            Map<String, Sizes> baseCategories = sizes(b.expectObjectMember("categories"));
            Map<String, Sizes> headCategories = sizes(h.expectObjectMember("categories"));
            Set<String> categories = new TreeSet<>(baseCategories.keySet());
            categories.addAll(headCategories.keySet());
            List<String> ordered = categories.stream()
                    .sorted(Comparator.comparingLong((String c) -> -deflated(headCategories, c)))
                    .toList();
            sb.append("<details>\n<summary>").append(categoryTitle).append("</summary>\n\n");
            sb.append("| Category | Base jar bytes | PR jar bytes | Change | Base files | PR files |\n");
            sb.append("|---|---:|---:|---:|---:|---:|\n");
            for (String category : ordered) {
                Sizes bc = baseCategories.getOrDefault(category, new Sizes());
                Sizes hc = headCategories.getOrDefault(category, new Sizes());
                sb.append("| ")
                        .append(category)
                        .append(" | ")
                        .append(bytes(bc.deflated))
                        .append(" | ")
                        .append(bytes(hc.deflated))
                        .append(" | ")
                        .append(change(bc.deflated, hc.deflated, true))
                        .append(" | ")
                        .append(count(bc.files))
                        .append(" | ")
                        .append(count(hc.files))
                        .append(" |\n");
            }
            sb.append("\n</details>\n\n");

            Map<String, Sizes> baseClasses = sizes(b.expectObjectMember("classes"));
            Map<String, Sizes> headClasses = sizes(h.expectObjectMember("classes"));
            Set<String> names = new TreeSet<>(baseClasses.keySet());
            names.addAll(headClasses.keySet());
            long added = headClasses.keySet().stream().filter(n -> !baseClasses.containsKey(n)).count();
            long removed = baseClasses.keySet().stream().filter(n -> !headClasses.containsKey(n)).count();
            List<String> movers = names.stream()
                    .filter(n -> delta(baseClasses, headClasses, n) != 0)
                    .sorted(Comparator.comparingLong((String n) -> -Math.abs(delta(baseClasses, headClasses, n)))
                            .thenComparing(Comparator.naturalOrder()))
                    .limit(MAX_CLASS_MOVERS)
                    .toList();
            sb.append("<details>\n<summary>").append(classTitle).append("</summary>\n\n");
            if (movers.isEmpty()) {
                sb.append("No class changed size.\n");
            } else {
                sb.append(String.format(Locale.ROOT,
                        "%,d top-level classes added, %,d removed. Sizes are deflated jar bytes.%n%n",
                        added,
                        removed));
                sb.append("| Class | Base | PR | Change |\n|---|---:|---:|---:|\n");
                String packagePrefix = namespace.isEmpty() ? "" : namespace + ".";
                for (String className : movers) {
                    Sizes bc = baseClasses.get(className);
                    Sizes hc = headClasses.get(className);
                    String shown = className.startsWith(packagePrefix)
                            ? className.substring(packagePrefix.length())
                            : className;
                    sb.append("| `")
                            .append(shown)
                            .append("` | ")
                            .append(bc == null ? "added" : bytes(bc.deflated))
                            .append(" | ")
                            .append(hc == null ? "removed" : bytes(hc.deflated))
                            .append(" | ")
                            .append(change(deflated(baseClasses, className), deflated(headClasses, className), true))
                            .append(" |\n");
                }
            }
            sb.append("\n</details>\n\n");
        }
    }

    private static List<String> inputDifferences(ObjectNode base, ObjectNode head) {
        List<String> differences = new ArrayList<>();
        if (!base.expectArrayMember("modes").equals(head.expectArrayMember("modes"))) {
            differences.add("modes " + joinStrings(base.expectArrayMember("modes")));
        }
        if (!base.expectArrayMember("sizeModes").equals(head.expectArrayMember("sizeModes"))) {
            differences.add("size modes " + joinStrings(base.expectArrayMember("sizeModes")));
        }
        if (!base.expectObjectMember("javac").equals(head.expectObjectMember("javac"))) {
            differences.add("javac " + base.expectObjectMember("javac").getStringMemberOrDefault("version", "?"));
        }
        String baseBom = base.expectObjectMember("models").getStringMemberOrDefault("bom", "");
        String headBom = head.expectObjectMember("models").getStringMemberOrDefault("bom", "");
        if (!baseBom.isEmpty() && !headBom.isEmpty() && !baseBom.equals(headBom)) {
            differences.add("aws-api-models " + baseBom);
        }
        return differences;
    }

    private static ObjectNode totals(ObjectNode service) {
        return service.expectObjectMember("totals");
    }

    private static long jarBytes(ObjectNode service) {
        return number(totals(service), "jarBytes");
    }

    private static long sum(List<ObjectNode> services, String key) {
        long sum = 0;
        for (ObjectNode service : services) {
            sum += number(totals(service), key);
        }
        return sum;
    }

    private static long average(List<ObjectNode> services, String key) {
        return services.isEmpty() ? 0 : Math.round((double) sum(services, key) / services.size());
    }

    private static long median(List<ObjectNode> services, String key) {
        if (services.isEmpty()) {
            return 0;
        }
        long[] values = new long[services.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = number(totals(services.get(i)), key);
        }
        Arrays.sort(values);
        int middle = values.length / 2;
        return values.length % 2 == 1 ? values[middle] : Math.round((values[middle - 1] + values[middle]) / 2.0);
    }

    private static boolean sameTotals(ObjectNode base, ObjectNode head) {
        for (String key : List.of("jarBytes", "classBytes", "classes", "sourceBytes", "sourceFiles")) {
            if (number(base, key) != number(head, key)) {
                return false;
            }
        }
        return true;
    }

    private static long delta(Map<String, Sizes> base, Map<String, Sizes> head, String name) {
        return deflated(head, name) - deflated(base, name);
    }

    private static long deflated(Map<String, Sizes> sizes, String name) {
        Sizes entry = sizes.get(name);
        return entry == null ? 0 : entry.deflated;
    }

    private static Map<String, Sizes> sizes(ObjectNode node) {
        Map<String, Sizes> sizes = new TreeMap<>();
        node.getStringMap().forEach((name, value) -> {
            ObjectNode entry = value.expectObjectNode();
            sizes.put(name,
                    new Sizes(number(entry, "files"), number(entry, "classBytes"), number(entry, "jarBytes")));
        });
        return sizes;
    }

    private static long number(ObjectNode node, String member) {
        return node.getNumberMember(member).map(n -> n.getValue().longValue()).orElse(0L);
    }

    private static String joinStrings(ArrayNode node) {
        return node.getElements().stream().map(n -> n.expectStringNode().getValue()).collect(Collectors.joining(" "));
    }

    private static String codeList(List<String> names) {
        return names.stream().map(n -> "`" + n + "`").collect(Collectors.joining(", "));
    }

    private static String modelSource(ObjectNode model) {
        return model.getStringMemberOrDefault("artifact", model.getStringMemberOrDefault("file", "?"));
    }

    private static String bytes(long value) {
        if (value < 1024) {
            return value + " B";
        }
        if (value < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", value / 1024.0);
        }
        if (value < 1024L * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.2f MB", value / (1024.0 * 1024.0));
        }
        return String.format(Locale.ROOT, "%.2f GB", value / (1024.0 * 1024.0 * 1024.0));
    }

    private static String count(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String change(long base, long head, boolean asBytes) {
        long delta = head - base;
        if (delta == 0) {
            return "0";
        }
        String value = asBytes
                ? (delta < 0 ? "-" : "+") + bytes(Math.abs(delta))
                : String.format(Locale.ROOT, "%+,d", delta);
        if (base == 0) {
            return value;
        }
        return value + String.format(Locale.ROOT, " (%+.2f%%)", 100.0 * delta / base);
    }

    private static List<Path> modelFiles(String argument) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String entry : csv(argument)) {
            Path path = Path.of(entry).toAbsolutePath();
            if (Files.isDirectory(path)) {
                try (Stream<Path> children = Files.list(path)) {
                    children.filter(p -> p.toString().endsWith(".jar") || p.toString().endsWith(".json"))
                            .sorted()
                            .forEach(files::add);
                }
            } else if (Files.isRegularFile(path)) {
                files.add(path);
            } else {
                throw new IllegalArgumentException("no such model file or directory: " + path);
            }
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException("no model jars or JSON files found in " + argument);
        }
        return files;
    }

    private static Map<String, Path> keyModels(List<Path> files) {
        Map<String, Path> keyed = new TreeMap<>();
        for (Path file : files) {
            String key = modelKey(file);
            String candidate = key;
            for (int i = 2; keyed.containsKey(candidate); i++) {
                candidate = key + "-" + i;
            }
            keyed.put(candidate, file);
        }
        return keyed;
    }

    private static String modelKey(Path file) {
        String artifact = modelArtifact(file);
        if (artifact != null) {
            return artifact.split(":")[1];
        }
        String name = String.valueOf(file.getFileName());
        Matcher versioned = VERSIONED_JAR.matcher(name);
        if (versioned.find()) {
            return name.substring(0, versioned.start());
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static Model loadModel(Path path) throws IOException {
        ModelAssembler assembler = Model.assembler()
                .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .disableValidation();
        if (path.toString().endsWith(".jar")) {
            URL manifest = URI.create("jar:" + path.toUri() + "!/META-INF/smithy/manifest").toURL();
            List<URL> models = ModelDiscovery.findModels(manifest);
            if (models.isEmpty()) {
                throw new IllegalArgumentException(path + " contains no Smithy models");
            }
            models.forEach(assembler::addImport);
        } else {
            assembler.addImport(path);
        }
        return assembler.assemble().unwrap();
    }

    private static String modelArtifact(Path path) {
        if (!path.toString().endsWith(".jar")) {
            return null;
        }
        try (JarFile jar = new JarFile(path.toFile())) {
            Manifest manifest = jar.getManifest();
            String tags = manifest == null ? null : manifest.getMainAttributes().getValue("Smithy-Tags");
            if (tags != null) {
                for (String tag : tags.split(",")) {
                    String candidate = tag.trim();
                    if (candidate.split(":").length == 3) {
                        return candidate;
                    }
                }
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private static ServiceShape selectService(Model model, String shapeId) {
        if (shapeId != null) {
            return model.expectShape(ShapeId.from(shapeId), ServiceShape.class);
        }
        List<ServiceShape> services = model.getServiceShapes()
                .stream()
                .sorted(Comparator.comparing(s -> s.getId().toString()))
                .toList();
        if (services.isEmpty()) {
            throw new IllegalArgumentException("the model contains no service shape");
        }
        if (services.size() > 1) {
            System.err.println("the model contains several services; measuring " + services.get(0).getId()
                    + " (use --service to pick another)");
        }
        return services.get(0);
    }

    private static String sanitize(String name) {
        String sanitized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return sanitized.isEmpty() ? "service" : sanitized;
    }

    private static String qualifiedName(String path, String content) {
        String simple = path.substring(path.lastIndexOf('/') + 1, path.length() - ".java".length());
        Matcher matcher = PACKAGE.matcher(content);
        return matcher.find() ? matcher.group(1) + "." + simple : simple;
    }

    private static String resourcePath(String path) {
        int index = path.indexOf("/resources/");
        String relative = index >= 0 ? path.substring(index + "/resources/".length()) : path;
        return relative.startsWith("/") ? relative.substring(1) : relative;
    }

    private static List<String> csv(String value) {
        return Stream.of(value.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static final class CompilationFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final transient String errors;

        CompilationFailure(String errors, Path dump) {
            super("compilation of the generated code failed; sources written to " + dump);
            this.errors = errors;
        }
    }

    private static Map<String, byte[]> compile(Map<String, String> sources, Path dump) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<JavaFileObject> units = new ArrayList<>(sources.size());
        sources.forEach((name, content) -> units.add(new SourceFile(name, content)));
        List<String> options = new ArrayList<>(JAVAC_FLAGS);
        options.add("-classpath");
        options.add(System.getProperty("java.class.path"));
        try (StandardJavaFileManager standard = compiler.getStandardFileManager(diagnostics,
                null,
                StandardCharsets.UTF_8)) {
            MemoryFileManager fileManager = new MemoryFileManager(standard);
            boolean ok = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            if (!ok) {
                String errors = diagnostics.getDiagnostics()
                        .stream()
                        .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                        .map(Object::toString)
                        .collect(Collectors.joining("\n"));
                Path dir = dump != null ? dump : Files.createTempDirectory("codegen-size-");
                dumpSources(dir, sources, Map.of());
                writeString(dir.resolve("ERRORS.txt"), errors);
                throw new CompilationFailure(errors, dir);
            }
            return fileManager.classes;
        }
    }

    private static final class SourceFile extends SimpleJavaFileObject {
        private final String content;

        SourceFile(String qualifiedName, String content) {
            super(URI.create("string:///" + qualifiedName.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
    }

    private static final class MemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, byte[]> classes = new TreeMap<>();

        MemoryFileManager(StandardJavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                JavaFileManager.Location location,
                String className,
                JavaFileObject.Kind kind,
                FileObject sibling
        ) {
            return new SimpleJavaFileObject(URI.create("mem:///" + className.replace('.', '/') + kind.extension),
                    kind) {
                @Override
                public OutputStream openOutputStream() {
                    return new ByteArrayOutputStream() {
                        @Override
                        public void close() {
                            classes.put(className, toByteArray());
                        }
                    };
                }
            };
        }
    }

    private static String topLevel(String binaryName) {
        int simpleStart = binaryName.lastIndexOf('.') + 1;
        // Preserve a leading '$' in top-level names such as $HiddenEnum_1234.
        int nested = binaryName.indexOf('$', simpleStart + 1);
        return nested < 0 ? binaryName : binaryName.substring(0, nested);
    }

    private static String categorize(String qualifiedName, String source) {
        String simple = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        if (simple.startsWith("Schemas") || simple.equals("GeneratedSchemaIndex")) {
            return "schemas";
        }
        if (simple.startsWith("SharedSerde")) {
            return "serde";
        }
        if (simple.startsWith("$Hidden")) {
            return "enum";
        }
        Declaration declaration = Declaration.find(simple, source);
        String header = declaration.header;
        if (header.contains("SmithyEnum") || header.contains("SmithyIntEnum")) {
            return "enum";
        }
        if (OPERATION.matcher(header).find()) {
            return "operation";
        }
        if (header.contains("SerializableStruct")) {
            return declaration.kind.equals("interface") ? "union" : "structure";
        }
        if (header.contains("SchemaIndex")) {
            return "schemas";
        }
        if (EXCEPTION.matcher(header).find()) {
            return "error";
        }
        String pkg = qualifiedName.substring(0, Math.max(0, qualifiedName.lastIndexOf('.')));
        if (header.contains("ApiService") || header.contains("Client") || pkg.endsWith(".client")) {
            return "client";
        }
        return "other";
    }

    private record Declaration(String kind, String header) {
        static Declaration find(String simpleName, String source) {
            Matcher matcher = DECLARATION.matcher(source);
            while (matcher.find()) {
                if (matcher.group(2).equals(simpleName)) {
                    return new Declaration(matcher.group(1), matcher.group(0));
                }
            }
            return new Declaration("", "");
        }
    }

    private static long addEntry(JarOutputStream jar, String name, byte[] bytes) throws IOException {
        JarEntry entry = new JarEntry(name);
        entry.setTimeLocal(ENTRY_TIME);
        jar.putNextEntry(entry);
        jar.write(bytes);
        jar.closeEntry();
        return entry.getCompressedSize();
    }

    private static final class Sizes {
        private long files;
        private long bytes;
        private long deflated;

        Sizes() {}

        Sizes(long files, long bytes, long deflated) {
            this.files = files;
            this.bytes = bytes;
            this.deflated = deflated;
        }

        void add(long entryBytes, long entryDeflated) {
            files++;
            bytes += entryBytes;
            deflated += entryDeflated;
        }
    }

    private static void add(Map<String, Sizes> sizes, String key, long bytes, long deflated) {
        sizes.computeIfAbsent(key, k -> new Sizes()).add(bytes, deflated);
    }

    private static ObjectNode sizesNode(Map<String, Sizes> sizes, Map<String, String> categories) {
        ObjectNode.Builder builder = Node.objectNodeBuilder();
        sizes.forEach((name, sum) -> {
            ObjectNode.Builder entry = Node.objectNodeBuilder();
            if (categories != null) {
                entry.withMember("category", categories.get(name));
            }
            entry.withMember("files", sum.files)
                    .withMember("classBytes", sum.bytes)
                    .withMember("jarBytes", sum.deflated);
            builder.withMember(name, entry.build());
        });
        return builder.build();
    }

    private static void dumpSources(Path dir, Map<String, String> sources, Map<String, byte[]> resources)
            throws IOException {
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            write(dir.resolve("java").resolve(entry.getKey().replace('.', '/') + ".java"),
                    entry.getValue().getBytes(StandardCharsets.UTF_8));
        }
        for (Map.Entry<String, byte[]> entry : resources.entrySet()) {
            write(dir.resolve("resources").resolve(entry.getKey()), entry.getValue());
        }
    }

    private static void dumpClasses(Path dir, Map<String, byte[]> classes, byte[] jar) throws IOException {
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            write(dir.resolve("classes").resolve(entry.getKey().replace('.', '/') + ".class"), entry.getValue());
        }
        write(dir.resolve("generated.jar"), jar);
    }

    private static void write(Path file, byte[] bytes) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(file, bytes);
    }

    private static void writeString(Path file, String content) throws IOException {
        write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private static final class Options {
        private final String command;
        private final List<String> positional = new ArrayList<>();
        private final Map<String, String> named = new HashMap<>();

        private Options(String command) {
            this.command = command;
        }

        static Options parse(String[] args) {
            if (args.length == 0) {
                throw new IllegalArgumentException("missing command");
            }
            Options options = new Options(args[0]);
            for (int i = 1; i < args.length; i++) {
                if (args[i].startsWith("--")) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("missing value for " + args[i]);
                    }
                    options.named.put(args[i].substring(2), args[++i]);
                } else {
                    options.positional.add(args[i]);
                }
            }
            return options;
        }

        String require(String name) {
            String value = named.get(name);
            if (value == null) {
                throw new IllegalArgumentException("--" + name + " is required");
            }
            return value;
        }

        Path path(String name) {
            String value = named.get(name);
            return value == null ? null : Path.of(value).toAbsolutePath();
        }

        Path requirePath(String name) {
            Path path = path(name);
            if (path == null) {
                throw new IllegalArgumentException("--" + name + " is required");
            }
            return path;
        }
    }
}
