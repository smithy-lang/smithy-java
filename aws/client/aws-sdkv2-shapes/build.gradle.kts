plugins {
    id("smithy-java.module-conventions")
}

description = "This module allows for converting between Smithy Java and AWS SDK for Java V2 documents"

extra["displayName"] = "Smithy :: Java :: AWS :: SDK V2 :: Shapes"
extra["moduleName"] = "software.amazon.smithy.java.aws.sdkv2.shapes"

dependencies {
    implementation(project(":codecs:json-codec"))
    implementation(project(":core"))
    implementation(project(":aws:client:client-restjson"))
    implementation(project(":aws:client:client-awsjson"))
    implementation(libs.aws.sdkcore)

    testImplementation(project(":client:dynamic-client"))
}
