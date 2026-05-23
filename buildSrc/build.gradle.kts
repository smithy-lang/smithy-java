plugins {
    `kotlin-dsl`
    `java-library`
}

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.test.logger.plugin)
    implementation(libs.spotbugs)
    implementation(libs.spotless)
    implementation(libs.smithy.gradle.base)
    implementation(libs.dependency.analysis)

    // https://github.com/gradle/gradle/issues/15383
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}
