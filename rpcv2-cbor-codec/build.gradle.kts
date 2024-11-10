plugins {
    id("smithy-java.module-conventions")
    `java-test-fixtures`
}

description = "This module provides CBOR functionality"

extra["displayName"] = "Smithy :: Java :: RPCv2 CBOR"
extra["moduleName"] = "software.amazon.smithy.java.cbor"

dependencies {
    api(project(":core"))
    testFixturesImplementation(libs.assertj.core)
}
