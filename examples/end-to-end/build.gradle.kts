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

    smithyBuild("software.amazon.smithy.java:plugins:$smithyJavaVersion")

    // Server dependencies
    implementation("software.amazon.smithy.java:server-netty:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:aws-server-restjson:$smithyJavaVersion")

    // Client dependencies
    implementation("software.amazon.smithy.java:aws-client-restjson:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:client-core:$smithyJavaVersion")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Add generated Java sources to the main sourceset
afterEvaluate {
    val clientPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-client-codegen")
    val serverPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-server-codegen")
    sourceSets {
        main {
            java {
                srcDir(clientPath)
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
