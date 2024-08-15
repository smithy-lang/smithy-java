plugins {
    id("smithy-java.codegen-plugin-conventions")
}

description = "This module provides the codegen plugin for Smithy java server codegen"

extra["displayName"] = "Smithy :: Java :: Codegen :: Server"
extra["moduleName"] = "software.amazon.smithy.java.codegen.server"

dependencies {
    implementation(project(":server-core"))
}

tasks {
    val generateSrcTask = addGenerateSrcsTask("software.amazon.smithy.java.codegen.server.TestServerJavaCodegenRunner")

    integ {
        dependsOn(generateSrcTask)
    }
    compileItJava {
        dependsOn(generateSrcTask)
    }
    test {
        finalizedBy(integ)
    }
}
