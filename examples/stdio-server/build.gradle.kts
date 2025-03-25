plugins {
    `java-library`
    id("software.amazon.smithy.gradle.smithy-base")
    application
}

dependencies {
    val smithyJavaVersion: String by project

    smithyBuild("software.amazon.smithy.java.codegen:plugins:$smithyJavaVersion")
    smithyBuild("software.amazon.smithy.java:jsonrpc2-schema:$smithyJavaVersion")

    implementation("software.amazon.smithy.java:server-stdio:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:jsonrpc2-schema:$smithyJavaVersion")
    runtimeOnly("software.amazon.smithy.java:server-jsonrpc2:$smithyJavaVersion")
    testRuntimeOnly("software.amazon.smithy.java:server-jsonrpc2:$smithyJavaVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
    testImplementation("software.amazon.smithy.java:jsonrpc2-schema:$smithyJavaVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Use that application plugin to start the service via the `run` task.
application {
    mainClass = "software.amazon.smithy.java.server.example.StdioServerExample"
}

// Add generated Java files to the main sourceSet
afterEvaluate {
    val serverPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-server-codegen")
    sourceSets {
        main {
            java {
                srcDir(serverPath)
            }
        }
        create("it") {
            compileClasspath += main.get().output + configurations["testRuntimeClasspath"] + configurations["testCompileClasspath"]
            runtimeClasspath += output + compileClasspath + test.get().runtimeClasspath + test.get().output
        }

    }
}

tasks {
    compileJava {
        dependsOn(smithyBuild)
    }

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
