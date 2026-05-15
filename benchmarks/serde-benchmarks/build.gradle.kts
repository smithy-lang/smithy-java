plugins {
    id("smithy-java.java-conventions")
    alias(libs.plugins.shadow)
    alias(libs.plugins.jmh)
    id("software.amazon.smithy.gradle.smithy-base")
}

description = "Serde (serialization/deserialization) microbenchmarks for smithy-java codecs."

// Not published. No `smithy-java.module-conventions`, no publishing, no BOM entry.

// Benchmarks intentionally target JDK 25 (the rest of smithy-java targets 21).
// Performance measurements should reflect the latest JIT/runtime improvements
// available to consumers; the runtime deps were compiled for 21 but run fine
// on a newer JVM.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

dependencies {
    // Smithy traits needed at build time (smithy-build) and at runtime (when
    // BenchmarkTestCases loads the model). The jmh configuration extends
    // implementation, so these are available to both.
    implementation(libs.smithy.model)
    implementation(libs.smithy.aws.traits)
    implementation(libs.smithy.protocol.traits)
    implementation(libs.smithy.protocol.test.traits)
    implementation(libs.smithy.utils)

    // The Smithy Java codegen plugin produces typed shape classes plus
    // ApiOperation classes per service (see `smithy-build.json`). The
    // client-core dep is required because the generated client classes
    // reference it.
    smithyBuild(project(":codegen:codegen-plugin"))
    smithyBuild(project(":client:client-core"))

    // smithy-java runtime stack — what we are benchmarking.
    jmh(project(":core"))
    jmh(project(":io"))
    jmh(project(":logging"))
    jmh(project(":codecs:json-codec", configuration = "shadow"))
    jmh(project(":codecs:cbor-codec"))
    jmh(project(":codecs:xml-codec"))
    jmh(project(":codecs:codec-codegen"))

    // Client protocols — every benchmark drives the corresponding
    // ClientProtocol#createRequest / #deserializeResponse, mirroring the
    // reference implementation's protocol-level entry points.
    jmh(project(":client:client-core"))
    jmh(project(":client:client-http"))
    jmh(project(":client:client-http-binding"))
    jmh(project(":client:client-rpcv2-cbor"))
    jmh(project(":aws:client:aws-client-awsjson"))
    jmh(project(":aws:client:aws-client-awsquery"))
    jmh(project(":aws:client:aws-client-restjson"))
    jmh(project(":aws:client:aws-client-restxml"))

    // Protocol test document for converting test case params into typed shapes.
    jmh(project(":protocol-test-harness"))
}

// Smithy benchmark model files (tagged @httpRequestTests / @httpResponseTests
// with the `serde-benchmark` tag).
//
// At runtime BenchmarkContext loads the model via
// `Model.assembler().discoverModels()`, which walks `META-INF/smithy/manifest`
// resources on the classpath.
abstract class GenerateSmithyManifest : DefaultTask() {
    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @org.gradle.api.tasks.TaskAction
    fun run() {
        val outRoot = outputDir.get().asFile
        val smithyDir = outRoot.resolve("META-INF/smithy")
        smithyDir.deleteRecursively()
        smithyDir.mkdirs()

        val srcRoot = sourceDir.get().asFile
        val entries = mutableListOf<String>()
        srcRoot.walkTopDown().filter { it.isFile && it.extension == "smithy" }.forEach { f ->
            val rel = f.relativeTo(srcRoot).invariantSeparatorsPath
            f.copyTo(smithyDir.resolve(rel), overwrite = true)
            entries += rel
        }
        entries.sort()

        smithyDir.resolve("manifest").writeText(entries.joinToString("\n", postfix = "\n"))
    }
}

val generateSmithyManifest by tasks.registering(GenerateSmithyManifest::class) {
    group = "build"
    description = "Copy benchmark .smithy files to META-INF/smithy/ and generate the model manifest."
    sourceDir.set(layout.projectDirectory.dir("model"))
    outputDir.set(layout.buildDirectory.dir("generated-resources/smithy-manifest"))
}

// Wire each codegen projection's output into the jmh source set. There are
// five projections (one per service); each emits Java source under
// `build/smithyprojections/<project>/<projection>/java-codegen/java` into a
// distinct package so same-named typed shapes from different protocols don't
// collide.
val codegenProjections =
    listOf(
        "aws-json-rpc-1-0-client",
        "aws-query-client",
        "rest-json-client",
        "rest-xml-client",
        "rpc-v2-cbor-client",
    )

