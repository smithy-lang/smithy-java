plugins {
    id("smithy-java.java-conventions")
    alias(libs.plugins.jmh)
    alias(libs.plugins.shadow)
    id("software.amazon.smithy.gradle.smithy-base")
}

description = "Serde (serialization/deserialization) microbenchmarks for smithy-java codecs."

// Not published. No `smithy-java.module-conventions`, no publishing, no BOM entry.

dependencies {
    // Smithy traits needed at build time (smithy-build) AND at runtime (when
    // BenchmarkContext loads the model).
    implementation(libs.smithy.model)
    implementation(libs.smithy.aws.traits)
    implementation(libs.smithy.protocol.traits)
    implementation(libs.smithy.protocol.test.traits)
    implementation(libs.smithy.utils)

    jmh(libs.smithy.model)
    jmh(libs.smithy.aws.traits)
    jmh(libs.smithy.protocol.traits)
    jmh(libs.smithy.protocol.test.traits)
    jmh(libs.smithy.utils)

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
}

// Smithy benchmark model files (tagged @httpRequestTests / @httpResponseTests
// with the `serde-benchmark` tag).
//
// At runtime BenchmarkContext loads the model via
// `Model.assembler().discoverModels()`, which walks `META-INF/smithy/manifest`
// resources on the classpath.
abstract class GenerateSmithyManifest : DefaultTask() {
    @get:org.gradle.api.tasks.InputDirectory
    abstract val sourceDir: org.gradle.api.file.DirectoryProperty

    @get:org.gradle.api.tasks.OutputDirectory
    abstract val outputDir: org.gradle.api.file.DirectoryProperty

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
val codegenProjections = listOf(
    "aws-json-rpc-1-0-client",
    "aws-query-client",
    "rest-json-client",
    "rest-xml-client",
    "rpc-v2-cbor-client",
)

afterEvaluate {
    val projectionPaths = codegenProjections.map { name ->
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

jmh {
    // Per-benchmark @Warmup / @Measurement / @Fork annotations on each class
    // are authoritative. These extension defaults apply only when annotations
    // are absent.
    warmupIterations = 5
    iterations = 10
    fork = 0
    // Select the native smithy-java JSON provider (rather than the default
    // Jackson-backed provider, which has higher ServiceLoader priority).
    // The system property is read once during static initialization of
    // `JsonSettings`, so it must be set before the codec class loads.
    jvmArgs.addAll("-Dsmithy-java.json-provider=smithy")
    includes.addAll(
        providers
            .gradleProperty("jmh.includes")
            .map { listOf(it) }
            .orElse(emptyList()),
    )
    // Emit JSON output so it can be picked up by the `convertJmhResults` task.
    resultFormat = "json"
    resultsFile = layout.buildDirectory.file("results/jmh/results.json")
}

// The me.champeau.jmh plugin auto-integrates with com.gradleup.shadow when
// both plugins are present: the `jmh` task uses a shadow-style classpath so
// duplicate META-INF/services/ entries (one per upstream module) are merged
// rather than overwritten. Without this, multiple TraitService SPI files
// collide and only one wins.
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    mergeServiceFiles()
}

// Run the cross-language result converter. Reads the JMH JSON written by the
// `jmh` task and writes a JSON + Markdown pair conforming to the shared
// benchmark output schema.
//
//   ./gradlew :benchmarks:serde-benchmarks:convertJmhResults
//
// Optional properties:
//   -Pinstance=m7i.xlarge   instance label written to metadata.instance
//   -Pos="<descriptor>"     OS label written to metadata.os
//   -PoutputPrefix=<path>   prefix for the output files (default: build/results/jmh/output)
tasks.register<JavaExec>("convertJmhResults") {
    group = "benchmarks"
    description = "Convert JMH JSON output to the cross-language serde benchmark schema."

    mainClass.set("software.amazon.smithy.java.benchmarks.serde.JmhResultConverter")
    classpath = sourceSets["jmh"].runtimeClasspath

    val inputFile = layout.buildDirectory.file("results/jmh/results.json")
    val defaultPrefix = layout.buildDirectory.file("results/jmh/output").map { it.asFile.absolutePath }
    val outputPrefix = providers.gradleProperty("outputPrefix").orElse(defaultPrefix)
    val instance = providers.gradleProperty("instance").orElse("unknown")
    val os = providers.gradleProperty("os").orElse(System.getProperty("os.name") + " " + System.getProperty("os.version"))
    val smithyJavaVersion = project.file("${project.rootDir}/VERSION").readText().trim()

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
        "--instance",
        instance.get(),
        "--os",
        os.get(),
        "--software-version",
        smithyJavaVersion,
    )
}
