plugins {
    id("software.amazon.smithy.java.gradle.smithy-java")
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    val smithyJavaVersion: String by project

    implementation("software.amazon.smithy.java:server-netty:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:aws-server-restjson:$smithyJavaVersion")
}

// Use that application plugin to start the service via the `run` task.
application {
    mainClass = "software.amazon.smithy.java.server.example.BasicServerExample"
}

// Helps Intellij IDE's discover smithy models
sourceSets {
    main {
        java {
            srcDir("model")
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}
