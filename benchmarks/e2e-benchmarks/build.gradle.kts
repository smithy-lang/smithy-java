// Note: Not published
plugins {
    id("smithy-java.java-conventions")
    id("com.gradleup.shadow")
    application
    id("smithy-java.jmh-conventions")
    id("software.amazon.smithy.gradle.smithy-base")
}

description =
    "Cross-SDK serde E2E ops/CPU-sec benchmark: complete generated-client calls with the HTTP transport mocked in-process."

application {
    mainClass.set("software.amazon.smithy.java.benchmarks.e2e.E2eBenchmark")
}

// Codegen imports this model; GenerateSmithyManifest makes it discoverable at runtime.
val sharedModelDir = layout.projectDirectory.dir("../serde-benchmarks/model")

dependencies {
    implementation(project(":benchmarks:benchmark-commons"))

    // Codegen resolves protocol/auth factories and the credential-chain plugin from this classpath.
    smithyBuild(project(":codegen:codegen-plugin"))
    smithyBuild(project(":client:client-core"))
    smithyBuild(project(":client:client-rpcv2-cbor"))
    smithyBuild(project(":aws:client:aws-client-awsjson"))
    smithyBuild(project(":aws:client:aws-client-awsquery"))
    smithyBuild(project(":aws:client:aws-client-restjson"))
    smithyBuild(project(":aws:client:aws-client-restxml"))
    smithyBuild(project(":aws:aws-sigv4"))
    smithyBuild(project(":aws:client:aws-client-core"))

    // The model is assembled at runtime (BenchmarkCases) to index the tagged test cases.
    implementation(libs.smithy.model)
    implementation(libs.smithy.aws.traits)
    implementation(libs.smithy.protocol.traits)
    implementation(libs.smithy.protocol.test.traits)
    implementation(libs.smithy.utils)

    // Runtime stack under test: what a customer's generated client pulls in.
    implementation(project(":core"))
    implementation(project(":io"))
    implementation(project(":logging"))
    implementation(project(":context"))
    implementation(project(":client:client-core"))
    implementation(project(":client:client-http"))
    implementation(project(":client:client-http-binding"))
    // Network modes use smithy-java's HTTP client with BoringSSL for HTTPS.
    implementation(project(":client:client-http-smithy"))
    implementation(project(":client:client-http-boringssl"))
    implementation(project(":http:http-client"))
    implementation(project(":client:client-rpcv2-cbor"))
    implementation(project(":endpoints"))
    implementation(project(":auth-api"))
    implementation(project(":retries-api"))
    implementation(project(":retries"))
    implementation(project(":http:http-api"))
    implementation(project(":http:http-binding"))
    implementation(project(":aws:aws-sigv4"))
    implementation(project(":aws:aws-auth-api"))
    implementation(project(":aws:client:aws-client-core"))
    implementation(project(":aws:client:aws-client-awsjson"))
    implementation(project(":aws:client:aws-client-awsquery"))
    implementation(project(":aws:client:aws-client-restjson"))
    implementation(project(":aws:client:aws-client-restxml"))
    implementation(project(":codecs:json-codec", configuration = "shadow"))
    implementation(project(":codecs:cbor-codec"))
    implementation(project(":codecs:xml-codec"))

    // ProtocolTestDocument turns each test case's `params` Node into a typed input at setup,
    // exactly as serde-benchmarks does, so both suites serialize identical inputs.
    implementation(project(":protocol-test-harness"))

    // JMH view of the same benchmarks (src/jmh). The profiler lives in benchmark-commons.
    jmhImplementation(project(":benchmarks:benchmark-commons"))
}

// Keep server classes out of the client jar; FixtureServerTransports bridges to package-private transports.
val fixtureServer: SourceSet by sourceSets.creating

val netty = "4.2.18.Final"
val tcnative = "2.0.84.Final"

dependencies {
    "fixtureServerImplementation"(project(":http:http-client"))
    "fixtureServerImplementation"("io.netty:netty-handler:$netty")
    "fixtureServerImplementation"("io.netty:netty-buffer:$netty")
    "fixtureServerImplementation"("io.netty:netty-tcnative-boringssl-static:$tcnative")
    "fixtureServerRuntimeOnly"("io.netty:netty-tcnative-boringssl-static:$tcnative:osx-aarch_64")
    "fixtureServerRuntimeOnly"("io.netty:netty-tcnative-boringssl-static:$tcnative:osx-x86_64")
    "fixtureServerRuntimeOnly"("io.netty:netty-tcnative-boringssl-static:$tcnative:linux-x86_64")
    "fixtureServerRuntimeOnly"("io.netty:netty-tcnative-boringssl-static:$tcnative:linux-aarch_64")

    // The server's tests live in src/test next to the benchmark tests, so give the test classpath the
    // server classes and the one netty type they touch (OpenSsl.isAvailable()).
    testImplementation(fixtureServer.output)
    testImplementation("io.netty:netty-handler:$netty")
    testImplementation("io.netty:netty-tcnative-boringssl-static:$tcnative")
}

