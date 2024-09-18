import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP

plugins {
    id("smithy-java.module-conventions")
    alias(libs.plugins.jmh)
}

description = "This module provides the Logging functionality for Smithy java"

extra["displayName"] = "Smithy :: Java :: Logging"
extra["moduleName"] = "software.amazon.smithy.java.logging"

val testImplementation: Configuration by configurations.getting

val log4j2TestConfiguration: Configuration by configurations.creating {
    extendsFrom(testImplementation)
}

val slf4jTestConfiguration: Configuration by configurations.creating {
    extendsFrom(testImplementation)
}

val jclTestConfiguration: Configuration by configurations.creating {
    extendsFrom(testImplementation)
}

// These are declared here instead of the version catalog because we don't want other modules to depend on them.
val log4j2 = "2.12.4"
val slf4j = "2.0.13"
val logBack = "1.5.6"
val jcl = "1.3.2"

dependencies {
    compileOnly("org.apache.logging.log4j:log4j-api:$log4j2")
    compileOnly("org.slf4j:slf4j-api:$slf4j")
    compileOnly("commons-logging:commons-logging:$jcl")

    testCompileOnly("org.apache.logging.log4j:log4j-core:$log4j2")
    testCompileOnly("ch.qos.logback:logback-classic:$logBack")
    testCompileOnly("commons-logging:commons-logging:$jcl")

    log4j2TestConfiguration("org.apache.logging.log4j:log4j-core:$log4j2")

    slf4jTestConfiguration("ch.qos.logback:logback-classic:$logBack")
    slf4jTestConfiguration("org.slf4j:slf4j-api:$slf4j")

    jclTestConfiguration("commons-logging:commons-logging:$jcl")
}

tasks.named<Test>("test") {
    exclude("**/*IsolatedTest*")
}

val log4j2Test =
    tasks.register<Test>("log4j2Test") {
        description = "Tests if InternalLogger successfully uses Log4j2 if found on classpath"
        group = VERIFICATION_GROUP
        include("**/*Log4j2LoggerIsolatedTest*")
        classpath += log4j2TestConfiguration
    }

val slf4jTest =
    tasks.register<Test>("slf4jTest") {
        description = "Tests if InternalLogger successfully uses Slf4j if found on classpath"
        group = VERIFICATION_GROUP
        include("**/*Slf4jLoggerIsolatedTest*")
        classpath += slf4jTestConfiguration
    }

val jclTest =
    tasks.register<Test>("jclTest") {
        description = "Tests if InternalLogger successfully uses JCL if found on classpath"
        group = VERIFICATION_GROUP
        include("**/*JclLoggerIsolatedTest*")
        classpath += jclTestConfiguration
    }

tasks.build {
    dependsOn(log4j2Test, slf4jTest, jclTest)
}
