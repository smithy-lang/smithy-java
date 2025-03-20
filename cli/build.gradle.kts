plugins {
    `java-library`
    application
    id("software.amazon.smithy.gradle.smithy-base")
    id("org.graalvm.buildtools.native") version "0.10.3"
}

dependencies {
//    val smithyJavaVersion: String by project

//    smithyBuild("software.amazon.smithy.java.codegen:plugins:$smithyJavaVersion")

    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")

    // Client dependencies
    implementation("software.amazon.smithy:smithy-aws-traits")

    implementation(project(":aws:client:aws-client-restjson"))
    implementation(project(":aws:client:aws-client-awsjson"))
    implementation(project(":aws:client:aws-client-restxml"))
    implementation(project(":client:client-rpcv2-cbor"))

    implementation(project(":client:dynamic-client"))
}

application {
    mainClass = "software.amazon.smithy.java.cli.CoralXRunner"
}

// Add generated Java sources to the main sourceset
//afterEvaluate {
//    val clientPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-cli-codegen")
//    sourceSets.main.get().java.srcDir(clientPath)
//}

//tasks {
//    compileJava {
//        dependsOn(smithyBuild)
//    }
//}

// Helps Intellij IDE's discover smithy models
//sourceSets {
//    main {
//        java {
//            srcDir("src")
//        }
//    }
//}

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

            "--initialize-at-build-time=software.amazon.smithy.aws.traits",
            "--initialize-at-build-time=software.amazon.smithy.aws.traits.protocols",
            "--initialize-at-build-time=software.amazon.smithy.utils.BuilderRef"


//            "--initialize-at-build-time=java.net.URL",
//            "--initialize-at-build-time=java.net.URI",
//            "--enable-all-security-services"

//            "--initialize-at-build-time=software.amazon.smithy.aws.traits",
//            "--initialize-at-build-time=software.amazon.smithy.aws.protocols"
//            "--initialize-at-build-time=software.amazon.smithy",
//            "--initialize-at-build-time=software.amazon.smithy.model",
//            "-H:Log=registerResource:5",
//            "--no-fallback"
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


