plugins {
    id("smithy-java.module-conventions")
    alias(libs.plugins.smithy.gradle.jar)
}

description = "Smithy AI Traits"

extra["displayName"] = "Smithy :: Java :: AI Traits"
extra["moduleName"] = "software.amazon.smithy.ai.traits"

dependencies {
    api(libs.smithy.model)
    smithyBuild(libs.smithy.traitcodegen)
}

sourceSets {
    val traitsPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "trait-codegen")
    sourceSets {
        main {
            java {
                srcDir(traitsPath)
                include("software/**")
            }

            smithy {
                srcDir("$traitsPath/model")
            }

            resources {
                srcDir(traitsPath)
                exclude("**/*.java")
            }
        }
    }
}

tasks.sourcesJar {
    dependsOn("smithyJarStaging")
}

spotbugs {
    ignoreFailures = true
}

tasks.compileJava {
    options.release.set(8)
}

java.sourceSets["main"].java {
    srcDirs("model", "src/main/smithy")
}

tasks.jar {
    doFirst {
        manifest.attributes.remove("Build-Timestamp")
        manifest.attributes.remove("Build-OS")
        manifest.attributes.remove("Build-Jdk")
    }
}
