plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides XML functionality"

extra["displayName"] = "Smithy :: Java :: XML"
extra["moduleName"] = "software.amazon.smithy.java.runtime.xml"

dependencies {
    api(project(":core"))
}
