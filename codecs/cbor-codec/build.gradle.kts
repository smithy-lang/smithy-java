plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.fuzz-test")
    id("smithy-java.jmh-conventions")
    `java-test-fixtures`
}

description = "This module provides CBOR functionality"

extra["displayName"] = "Smithy :: Java :: CBOR"
extra["moduleName"] = "software.amazon.smithy.java.cbor"

dependencies {
    api(project(":core"))
    implementation(project(":codecs:codec-commons", configuration = "shadow"))
    testFixturesImplementation(libs.assertj.core)
    testImplementation(project(":codecs:json-codec", configuration = "shadow"))
}
