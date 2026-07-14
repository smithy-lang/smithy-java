plugins {
    `java-library`
    id("software.amazon.smithy.java.gradle.smithy-java")
    application
    id("me.champeau.jmh")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    val smithyJavaVersion: String by project

    implementation("software.amazon.smithy.java:client-core:$smithyJavaVersion")
    api("software.amazon.smithy.java:aws-client-restjson:$smithyJavaVersion")

    // JMH benchmark dependencies
    jmhImplementation("software.amazon.smithy.java:client-mock-plugin:$smithyJavaVersion")
    jmhImplementation("software.amazon.smithy.java:cbor-codec:$smithyJavaVersion")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

application {
    mainClass = "software.amazon.smithy.java.example.ClientExample"
}

sourceSets {
    create("it") {
        compileClasspath += main.get().output + configurations["testRuntimeClasspath"] + configurations["testCompileClasspath"]
        runtimeClasspath += output + compileClasspath + test.get().runtimeClasspath + test.get().output
    }
}

tasks {
    val integ by registering(Test::class) {
        useJUnitPlatform()
        testClassesDirs = sourceSets["it"].output.classesDirs
        classpath = sourceSets["it"].runtimeClasspath
    }
}

jmh {
    warmupIterations = 4
}

repositories {
    mavenLocal()
    mavenCentral()
}