// Copies the shared .smithy files to META-INF/smithy/ and writes the model manifest so
// `Model.assembler().discoverModels()` finds them on the runtime classpath.
abstract class GenerateSmithyManifest : DefaultTask() {
    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
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
    description = "Copy the shared benchmark .smithy files to META-INF/smithy/ and generate the model manifest."
    sourceDir.set(sharedModelDir)
    outputDir.set(layout.buildDirectory.dir("generated-resources/smithy-manifest"))
}

// Records the git commit the jar was built from. The jar is usually copied to a benchmark
// host without the repository, and the cross-SDK results schema wants the commit hash.
abstract class WriteBuildInfo : DefaultTask() {
    @get:Input
    abstract val commit: Property<String>

    @get:Input
    abstract val branch: Property<String>

    @get:Input
    abstract val dirty: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val file = outputDir.get().asFile.resolve("META-INF/smithy-java-e2e-benchmarks/build-info.properties")
        file.parentFile.mkdirs()
        file.writeText("commit=${commit.get()}\nbranch=${branch.get()}\ndirty=${dirty.get()}\n")
    }
}

fun git(vararg args: String): Provider<String> =
    providers
        .exec {
            workingDir = rootDir
            commandLine("git", *args)
            isIgnoreExitValue = true
        }.standardOutput.asText
        .map { it.trim() }

val writeBuildInfo by tasks.registering(WriteBuildInfo::class) {
    group = "build"
    description = "Record the git commit and branch the benchmark jar was built from."
    commit.set(git("rev-parse", "HEAD").map { it.ifEmpty { "unknown" } })
    branch.set(git("rev-parse", "--abbrev-ref", "HEAD").map { it.ifEmpty { "unknown" } })
    dirty.set(git("status", "--porcelain", "--untracked-files=no").map { it.isNotEmpty() })
    outputDir.set(layout.buildDirectory.dir("generated-resources/build-info"))
}

// Each codegen projection registers its own META-INF/services/...SchemaIndex. A plain copy keeps
// only one of the duplicate paths, so merge them into one descriptor per service type and feed
// that to processResources instead of the per-projection copies.
abstract class MergeServiceFiles : DefaultTask() {
    @get:InputFiles
    abstract val serviceDirs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val out = outputDir.get().asFile.resolve("META-INF/services")
        out.deleteRecursively()
        out.mkdirs()
        val merged = sortedMapOf<String, LinkedHashSet<String>>()
        serviceDirs.files.filter { it.isDirectory }.forEach { dir ->
            dir.listFiles()?.filter { it.isFile }?.forEach { file ->
                merged.getOrPut(file.name) { linkedSetOf() } +=
                    file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            }
        }
        merged.forEach { (name, providers) ->
            out.resolve(name).writeText(providers.joinToString("\n", postfix = "\n"))
        }
    }
}

// One codegen projection per protocol, each emitting into its own package so same-named
// shapes from different protocols don't collide.
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
    val mergeCodegenServiceFiles by tasks.registering(MergeServiceFiles::class) {
        group = "build"
        description = "Merge the META-INF/services descriptors emitted by the codegen projections."
        dependsOn("smithyBuild")
        projectionPaths.forEach { serviceDirs.from("$it/resources/META-INF/services") }
        outputDir.set(layout.buildDirectory.dir("generated-resources/merged-services"))
    }
    sourceSets.named("main") {
        java {
            projectionPaths.forEach { srcDir("$it/java") }
        }
        resources {
            projectionPaths.forEach { srcDir("$it/resources") }
            // The per-projection descriptors are replaced by the merged ones below.
            exclude("META-INF/services/**")
            srcDir(generateSmithyManifest)
            srcDir(writeBuildInfo)
        }
    }
    tasks.named<Copy>("processResources") {
        from(mergeCodegenServiceFiles)
    }
}

tasks.named("compileJava") {
    dependsOn("smithyBuild")
}

