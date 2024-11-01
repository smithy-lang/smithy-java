plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides an adapter that allows Smithy Java to use retry strategies from the AWS SDK for Java V2"

extra["displayName"] = "Smithy :: Java :: Retries :: AWS SDK for Java Adapter"
extra["moduleName"] = "software.amazon.smithy.java.retries.sdkadapter"

dependencies {
    api(project(":retries-api"))
    implementation(libs.aws.sdk.retries.spi)

    testImplementation(libs.aws.sdk.retries)
}
