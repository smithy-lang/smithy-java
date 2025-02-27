plugins {
    id("smithy-java.module-conventions")
    id("org.graalvm.buildtools.native") version "0.10.3"
    application
}

description = "This module provides a dynamic Smithy client cli"

extra["displayName"] = "Smithy :: Java :: Dynamic client cli"
extra["moduleName"] = "software.amazon.smithy.java.dynamicclientcli"

dependencies {
    // Add core Aws client plugin
    implementation(project(":aws:client:aws-client-core"))
    implementation(project(":aws:client:aws-client-http"))

    // Add protocols
    implementation(project(":aws:client:aws-client-restjson"))
    implementation(project(":aws:client:aws-client-awsjson"))
    implementation(project(":client:client-rpcv2-cbor"))

    implementation(project(":client:dynamic-client"))
//    implementation(libs.smithy.aws.traits)

    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")

    // These are both unused at the moment, but included so we dont have to filter out the traits.
    // Add waitable trait
//    implementation(libs.smithy.waiters)
//    // Endpoint ruleset traits
//    implementation(libs.smithy.rules.engine)
//    implementation(libs.smithy.aws.endpoints)

//    implementation(libs.smithy.utils)
//    implementation(libs.smithy.model)
//    api(libs.smithy.model)
//    api(libs.smithy.utils)
}

tasks {
    integ {
        enabled = true
        testLogging {
            showStandardStreams = true
        }
    }
}

application {
    mainClass = "software.amazon.smithy.java.Runner"
}

graalvmNative {
    binaries.named("main") {
        // Set up correct java JVM to use.
        javaLauncher.set(
            javaToolchains.launcherFor {
                // Use oracle GraalVM JDK for build
                languageVersion.set(JavaLanguageVersion.of(21))
                vendor.set(JvmVendorSpec.matching("Oracle Corporation"))
            },
        )

        // Ensure resources are detected
        resources.autodetect()

        // Ensure all files are UTF-8
        buildArgs.add("-Dfile.encoding=UTF-8")
        buildArgs.add("-J-Dfile.encoding=UTF-8")

        // Debug info
        verbose.set(true)

        // Add helpful errors
        buildArgs.add("-H:+InstallExitHandlers")
        buildArgs.add("-H:+ReportUnsupportedElementsAtRuntime")
        buildArgs.add("-H:+ReportExceptionStackTraces")

        // Image configuration
        imageName.set("application")
        mainClass.set(application.mainClass)

        // Determines if image is a shared library [note: defaults to true if java-library plugin is applied]
        sharedLibrary.set(false)
    }
}
