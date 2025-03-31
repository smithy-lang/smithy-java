plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides an adapter that allows Smithy Java to use retry strategies from the AWS SDK for Java V2"

extra["displayName"] = "Smithy :: Java :: AWS :: AWS SDK v2 :: Retries adapter"
extra["moduleName"] = "software.amazon.smithy.java.aws.sdkv2.retries"

dependencies {
    api(project(":retries-api"))
    implementation(libs.aws.sdk.retries.spi)

    testImplementation(libs.aws.sdk.retries)
}
