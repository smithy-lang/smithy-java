plugins {
    id("smithy-java.codegen-plugin-conventions")
}

description = "This module provides the codegen plugin for Smithy java type codegen"

extra["displayName"] = "Smithy :: Java :: Codegen :: Types"
extra["moduleName"] = "software.amazon.smithy.java.codegen.types"

dependencies {
    testImplementation(project(":codegen:test-utils"))
}

addGenerateSrcsTask("software.amazon.smithy.java.codegen.types.TestJavaTypeCodegenRunner")
