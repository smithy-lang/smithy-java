plugins {
    id("smithy-java.codegen-plugin-conventions")
}

description = "This module provides the codegen plugin for Smithy java client codegen"

extra["displayName"] = "Smithy :: Java :: Codegen :: Client"
extra["moduleName"] = "software.amazon.smithy.java.codegen.client"

dependencies {
    api(project(":client:client-core"))
    api(project(":client:client-rulesengine"))
    testImplementation(project(":aws:client:aws-client-restjson"))
    testImplementation(libs.smithy.aws.traits)
    testImplementation(libs.smithy.rules)
    itImplementation(project(":aws:client:aws-client-restjson"))
}

addGenerateSrcsTask("software.amazon.smithy.java.codegen.client.TestServerJavaClientCodegenRunner")

tasks.test {
    failOnNoDiscoveredTests = false
}

sourceSets {
    it {
        // Add test plugin to classpath
        compileClasspath += sourceSets["test"].output
        resources.srcDir("${layout.buildDirectory.get()}/generated-src/resources")
    }
}

tasks.named("processItResources") {
    dependsOn("generateSources")
}
