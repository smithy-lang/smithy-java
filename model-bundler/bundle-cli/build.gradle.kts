plugins {
    application
    id("smithy-java.module-conventions")
    alias(libs.plugins.shadow)
}

description = "This module implements the model-bundler utility"

extra["displayName"] = "Smithy :: Java :: Model Bundler"
extra["moduleName"] = "software.amazon.smithy.java.modelbundle.cli"

dependencies {
    implementation(project(":core"))
    implementation(project(":model-bundler:bundle-api"))
    implementation(libs.smithy.model)
    implementation(project(":codecs:json-codec"))

    // TODO dynamically load this dependency instead of bundling it
    implementation(project(":aws:aws-service-bundler"))

    shadow(project(":core"))
    shadow(project(":model-bundler:bundle-api"))
}

val mainClassName = "software.amazon.smithy.java.modelbundler.ModelBundler"
application {
    mainClass.set(mainClassName)
    applicationName = "model-bundler"
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
        manifest {
            attributes["Main-Class"] = mainClassName
        }
    }

    jar {
        finalizedBy(shadowJar)
    }

    distZip {
        dependsOn(shadowJar)
    }

    distTar {
        dependsOn(shadowJar)
    }

    startScripts {
        dependsOn(shadowJar)
    }
}
