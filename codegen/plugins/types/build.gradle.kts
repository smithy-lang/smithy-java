plugins {
    id("smithy-java.codegen-plugin-conventions")
}

description = "This module provides the codegen plugin for Smithy java type codegen"

extra["displayName"] = "Smithy :: Java :: Codegen :: Plugins :: Types"
extra["moduleName"] = "software.amazon.smithy.java.codegen.types"

addGenerateSrcsTask("software.amazon.smithy.java.codegen.types.TestJavaTypeCodegenRunner")

dependencies {
    // TODO: Remove once trait has been upstreamed to prelude.
    implementation(project(":smithy-implicit-error-trait"))
}
