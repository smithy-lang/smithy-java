import com.google.gradle.osdetector.OsDetector

plugins {
    id("smithy-java.module-conventions")
    alias(libs.plugins.jmh)
    alias(libs.plugins.osdetector)
}

description = "This module provides AWS-Specific http client functionality"

extra["displayName"] = "Smithy :: Java :: AWS :: Client-HTTP"
extra["moduleName"] = "software.amazon.smithy.java.aws.client-http"

dependencies {
    implementation(project(":client-core"))
    api(project(":aws:aws-client-core"))
    implementation(project(":http-api"))
    implementation(project(":io"))
    implementation(project(":logging"))
    implementation(libs.smithy.aws.traits)
}

afterEvaluate {
    val osDetector = extensions.getByType<OsDetector>()
    if (osDetector.os != "windows") {
        dependencies {
            jmh("software.amazon.cryptools:AmazonCorrettoCryptoProvider:${libs.versions.accp.get()}:${osDetector.classifier}")
        }
    }
}

jmh {
    iterations = 3
    warmupIterations = 2
    fork = 1
    // profilers.add("async:output=flamegraph")
    // profilers.add("gc")
}
