plugins {
    `java-library`
    id("software.amazon.smithy.gradle.smithy-base")
    application
}

application {
    mainClass = "software.amazon.smithy.java.server.example.BasicServerExample"
}

dependencies {
    val smithyJavaVersion: String by project

    smithyBuild("software.amazon.smithy.java:codegen-plugin:$smithyJavaVersion")
    smithyBuild("software.amazon.smithy.java:client-api:$smithyJavaVersion")
    smithyBuild("software.amazon.smithy.java:server-api:$smithyJavaVersion")

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

// Add generated Java sources to the main sourceset
afterEvaluate {
    val codegenPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-codegen")
    sourceSets {
        main {
            java {
                srcDir(codegenPath)
            }
        }
        create("it") {
            compileClasspath += main.get().output + configurations["testRuntimeClasspath"] + configurations["testCompileClasspath"]
            runtimeClasspath += output + compileClasspath + test.get().runtimeClasspath + test.get().output
        }
    }
}

tasks {
    val smithyBuild by getting
    compileJava {
        dependsOn(smithyBuild)
    }

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
