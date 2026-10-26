/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
 * Measures Java source, class, and jar sizes for a Smithy service.
 *
 * <p>The {@code report} command writes JSON totals, category totals, and sizes for each class.
 * With {@code --baseline}, it also writes a Markdown comparison.
 * The {@code compare} command writes a Markdown comparison of two existing JSON reports.
 *
 * <p>This tool uses the public plugin API to run against checkouts that do not contain it.
 * See {@code measure.sh}.
 *
 * <pre>
 * java -cp &lt;codegen classpath&gt; CodegenSize.java report --model ec2.jar --out pr.json \
 *     [--modes client,server] [--service com.amazonaws.ec2#AmazonEC2] \
 *     [--baseline base.json] [--markdown report.md] [--dump build/codegen-size]
 * java -cp &lt;classpath with smithy-model&gt; CodegenSize.java compare base.json pr.json [--markdown report.md]
 * </pre>
 */
public final class CodegenSize {

    private static final int FORMAT = 1;

    /**
     * Use a fixed local time within the DOS range to avoid timezone-dependent ZIP fields.
     */
    private static final LocalDateTime ENTRY_TIME = LocalDateTime.of(1980, 2, 1, 0, 0);

    private static final List<String> JAVAC_FLAGS = List.of("--release", "21", "-g", "-proc:none", "-nowarn");

    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern DECLARATION = Pattern.compile(
            "^(?:public\\s+)?(?:(?:final|abstract|sealed|non-sealed|static)\\s+)*(class|interface|record|enum)\\s+(\\w+)\\b[^{]*",
            Pattern.MULTILINE);
    private static final Pattern OPERATION = Pattern.compile("\\b(?:extends|implements)\\s+(?:[\\w.]*\\.)?(?:Abstract)?ApiOperation\\b");
    private static final Pattern EXCEPTION = Pattern.compile("\\bextends\\s+[\\w.]*Exception\\b");

    private static final String USAGE = """
            usage:
              CodegenSize.java report --model <model.jar|model.json> --out <report.json>
                  [--modes client,server] [--service <shape id>] [--baseline <base.json>]
                  [--markdown <report.md>] [--dump <dir>]
              CodegenSize.java compare <base.json> <head.json> [--markdown <report.md>]""";

    private CodegenSize() {}

    public static void main(String[] args) throws IOException {
        try {
            Options options = Options.parse(args);
            switch (options.command) {
                case "report" -> report(options);
                case "compare" -> compare(options);
                default -> throw new IllegalArgumentException("unknown command: " + options.command);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println(USAGE);
            System.exit(2);
        }
    }

    private static void report(Options options) throws IOException {
        Path modelPath = options.requirePath("model");
        Path out = options.requirePath("out");
        Path dump = options.path("dump");
        List<String> modes = List.of(options.named.getOrDefault("modes", "client").split(","));

        Model model = loadModel(modelPath);
        ServiceShape service = selectService(model, options.named.get("service"));
        String namespace = "gen." + sanitize(service.getId().getName());

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
        System.err.printf(Locale.ROOT, "codegen: %,d Java files and %,d resources for %s in %,d ms%n",
                sources.size(), resources.size(), service.getId(), codegenMillis);
        if (dump != null) {
            dumpSources(dump, sources, resources);
        }

        start = System.nanoTime();
        Map<String, byte[]> classes = compile(sources, dump);
        long compileMillis = (System.nanoTime() - start) / 1_000_000;
        System.err.printf(Locale.ROOT, "javac: %,d classes in %,d ms%n", classes.size(), compileMillis);

        Map<String, String> categories = new HashMap<>();
        sources.forEach((name, content) -> categories.put(name, categorize(name, content)));

        // Size arrays contain file count, uncompressed bytes, and deflated bytes, in that order.
        Map<String, long[]> byCategory = new TreeMap<>();
        Map<String, long[]> byClass = new TreeMap<>();
        Map<String, String> classCategory = new TreeMap<>();
        Manifest jarManifest = new Manifest();
        jarManifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        ByteArrayOutputStream jarBuffer = new ByteArrayOutputStream(32 << 20);
        try (JarOutputStream jar = new JarOutputStream(jarBuffer, jarManifest)) {
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                String top = topLevel(entry.getKey());
                String category = categories.getOrDefault(top, "other");
                long deflated = addEntry(jar, entry.getKey().replace('.', '/') + ".class", entry.getValue());
                add(byCategory, category, entry.getValue().length, deflated);
                add(byClass, top, entry.getValue().length, deflated);
                classCategory.put(top, category);
            }
            for (Map.Entry<String, byte[]> entry : resources.entrySet()) {
                long deflated = addEntry(jar, entry.getKey(), entry.getValue());
                add(byCategory, "resources", entry.getValue().length, deflated);
                add(byClass, entry.getKey(), entry.getValue().length, deflated);
                classCategory.put(entry.getKey(), "resources");
            }
        }
        if (dump != null) {
            dumpClasses(dump, classes, jarBuffer.toByteArray());
        }

        long sourceBytes = 0;
        for (String content : sources.values()) {
            sourceBytes += content.getBytes(StandardCharsets.UTF_8).length;
        }
        long classBytes = 0;
        for (byte[] bytes : classes.values()) {
            classBytes += bytes.length;
        }

        ObjectNode.Builder modelNode = Node.objectNodeBuilder()
                .withMember("file", String.valueOf(modelPath.getFileName()))
                .withMember("service", service.getId().toString())
                .withMember("namespace", namespace)
                .withMember("modes", ArrayNode.fromStrings(modes));
        String artifact = modelArtifact(modelPath);
        if (artifact != null) {
            modelNode.withMember("artifact", artifact);
        }
        ObjectNode report = Node.objectNodeBuilder()
                .withMember("tool", "codegen-size")
                .withMember("format", FORMAT)
                .withMember("model", modelNode.build())
                .withMember("javac", Node.objectNodeBuilder()
                        .withMember("version", Runtime.version().toString())
                        .withMember("flags", ArrayNode.fromStrings(JAVAC_FLAGS))
                        .build())
                .withMember("timings", Node.objectNodeBuilder()
                        .withMember("codegenMillis", codegenMillis)
                        .withMember("compileMillis", compileMillis)
                        .build())
                .withMember("totals", Node.objectNodeBuilder()
                        .withMember("sourceFiles", sources.size())
                        .withMember("sourceBytes", sourceBytes)
                        .withMember("resourceFiles", resources.size())
                        .withMember("classes", classes.size())
                        .withMember("classBytes", classBytes)
                        .withMember("jarBytes", jarBuffer.size())
                        .build())
                .withMember("categories", sizesNode(byCategory, null))
                .withMember("classes", sizesNode(byClass, classCategory))
                .build();
        Files.createDirectories(out.getParent());
        Files.writeString(out, Node.prettyPrintJson(report));
        System.err.printf(Locale.ROOT, "jar %,d B, class bytes %,d B, %,d classes, %,d source files (%,d B)%n",
                jarBuffer.size(), classBytes, classes.size(), sources.size(), sourceBytes);

        Path baseline = options.path("baseline");
        if (baseline != null) {
            emitMarkdown(markdown(readReport(baseline), report), options.path("markdown"));
        }
    }

    private static void compare(Options options) throws IOException {
        if (options.positional.size() != 2) {
            throw new IllegalArgumentException("compare takes exactly two reports: <base.json> <head.json>");
        }
        ObjectNode base = readReport(Path.of(options.positional.get(0)));
        ObjectNode head = readReport(Path.of(options.positional.get(1)));
        emitMarkdown(markdown(base, head), options.path("markdown"));
    }

    private static void emitMarkdown(String markdown, Path target) throws IOException {
        if (target != null) {
            Files.createDirectories(target.getParent());
            Files.writeString(target, markdown);
        }
        System.out.print(markdown);
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
        } catch (IOException e) {
        }
        return null;
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

    private static Map<String, byte[]> compile(Map<String, String> sources, Path dump) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<JavaFileObject> units = new ArrayList<>(sources.size());
        sources.forEach((name, content) -> units.add(new SourceFile(name, content)));
        List<String> options = new ArrayList<>(JAVAC_FLAGS);
        options.add("-classpath");
        options.add(System.getProperty("java.class.path"));
        try (StandardJavaFileManager standard = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
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
                Files.writeString(dir.resolve("ERRORS.txt"), errors);
                System.err.println(errors);
                throw new IllegalStateException("compilation of the generated code failed; sources written to " + dir);
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
            return new SimpleJavaFileObject(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {
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

    private static void add(Map<String, long[]> sizes, String key, long bytes, long deflated) {
        long[] sum = sizes.computeIfAbsent(key, k -> new long[3]);
        sum[0]++;
        sum[1] += bytes;
        sum[2] += deflated;
    }

    private static ObjectNode sizesNode(Map<String, long[]> sizes, Map<String, String> categories) {
        ObjectNode.Builder builder = Node.objectNodeBuilder();
        sizes.forEach((name, sum) -> {
            ObjectNode.Builder entry = Node.objectNodeBuilder();
            if (categories != null) {
                entry.withMember("category", categories.get(name));
            }
            entry.withMember("files", sum[0]).withMember("classBytes", sum[1]).withMember("jarBytes", sum[2]);
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
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private static ObjectNode readReport(Path path) throws IOException {
        ObjectNode report = Node.parse(Files.readString(path)).expectObjectNode();
        int format = report.getNumberMember("format").map(n -> n.getValue().intValue()).orElse(0);
        if (format != FORMAT) {
            throw new IllegalArgumentException(path + " has report format " + format + ", expected " + FORMAT);
        }
        return report;
    }

    private static String markdown(ObjectNode base, ObjectNode head) {
        StringBuilder sb = new StringBuilder();
        ObjectNode model = head.expectObjectMember("model");
        ObjectNode javac = head.expectObjectMember("javac");
        String namespace = model.getStringMemberOrDefault("namespace", "");
        sb.append("## Codegen size report\n\n");
        sb.append("Code generated for `").append(model.expectStringMember("service").getValue())
                .append("` from `").append(modelSource(model))
                .append("` with modes `").append(joinStrings(model.expectArrayMember("modes")))
                .append("`, compiled with javac ").append(javac.expectStringMember("version").getValue())
                .append(" (`").append(joinStrings(javac.expectArrayMember("flags")))
                .append("`). Jar size is the size of a jar holding the compiled classes and generated resources ")
                .append("at default compression.\n\n");
        if (!base.expectObjectMember("model").equals(model)) {
            sb.append("> **Warning:** the base report was produced from different inputs (")
                    .append(describeModel(base.expectObjectMember("model"))).append(").\n\n");
        }

        ObjectNode baseTotals = base.expectObjectMember("totals");
        ObjectNode headTotals = head.expectObjectMember("totals");
        if (sameTotals(baseTotals, headTotals)) {
            sb.append("**No change** in the size of the generated code.\n\n");
        }
        sb.append("| Metric | Base | PR | Change |\n|---|---:|---:|---:|\n");
        totalRow(sb, "Jar size", baseTotals, headTotals, "jarBytes", true);
        totalRow(sb, "Class bytes", baseTotals, headTotals, "classBytes", true);
        totalRow(sb, "Classes", baseTotals, headTotals, "classes", false);
        totalRow(sb, "Source bytes", baseTotals, headTotals, "sourceBytes", true);
        totalRow(sb, "Source files", baseTotals, headTotals, "sourceFiles", false);

        Map<String, long[]> baseCategories = sizes(base.expectObjectMember("categories"));
        Map<String, long[]> headCategories = sizes(head.expectObjectMember("categories"));
        Set<String> categories = new TreeSet<>(baseCategories.keySet());
        categories.addAll(headCategories.keySet());
        List<String> ordered = categories.stream()
                .sorted(Comparator.comparingLong((String c) -> -headCategories.getOrDefault(c, new long[3])[2]))
                .toList();
        sb.append("\n<details>\n<summary>By category</summary>\n\n");
        sb.append("| Category | Base jar bytes | PR jar bytes | Change | Base files | PR files |\n");
        sb.append("|---|---:|---:|---:|---:|---:|\n");
        for (String category : ordered) {
            long[] b = baseCategories.getOrDefault(category, new long[3]);
            long[] h = headCategories.getOrDefault(category, new long[3]);
            sb.append("| ").append(category)
                    .append(" | ").append(bytes(b[2]))
                    .append(" | ").append(bytes(h[2]))
                    .append(" | ").append(change(b[2], h[2], true))
                    .append(" | ").append(count(b[0]))
                    .append(" | ").append(count(h[0]))
                    .append(" |\n");
        }
        sb.append("\n</details>\n");

        Map<String, long[]> baseClasses = sizes(base.expectObjectMember("classes"));
        Map<String, long[]> headClasses = sizes(head.expectObjectMember("classes"));
        Set<String> names = new TreeSet<>(baseClasses.keySet());
        names.addAll(headClasses.keySet());
        long added = headClasses.keySet().stream().filter(n -> !baseClasses.containsKey(n)).count();
        long removed = baseClasses.keySet().stream().filter(n -> !headClasses.containsKey(n)).count();
        List<String> movers = names.stream()
                .filter(n -> delta(baseClasses, headClasses, n) != 0)
                .sorted(Comparator.comparingLong((String n) -> -Math.abs(delta(baseClasses, headClasses, n)))
                        .thenComparing(Comparator.naturalOrder()))
                .limit(15)
                .toList();
        sb.append("\n<details>\n<summary>Largest changes by class</summary>\n\n");
        if (movers.isEmpty()) {
            sb.append("No class changed size.\n");
        } else {
            sb.append(String.format(Locale.ROOT, "%,d top-level classes added, %,d removed. Sizes are deflated jar bytes.%n%n",
                    added, removed));
            sb.append("| Class | Base | PR | Change |\n|---|---:|---:|---:|\n");
            String prefix = namespace.isEmpty() ? "" : namespace + ".";
            for (String name : movers) {
                long[] b = baseClasses.get(name);
                long[] h = headClasses.get(name);
                String shown = name.startsWith(prefix) ? name.substring(prefix.length()) : name;
                sb.append("| `").append(shown).append("` | ")
                        .append(b == null ? "added" : bytes(b[2])).append(" | ")
                        .append(h == null ? "removed" : bytes(h[2])).append(" | ")
                        .append(change(b == null ? 0 : b[2], h == null ? 0 : h[2], true))
                        .append(" |\n");
            }
        }
        sb.append("\n</details>\n");
        return sb.toString();
    }

    private static boolean sameTotals(ObjectNode base, ObjectNode head) {
        for (String key : List.of("jarBytes", "classBytes", "classes", "sourceBytes", "sourceFiles")) {
            if (number(base, key) != number(head, key)) {
                return false;
            }
        }
        return true;
    }

    private static void totalRow(
            StringBuilder sb,
            String label,
            ObjectNode base,
            ObjectNode head,
            String key,
            boolean asBytes
    ) {
        long b = number(base, key);
        long h = number(head, key);
        sb.append("| ").append(label)
                .append(" | ").append(asBytes ? bytes(b) : count(b))
                .append(" | ").append(asBytes ? bytes(h) : count(h))
                .append(" | ").append(change(b, h, asBytes))
                .append(" |\n");
    }

    private static long delta(Map<String, long[]> base, Map<String, long[]> head, String name) {
        long b = base.containsKey(name) ? base.get(name)[2] : 0;
        long h = head.containsKey(name) ? head.get(name)[2] : 0;
        return h - b;
    }

    private static Map<String, long[]> sizes(ObjectNode node) {
        Map<String, long[]> sizes = new TreeMap<>();
        node.getStringMap().forEach((name, value) -> {
            ObjectNode entry = value.expectObjectNode();
            sizes.put(name, new long[] {number(entry, "files"), number(entry, "classBytes"), number(entry, "jarBytes")});
        });
        return sizes;
    }

    private static long number(ObjectNode node, String member) {
        return node.getNumberMember(member).map(n -> n.getValue().longValue()).orElse(0L);
    }

    private static String joinStrings(ArrayNode node) {
        return node.getElements().stream().map(n -> n.expectStringNode().getValue()).collect(Collectors.joining(" "));
    }

    private static String modelSource(ObjectNode model) {
        return model.getStringMemberOrDefault("artifact", model.getStringMemberOrDefault("file", "?"));
    }

    private static String describeModel(ObjectNode model) {
        return model.getStringMemberOrDefault("service", "?") + " from " + modelSource(model)
                + " with modes " + joinStrings(model.getArrayMember("modes").orElse(ArrayNode.fromStrings()));
    }

    private static String bytes(long value) {
        if (value < 1024) {
            return value + " B";
        }
        if (value < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", value / 1024.0);
        }
        return String.format(Locale.ROOT, "%.2f MB", value / (1024.0 * 1024.0));
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
