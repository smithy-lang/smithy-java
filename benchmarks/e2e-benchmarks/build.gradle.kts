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

val sharedModelDir = layout.projectDirectory.dir("../serde-benchmarks/model")

dependencies {
    implementation(project(":benchmarks:benchmark-commons"))

    smithyBuild(project(":codegen:codegen-plugin"))
    smithyBuild(project(":client:client-core"))
    smithyBuild(project(":client:client-rpcv2-cbor"))
    smithyBuild(project(":aws:client:aws-client-awsjson"))
    smithyBuild(project(":aws:client:aws-client-awsquery"))
    smithyBuild(project(":aws:client:aws-client-restjson"))
    smithyBuild(project(":aws:client:aws-client-restxml"))
    smithyBuild(project(":aws:aws-sigv4"))
    smithyBuild(project(":aws:client:aws-client-core"))

    implementation(libs.smithy.model)
    implementation(libs.smithy.aws.traits)
    implementation(libs.smithy.protocol.traits)
    implementation(libs.smithy.protocol.test.traits)
    implementation(libs.smithy.utils)

    implementation(project(":core"))
    implementation(project(":io"))
    implementation(project(":logging"))
    implementation(project(":context"))
    implementation(project(":client:client-core"))
    implementation(project(":client:client-http"))
    implementation(project(":client:client-http-binding"))
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

    // Use the same typed inputs as serde-benchmarks.
    implementation(project(":protocol-test-harness"))

    jmhImplementation(project(":benchmarks:benchmark-commons"))
}

// Keep fixture server classes out of the client jar.
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

    testImplementation(fixtureServer.output)
    testImplementation("io.netty:netty-handler:$netty")
    testImplementation("io.netty:netty-tcnative-boringssl-static:$tcnative")
}

// Package the shared models for runtime discovery.
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

// Record build details for benchmark hosts without the repository.
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

// Merge service descriptors before processResources can overwrite duplicate paths.
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

// Separate protocol packages prevent shape name collisions.
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
    // Append all model manifests so runtime discovery finds every model.
    transform(com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer::class.java) {
        resource = "META-INF/smithy/manifest"
    }
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
    // Resolve paths during configuration to support the Gradle configuration cache.
    val transport = (project.findProperty("transport") as String?) ?: "https"
    val benchmarks = (project.findProperty("benchmarks") as String?)
        ?: "rpcv2Cbor_PutItemRequest_Baseline,awsJson1_0_GetItemOutput_M,restXml_PutObject_L,restXml_GetObject_L"
    val runs = (project.findProperty("runs") as String?) ?: "3"
    val script = file("fixture/e2e-scheduler.py").absolutePath
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

// Preserve service descriptors and model manifests in the JMH jar.
tasks.jmhJar {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    filesNotMatching(listOf("META-INF/services/**", "META-INF/smithy/manifest")) {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    mergeServiceFiles()
    append("META-INF/smithy/manifest")
    configurations = listOf(project.configurations["jmhRuntimeClasspath"])
}
