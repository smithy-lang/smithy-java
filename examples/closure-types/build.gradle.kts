/**
 * Configuration shared by every subproject. The Smithy plugin itself is applied per
 * subproject, since each one is configured by its own `smithy-build.json`.
 */
subprojects {
    apply(plugin = "java-library")

    repositories {
        mavenLocal()
        mavenCentral()
    }

    the<JavaPluginExtension>().toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:6.1.3")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        "testImplementation"("org.assertj:assertj-core:3.27.7")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
