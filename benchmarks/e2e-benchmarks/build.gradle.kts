// Note: Not published
plugins {
    id("smithy-java.java-conventions")
    alias(libs.plugins.shadow)
    application
    id("software.amazon.smithy.gradle.smithy-base")
}

description = "End-to-end SDK benchmarks against live AWS services (DynamoDB GetItem/PutItem latency, S3 GetObject/PutObject throughput)."

application {
    mainClass.set("software.amazon.smithy.java.benchmarks.e2e.WorkloadRunner")
}

dependencies {
    // Codegen plugin and runtime modules referenced by generated client code.
    smithyBuild(project(":codegen:codegen-plugin"))
    smithyBuild(project(":client:client-core"))
    smithyBuild(project(":client:client-waiters"))
    // Needed at codegen time so RulesEngineBuilder loads AwsRulesExtension via ServiceLoader.
    smithyBuild(project(":aws:client:aws-client-rulesengine"))
    // Codegen needs the plugin class on its classpath so `Class.forName(...)` resolves it
    // when wiring it into the generated client.
    smithyBuild(project(":aws:aws-sigv4-s3express"))

    // AWS service models pulled from Maven (https://github.com/aws/api-models-aws).
    // The smithy-base plugin only loads models from sources + runtimeClasspath
    // for the source projection, so these go on `implementation`. The codegen
    // plugin (and the runtime stack) read shape definitions through these
    // JARs at build time and at runtime.
    implementation("software.amazon.api.models:dynamodb:1.0.11")
    implementation("software.amazon.api.models:s3:1.0.17")

    // Runtime stack the generated clients depend on.
    implementation(project(":core"))
    implementation(project(":io"))
    implementation(project(":logging"))
    implementation(project(":context"))
    implementation(project(":client:client-core"))
    implementation(project(":client:client-http"))
    implementation(project(":client:client-http-binding"))
    implementation(project(":client:client-rulesengine"))
    implementation(project(":client:client-waiters"))
    implementation(project(":rulesengine"))
    implementation(project(":endpoints"))
    implementation(project(":auth-api"))
    implementation(project(":retries-api"))
    implementation(project(":retries"))
    implementation(project(":http:http-api"))
    implementation(project(":http:http-binding"))

    // AWS-specific runtime: SigV4, AWS protocols, AWS endpoints.
    implementation(project(":aws:aws-sigv4"))
    implementation(project(":aws:aws-sigv4-s3express"))
    implementation(project(":aws:aws-auth-api"))
    implementation(project(":aws:client:aws-client-core"))
    implementation(project(":aws:client:aws-client-http"))
    implementation(project(":aws:client:aws-client-restxml")) // S3 protocol
    implementation(project(":aws:client:aws-client-awsjson")) // DynamoDB protocol
    implementation(project(":aws:client:aws-client-rulesengine"))

    // Codecs
    implementation(project(":codecs:json-codec", configuration = "shadow"))
    implementation(project(":codecs:xml-codec"))

    // AWS Smithy traits (needed for SigV4Trait etc. on the runtime classpath
    // when generated code references trait IDs).
    implementation(libs.smithy.aws.traits)
    implementation(libs.smithy.model)

    // smithy-java credential chain
    implementation(project(":aws:aws-credential-chain"))
    implementation(project(":aws:aws-credentials-imds"))

    // Alternate transports — selected at runtime via -De2e.transport=netty|smithy|apache
    implementation(project(":client:client-http-netty"))
    implementation(project(":client:client-http-smithy"))
    implementation(project(":client:client-http-apache"))
}

// Two projections so that DynamoDB and S3 generate into different namespaces
// and don't collide. Each projection filters down to just the one service
// it wants; the model JAR for s3 only has the s3 model, but the projection
// makes the intent explicit and gives us a stable name.
val codegenProjections = listOf("dynamodb-client", "s3-client")

afterEvaluate {
    val projectionPaths =
        codegenProjections.map { name ->
            smithy.getPluginProjectionPath(name, "java-codegen").get()
        }
    sourceSets.named("main") {
        java {
            projectionPaths.forEach { srcDir("$it/java") }
        }
        resources {
            projectionPaths.forEach { srcDir("$it/resources") }
        }
    }
}

tasks.named("compileJava") {
    dependsOn("smithyBuild")
}

tasks.named<Copy>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    dependsOn("smithyBuild")
}

// The shaded jar is what users invoke from run-benchmark.py, mirroring
// `java -jar runners/java-workload-runner/target/workload-runner-1.0.0.jar`.
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("smithy-java-e2e-benchmark-runner")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
    // Keep META-INF/smithy/manifest entries from each codegen projection
    // so all schema indexes are discovered at runtime.
    transform(com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer::class.java) {
        resource = "META-INF/smithy/manifest"
    }
    // Avoid collisions between MANIFEST/SF files from third-party jars.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.named("assemble") {
    dependsOn("shadowJar")
}

// Don't run benchmarks under `./gradlew check`, they hit live AWS.
tasks.named("check") {
    enabled = true
}
