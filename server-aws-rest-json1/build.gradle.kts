plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the AWS RestJson1 support for servers."

extra["displayName"] = "Smithy :: Java :: Server AWS RestJson1"
extra["moduleName"] = "software.amazon.smithy.java.server-aws-rest-json1"

dependencies {
    api(project(":server"))
    api(project(":http-api"))
    api(libs.smithy.aws.traits)
    implementation(project(":server-core"))
    implementation(project(":context"))
    implementation(project(":core"))
    implementation(project(":json-codec"))
    implementation(project(":http-binding"))
}
