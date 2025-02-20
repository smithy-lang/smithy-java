plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the Smithy Java support for OTeL metrics"

extra["displayName"] = "Smithy :: Java :: Metrics :: OTeL"
extra["moduleName"] = "software.amazon.smithy.java.metrics.otel"

dependencies {
    api(project(":metrics:metrics-api"))
    api(libs.otel.api)
}
