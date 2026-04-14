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
    val smithyJavaVersion: String by project

    implementation("software.amazon.smithy.java:aws-client-restjson:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:client-core:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:client-rpcv2-cbor:${smithyJavaVersion}")
    implementation("software.amazon.smithy.java:framework-errors:$smithyJavaVersion")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
