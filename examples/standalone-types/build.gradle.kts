plugins {
    `java-library`
    id("software.amazon.smithy.java.gradle.smithy-java")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    testImplementation("org.hamcrest:hamcrest:3.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

repositories {
    mavenLocal()
    mavenCentral()
}
