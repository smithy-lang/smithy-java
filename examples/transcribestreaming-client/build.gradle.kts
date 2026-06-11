plugins {
    `java-library`
    id("software.amazon.smithy.java.gradle.smithy-java")
}

dependencies {
    val smithyJavaVersion: String by project
    val smithyVersion = "1.71.0"

    implementation("software.amazon.api.models:transcribe-streaming:1.0.8")
    implementation("software.amazon.smithy.java:aws-client-restjson:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:client-core:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:aws-sigv4:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:client-rulesengine:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:aws-client-rulesengine:$smithyJavaVersion")
    implementation("org.slf4j:slf4j-simple:2.0.18")
    implementation("software.amazon.smithy:smithy-aws-endpoints:$smithyVersion")
    implementation("software.amazon.smithy:smithy-aws-smoke-test-model:$smithyVersion")
    implementation("software.amazon.smithy:smithy-aws-traits:$smithyVersion")

    // Test dependencies
    testImplementation("software.amazon.smithy.java:aws-sdkv2-auth:$smithyJavaVersion")
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
