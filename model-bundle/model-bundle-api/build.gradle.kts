plugins {
    application
    id("smithy-java.module-conventions")
    id("software.amazon.smithy.gradle.smithy-base")
}

description = "This module implements the model-bundle utility"

extra["displayName"] = "Smithy :: Java :: Model Bundle"
extra["moduleName"] = "software.amazon.smithy.java.modelbundle.api"

dependencies {
    smithyBuild(project(":codegen:codegen-plugin"))

    implementation(project(":core"))
    implementation(project(":logging"))
    implementation(libs.smithy.model)
    api(project(":client:client-auth-api"))
    api(project(":client:client-core"))
    api(project(":dynamic-schemas"))
    api(project(":server:server-api"))
    api(project(":server:server-proxy"))
}

afterEvaluate {
    val typesPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-codegen").get()
    sourceSets {
        main {
            java {
                srcDir("$typesPath/java")
            }
            resources {
                srcDir("$typesPath/resources")
            }
        }
    }
}

tasks.named("compileJava") {
    dependsOn("smithyBuild")
}

// Needed because sources-jar needs to run after smithy-build is done
tasks.sourcesJar {
    mustRunAfter(tasks.compileJava)
}

tasks.processResources {
    dependsOn(tasks.compileJava)
}
