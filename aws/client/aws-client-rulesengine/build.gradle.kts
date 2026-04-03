plugins {
    id("smithy-java.module-conventions")
    id("me.champeau.jmh") version "0.7.3"
}

description = "This module provides AWS-Specific client rules engine functionality"

extra["displayName"] = "Smithy :: Java :: AWS :: Client :: Rules Engine"
extra["moduleName"] = "software.amazon.smithy.java.aws.client.rulesengine"

// Custom configurations for service models - kept separate from main classpath
val s3Model: Configuration by configurations.creating
val lambdaModel: Configuration by configurations.creating

dependencies {
    api(project(":aws:client:aws-client-core"))
    api(project(":client:client-rulesengine"))
    api(libs.smithy.aws.endpoints)

    testImplementation(libs.smithy.aws.traits)
    testImplementation(project(":aws:client:aws-client-restxml"))
    testImplementation(project(":aws:client:aws-client-restjson"))
    testImplementation(project(":client:dynamic-client"))

    s3Model("software.amazon.api.models:s3:1.0.15")
    lambdaModel("software.amazon.api.models:lambda:1.0.15")
}

// Add service models to test and JMH classpaths
configurations["testImplementation"].extendsFrom(s3Model)
configurations["jmhImplementation"].extendsFrom(s3Model)
configurations["jmhImplementation"].extendsFrom(lambdaModel)

// Share the S3 BDD trait between JMH and tests
sourceSets {
    val sharedResources = "src/shared-resources"

    named("test") {
        resources.srcDir(sharedResources)
    }

    named("jmh") {
        resources.srcDir(sharedResources)
    }
}

jmh {
    warmupIterations = 2
    iterations = 3
    fork = 1
    // Allow filtering for specific benchmarks, e.g. -Pjmh.includes=S3EndpointBenchmark
    includes.addAll(
        providers
            .gradleProperty("jmh.includes")
            .map { listOf(it) }
            .orElse(emptyList()),
    )
    // profilers.add("async:output=flamegraph;dir=build/jmh-profiler")
    profilers.add("async:output=collapsed;dir=build/jmh-profiler")
    // profilers.add("gc")
    duplicateClassesStrategy = DuplicatesStrategy.EXCLUDE // don't dump a bunch of warnings.
}

// Clean cached bytecode before running benchmarks so stale compilations aren't reused.
tasks.named("jmh") {
    doFirst {
        fileTree(System.getProperty("java.io.tmpdir"))
            .matching {
                include("s3-endpoint-bytecode-*.bin")
            }.forEach { delete(it) }
    }
}
