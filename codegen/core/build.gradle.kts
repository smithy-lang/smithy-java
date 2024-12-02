plugins {
    id("smithy-java.codegen-plugin-conventions")
    id("smithy-java.publishing-conventions")
}

description = "This module provides the core codegen functionality for Smithy java"
group = "software.amazon.smithy.java.codegen"
extra["displayName"] = "Smithy :: Java :: Codegen :: Core"
extra["moduleName"] = "software.amazon.smithy.java.codegen.core"

dependencies {
    api(libs.smithy.codegen)
    implementation(project(":smithy-implicit-error-trait"))
    itImplementation(project(":json-codec"))
}

addGenerateSrcsTask("software.amazon.smithy.java.codegen.utils.TestJavaCodegenRunner")
