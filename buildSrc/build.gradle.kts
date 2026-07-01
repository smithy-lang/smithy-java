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
    implementation(libs.pitest.gradle.plugin)
    implementation(libs.jmh.gradle.plugin)
    implementation(libs.shadow.gradle.plugin)

    // https://github.com/gradle/gradle/issues/15383
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}
