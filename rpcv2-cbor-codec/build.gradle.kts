plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides CBOR functionality"

extra["displayName"] = "Smithy :: Java :: RPCv2 CBOR"
extra["moduleName"] = "software.amazon.smithy.java.cbor"

dependencies {
    api(project(":core"))
    implementation(libs.jackson.core)
    implementation(libs.jackson.dataformat.cbor)
}