afterEvaluate {
    val projectionPaths =
        codegenProjections.map { name ->
            smithy.getPluginProjectionPath(name, "java-codegen").get()
        }
    sourceSets.named("jmh") {
        java {
            projectionPaths.forEach { srcDir("$it/java") }
        }
        resources {
            projectionPaths.forEach { srcDir("$it/resources") }
            srcDir(generateSmithyManifest)
        }
    }
}

tasks.named("processJmhResources") {
    dependsOn(generateSmithyManifest)
    dependsOn("smithyBuild")
}

// Multiple codegen projections each register META-INF/services/...SchemaIndex
// (and other SPI files). Both should be on the runtime classpath; tell
// processJmhResources to keep both entries by concatenating.
tasks.named<Copy>("processJmhResources") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.named("compileJmhJava") {
    dependsOn("smithyBuild")
}

// All JMH parameters are configured here (single source of truth).
// Per-class annotations (@Warmup, @Measurement, @Fork, etc.) are not used.
//
// Fast mode:  -Pjmh.fast       (1 warmup, 3 measurement, 1 fork, 5s each)
// Profilers:  -Pjmh.profilers=gc,stack   (comma-separated JMH profiler names)
// Filter:     -Pjmh.includes=RpcV2CborSerializeBenchmark.serialize
// Test case:  -Pjmh.testCaseId=rpcv2Cbor_PutItemRequest_BinaryData_S
val fast = providers.gradleProperty("jmh.fast").isPresent
jmh {
    benchmarkMode.addAll("sample")
    timeUnit = "ns"
    warmupIterations = if (fast) 1 else 5
    warmup = if (fast) "5s" else "2s"
    iterations = if (fast) 3 else 10
    timeOnIteration = if (fast) "5s" else "5s"
    fork = 1
    jvmArgs.addAll(
        "-Xms1g",
        "-Xmx1g",
        "-XX:+UseG1GC",
        "-XX:+AlwaysPreTouch",
        "-Dsmithy-java.json-provider=smithy",
    )
    includes.addAll(
        providers
            .gradleProperty("jmh.includes")
            .map { listOf(it) }
            .orElse(emptyList()),
    )
    profilers.addAll(
        providers
            .gradleProperty("jmh.profilers")
            .map { it.split(",") }
            .orElse(emptyList()),
    )
    providers.gradleProperty("jmh.testCaseId").orNull?.let { id ->
        val prop = objects.listProperty(String::class.java)
        prop.add(id)
        benchmarkParameters.put("testCaseId", prop)
    }
    resultFormat = "json"
    resultsFile = layout.buildDirectory.file("results/jmh/results.json")
}

// With shadow applied before jmh, jmhJar is a ShadowJar. Configure
// mergeServiceFiles() so duplicate META-INF/services/ entries from
// multiple codegen projections are concatenated rather than overwritten.
tasks.jmhJar {
    mergeServiceFiles()
    append("META-INF/smithy/manifest")
}

// Run the cross-language result converter. Reads the JMH JSON written by the
// `jmh` task and writes a JSON + Markdown pair conforming to the shared
// benchmark output schema. OS, instance type (via EC2 IMDS), and smithy-java
// version are detected at runtime.
//
//   ./gradlew :benchmarks:serde-benchmarks:convertJmhResults
//
// Optional properties:
//   -PoutputPrefix=<path>   prefix for the output files (default: build/results/jmh/output)
tasks.register<JavaExec>("convertJmhResults") {
    group = "benchmarks"
    description = "Convert JMH JSON output to the cross-language serde benchmark schema."

    mainClass.set("software.amazon.smithy.java.benchmarks.serde.JmhResultConverter")
    classpath = sourceSets["jmh"].runtimeClasspath

    val inputFile = layout.buildDirectory.file("results/jmh/results.json")
    val defaultPrefix = layout.buildDirectory.file("results/jmh/output").map { it.asFile.absolutePath }
    val outputPrefix = providers.gradleProperty("outputPrefix").orElse(defaultPrefix)

    inputs.file(inputFile)
    outputs.files(
        outputPrefix.map { file("$it.json") },
        outputPrefix.map { file("$it.md") },
    )

    args(
        "--input",
        inputFile.get().asFile.absolutePath,
        "--output-prefix",
        outputPrefix.get(),
    )
}

tasks.named("jmh") {
    finalizedBy("convertJmhResults")
}
