plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.test.logger.plugin)
    implementation(libs.spotbugs)
    implementation(libs.spotless)
    implementation(libs.smithy.gradle.base)

    // https://github.com/gradle/gradle/issues/15383
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
