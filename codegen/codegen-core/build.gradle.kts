plugins {
    id("smithy-java.codegen-plugin-conventions")
    id("smithy-java.publishing-conventions")
}

description = "This module provides the core codegen functionality for Smithy java"

extra["displayName"] = "Smithy :: Java :: Codegen :: Core"
extra["moduleName"] = "software.amazon.smithy.java.codegen.core"

dependencies {
    api(libs.smithy.codegen)
    itImplementation(project(":codecs:json-codec", configuration = "shadow"))
}

addGenerateSrcsTask("software.amazon.smithy.java.codegen.utils.TestJavaCodegenRunner")
