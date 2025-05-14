plugins {
    id("smithy-java.module-conventions")
}

description = "This module allows for converting between Smithy Java and AWS SDK for Java V2 documents"

extra["displayName"] = "Smithy :: Java :: AWS :: SDKv2 :: Shapes"
extra["moduleName"] = "software.amazon.smithy.java.aws.sdkv2.shapes"

dependencies {
    implementation(project(":codecs:json-codec", configuration = "shadow"))
    implementation(project(":core"))
    implementation(project(":aws:client:aws-client-restjson"))
    implementation(project(":aws:client:aws-client-awsjson"))
    implementation(libs.aws.sdk.core)

    testImplementation(project(":client:dynamic-client"))
}
