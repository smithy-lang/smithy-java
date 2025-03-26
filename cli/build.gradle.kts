plugins {
    `java-library`
    application
    id("software.amazon.smithy.gradle.smithy-base")
    id("org.graalvm.buildtools.native") version "0.10.3"
}

dependencies {
    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")

    // Client dependencies
    implementation(project(":aws:client:aws-client-restjson"))
    implementation(project(":aws:client:aws-client-awsjson"))
    implementation(project(":aws:client:aws-client-restxml"))
    implementation(project(":client:client-rpcv2-cbor"))

    implementation(project(":client:dynamic-client"))
    implementation(project(":codecs:json-codec"))
    implementation(project(":client:client-http"))
    implementation(project(":aws:client:aws-client-core"))
    implementation(project(":aws:sigv4"))
}

application {
    mainClass = "software.amazon.smithy.java.cli.CoralXRunner"
}

graalvmNative {
    binaries.named("main") {
        // Set up correct java JVM to use.
        javaLauncher.set(
            javaToolchains.launcherFor {
                // Use oracle GraalVM JDK for build
                languageVersion.set(JavaLanguageVersion.of(23))
                vendor.set(JvmVendorSpec.matching("Oracle Corporation"))
            },
        )

        // Ensure resources are detected
        resources.autodetect()

        buildArgs.addAll(listOf(
            "-H:ResourceConfigurationFiles=${projectDir}/src/resource-config.json",
            "-H:ReflectionConfigurationFiles=${projectDir}/src/reflect-config.json",
            "--enable-url-protocols=http,https",
        ))

        // Debug info
        verbose.set(true)

        // Image configuration
        imageName.set("coralx")
        mainClass.set(application.mainClass)

        // Determines if image is a shared library [note: defaults to true if java-library plugin is applied]
        sharedLibrary.set(false)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}


