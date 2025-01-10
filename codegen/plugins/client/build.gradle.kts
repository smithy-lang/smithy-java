plugins {
    id("smithy-java.codegen-plugin-conventions")
}

description = "This module provides the codegen plugin for Smithy java client codegen"

extra["displayName"] = "Smithy :: Java :: Codegen :: Plugins :: Client"
extra["moduleName"] = "software.amazon.smithy.java.codegen.client"

dependencies {
    api(project(":client-core"))
    testImplementation(project(":aws:client-restjson"))
    testImplementation(libs.smithy.aws.traits)

    itImplementation(project(":aws:client-restjson"))
}

addGenerateSrcsTask("software.amazon.smithy.java.codegen.client.TestServerJavaClientCodegenRunner")

sourceSets {
    it {
        // Add test plugin to classpath
        compileClasspath += sourceSets["test"].output
    }
}
