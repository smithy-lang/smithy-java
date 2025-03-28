plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.publishing-conventions")
}

description = "This module provides java code generation plugins for Smithy"

extra["displayName"] = "Smithy :: Java :: Codegen :: Plugins"
extra["moduleName"] = "software.amazon.smithy.java.codegen.plugins"

dependencies {
    subprojects.forEach { api(project(it.path)) }
}
