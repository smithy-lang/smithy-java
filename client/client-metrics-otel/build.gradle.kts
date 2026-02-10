plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides a client metrics plugin for OpenTelemetry"

extra["displayName"] = "Smithy :: Java :: Client :: Metrics :: OTel"
extra["moduleName"] = "software.amazon.smithy.java.client.metrics.otel"

dependencies {
    api(project(":core"))
    api(project(":client:client-core"))
    api(project(":http:http-api"))
    implementation(libs.opentelemetry.api)

    testImplementation(project(":client:dynamic-client"))
    testImplementation(project(":aws:client:aws-client-restjson"))
    testImplementation(project(":aws:client:aws-client-core"))
    testImplementation(project(":aws:aws-sigv4"))
    testImplementation(project(":client:client-mock-plugin"))
    testImplementation(project(":aws:sdkv2:aws-sdkv2-auth"))
    testImplementation(libs.aws.sdk.auth)
    testImplementation(libs.opentelemetry.test.api)
}
