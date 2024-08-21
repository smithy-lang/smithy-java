plugins {
    id("smithy-java.codegen-plugin-conventions")
}

description = "This module provides the core codegen functionality for Smithy java"

extra["displayName"] = "Smithy :: Java :: Codegen :: Core"
extra["moduleName"] = "software.amazon.smithy.java.codegen"

dependencies {
    itImplementation(project(":json-codec"))
}

addGenerateSrcsTask("software.amazon.smithy.java.codegen.utils.TestJavaCodegenRunner")