tasks.named<Copy>("processResources") {
    // Service descriptors are merged above; any other duplicate resource is a bug worth seeing.
    duplicatesStrategy = DuplicatesStrategy.FAIL
    dependsOn("smithyBuild")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("smithy-java-e2e-benchmark")
    archiveClassifier.set("")
    archiveVersion.set("")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    filesNotMatching(listOf("META-INF/services/**", "META-INF/smithy/manifest")) {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    mergeServiceFiles()
    // Keep META-INF/smithy/manifest entries from each jar so all models are discovered at runtime.
    transform(com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer::class.java) {
        resource = "META-INF/smithy/manifest"
    }
    // Avoid collisions between MANIFEST/SF files from third-party jars.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.named("assemble") {
    dependsOn("shadowJar")
}

val fixtureServerJar by tasks.registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
    group = "build"
    description = "Shaded, runnable HTTP/1.1 fixture server (BoringSSL TLS) for the http/https transport modes."
    from(fixtureServer.output)
    configurations = listOf(project.configurations["fixtureServerRuntimeClasspath"])
    archiveBaseName.set("smithy-java-fixture-server")
    archiveClassifier.set("")
    archiveVersion.set("")
    manifest { attributes("Main-Class" to "software.amazon.smithy.java.benchmarks.fixture.FixtureServer") }
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.named("assemble") {
    dependsOn(fixtureServerJar)
}

val transportBenchmark by tasks.registering(Exec::class) {
    group = "benchmark"
    description = "Run the e2e benchmark over the real HTTP transport against the fixture server (-Ptransport, -Pbenchmarks, -Pruns)."
    dependsOn("shadowJar", fixtureServerJar)
    // Everything is resolved to plain strings here, at configuration time: an Exec task that captured
    // project/layout references for execution would break the Gradle configuration cache.
    val transport = (project.findProperty("transport") as String?) ?: "https"
    val benchmarks = (project.findProperty("benchmarks") as String?)
        ?: "rpcv2Cbor_PutItemRequest_Baseline,awsJson1_0_GetItemOutput_M,restXml_PutObject_L,restXml_GetObject_L"
    val runs = (project.findProperty("runs") as String?) ?: "3"
    val script = file("fixture/run-transport.py").absolutePath
    val e2eJar = layout.buildDirectory.file("libs/smithy-java-e2e-benchmark.jar").get().asFile.absolutePath
    val serverJar = layout.buildDirectory.file("libs/smithy-java-fixture-server.jar").get().asFile.absolutePath
    val outDir = layout.buildDirectory.dir("transport-benchmark").get().asFile.absolutePath
    val javaBin = javaToolchains.launcherFor(java.toolchain).get().executablePath.asFile.absolutePath
    commandLine(
        "python3", script,
        "--java", javaBin,
        "--jar", e2eJar,
        "--server-jar", serverJar,
        "--modes", "stub,$transport",
        "--benchmarks", benchmarks,
        "--runs", runs,
        "--outdir", outDir,
    )
}

// JMH and the CPU-time runner share the canonical ids; -Pjmh.testCaseId overrides them.
val canonicalBenchmarkIds =
    file("src/main/resources/software/amazon/smithy/java/benchmarks/e2e/canonical-benchmarks.txt")
        .readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }

val fast = providers.gradleProperty("jmh.fast").isPresent
jmh {
    includeTests.set(false)
    benchmarkMode.set(listOf("sample"))
    profilers.add("software.amazon.smithy.java.benchmarks.OpsPerCpuSecondProfiler")
    if (!fast) {
        warmupIterations = 5
        iterations = 10
    }
    timeOnIteration = "5s"
    jvmArgs.addAll(
        "-Xms1g",
        "-Xmx1g",
        "-XX:+UseG1GC",
        "-XX:+AlwaysPreTouch",
    )
    val ids = objects.listProperty(String::class.java)
    val requested = providers.gradleProperty("jmh.testCaseId").orNull
    if (requested != null) {
        requested.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { ids.add(it) }
    } else {
        canonicalBenchmarkIds.forEach { ids.add(it) }
    }
    benchmarkParameters.put("testCaseId", ids)
    resultFormat = "json"
    resultsFile = layout.buildDirectory.file("results/jmh/results.json")
}

// With shadow applied before jmh, jmhJar is a ShadowJar. Merge duplicate META-INF/services/
// entries from the codegen projections instead of overwriting them, and keep every model
// manifest.
tasks.jmhJar {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    filesNotMatching(listOf("META-INF/services/**", "META-INF/smithy/manifest")) {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    mergeServiceFiles()
    append("META-INF/smithy/manifest")
    configurations = listOf(project.configurations["jmhRuntimeClasspath"])
}
