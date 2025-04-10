plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides an adapter that allows Smithy Java to use auth from the AWS SDK for Java V2"

extra["displayName"] = "Smithy :: Java :: AWS :: SDKv2 :: Auth Adapter"
extra["moduleName"] = "software.amazon.smithy.java.aws.sdkv2.auth"

dependencies {
    api(project(":retries-api"))
    api(project(":aws:aws-auth-api"))
    implementation(libs.aws.sdk.retries.spi)
    implementation(libs.aws.sdk.auth)
}
