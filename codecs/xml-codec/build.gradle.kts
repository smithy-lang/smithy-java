plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.fuzz-test")
}

description = "This module provides XML functionality"

extra["displayName"] = "Smithy :: Java :: XML"
extra["moduleName"] = "software.amazon.smithy.java.xml"

dependencies {
    api(project(":core"))
}
