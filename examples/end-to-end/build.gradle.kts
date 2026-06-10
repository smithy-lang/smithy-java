plugins {
    id("software.amazon.smithy.java.gradle.smithy-java")
    application
}

application {
    mainClass = "software.amazon.smithy.java.server.example.BasicServerExample"
}

dependencies {
    val smithyJavaVersion: String by project

    // Server dependencies
    implementation("software.amazon.smithy.java:server-netty:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:aws-server-restjson:$smithyJavaVersion")

    // Client dependencies
    implementation("software.amazon.smithy.java:aws-client-restjson:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:client-core:$smithyJavaVersion")

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

repositories {
    mavenLocal()
    mavenCentral()
}

java.sourceSets["main"].java {
    srcDirs("model", "src/main/smithy")
}
