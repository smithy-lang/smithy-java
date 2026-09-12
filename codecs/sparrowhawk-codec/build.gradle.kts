plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides Sparrowhawk binary serialization functionality"

extra["displayName"] = "Smithy :: Java :: Sparrowhawk"
extra["moduleName"] = "software.amazon.smithy.java.sparrowhawk"

dependencies {
    api(project(":core"))
    implementation(project(":codecs:codec-commons", configuration = "shadow"))
}
