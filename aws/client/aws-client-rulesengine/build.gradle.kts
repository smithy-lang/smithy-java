plugins {
    id("smithy-java.module-conventions")
    id("me.champeau.jmh") version "0.7.3"
}

description = "This module provides AWS-Specific client rules engine functionality"

extra["displayName"] = "Smithy :: Java :: AWS :: Client :: Rules Engine"
extra["moduleName"] = "software.amazon.smithy.java.aws.client.rulesengine"

dependencies {
    api(project(":aws:client:aws-client-core"))
    api(project(":client:client-rulesengine"))
    api(libs.smithy.aws.endpoints)

    testImplementation(libs.smithy.aws.traits)
    testImplementation(project(":aws:client:aws-client-restxml"))
    testImplementation(project(":aws:client:aws-client-restjson"))
    testImplementation(project(":client:dynamic-client"))
}

// Share the S3 model between JMH and tests.
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
    warmupIterations = 3
    iterations = 5
    fork = 1
    profilers.add("async:output=flamegraph")
    // profilers.add("gc")
    duplicateClassesStrategy = DuplicatesStrategy.EXCLUDE // don't dump a bunch of warnings.
}
