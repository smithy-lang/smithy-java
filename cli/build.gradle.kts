plugins {
    `java-library`
    application
    id("org.graalvm.buildtools.native") version "0.10.3"
}

dependencies {
    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")

    implementation("software.amazon.smithy:smithy-aws-traits:1.56.0")

    // Client dependencies
    implementation(project(":aws:client:aws-client-restjson"))
    implementation(project(":aws:client:aws-client-awsjson"))
    implementation(project(":aws:client:aws-client-restxml"))
    implementation(project(":client:client-rpcv2-cbor"))

    implementation(project(":client:dynamic-client"))
    implementation(project(":codecs:json-codec"))
    implementation(project(":client:client-http"))
    implementation(project(":aws:client:aws-client-core"))
    implementation(project(":aws:aws-sigv4"))

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.1")
}

tasks.register<Copy>("copySmithyAwsTraits") {
    from(zipTree(configurations.runtimeClasspath.get().filter { it.name.startsWith("smithy-aws-traits-") }.single())) {
        include("**/*.smithy")
        eachFile {
            relativePath = RelativePath(true, name)
        }
    }
    into(layout.buildDirectory.dir("smithy-aws-traits"))

    includeEmptyDirs = false
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named("processResources") {
    dependsOn("copySmithyAwsTraits")
}

sourceSets {
    main {
        resources {
            srcDir(layout.buildDirectory.dir("smithy-aws-traits"))
        }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

application {
    mainClass = "software.amazon.smithy.java.cli.SmithyCallRunner"
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
            "--enable-url-protocols=http,https",
        ))

        // Debug info
        verbose.set(true)

        // Image configuration
        imageName.set("smithy-call")
        mainClass.set(application.mainClass)

        // Determines if image is a shared library [note: defaults to true if java-library plugin is applied]
        sharedLibrary.set(false)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}


