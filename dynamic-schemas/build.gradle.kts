plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides a way to dynamically create Smithy Java schemas from a model"

extra["displayName"] = "Smithy :: Java :: Dynamic Schemas"
extra["moduleName"] = "software.amazon.smithy.java.dynamicschemas"

dependencies {
    api(project(":core"))
}
