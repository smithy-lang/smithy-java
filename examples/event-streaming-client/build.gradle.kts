plugins {
    id("software.amazon.smithy.java.gradle.smithy-java")
    id("smithy-java.jmh-conventions")
}

dependencies {
    val smithyJavaVersion: String by project

    implementation("software.amazon.smithy.java:aws-client-restjson:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:client-core:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:client-rpcv2-cbor:${smithyJavaVersion}")
    implementation(project(":framework-errors"))

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
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

jmh {
    warmupIterations = 2
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
