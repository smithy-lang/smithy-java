plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides generic request parameters for interacting with AWS services described by an AWS service bundle"
extra["displayName"] = "Smithy :: Java :: AWS Service Bundle provider"
extra["moduleName"] = "software.amazon.smithy.java.aws.servicebundle.provider"

dependencies {
    api(project(":core"))
    api(libs.smithy.model)
    api(project(":aws:aws-mcp-types"))
    api(project(":auth-api"))
    implementation(project(":aws:aws-sigv4"))
    implementation(project(":aws:client:aws-client-core"))
    implementation(project(":model-bundle:model-bundle-api"))
    implementation(project(":aws:sdkv2:aws-sdkv2-auth"))
    implementation(libs.aws.sdk.auth)
}
