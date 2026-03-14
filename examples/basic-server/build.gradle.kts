plugins {
    `java-library`
    id("software.amazon.smithy.gradle.smithy-base")
    application
}

dependencies {
    val smithyJavaVersion: String by project

    smithyBuild("software.amazon.smithy.java:codegen-plugin:$smithyJavaVersion")
    smithyBuild("software.amazon.smithy.java:server-api:$smithyJavaVersion")

    implementation("software.amazon.smithy.java:server-netty:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:aws-server-restjson:$smithyJavaVersion")
}

// Use that application plugin to start the service via the `run` task.
application {
    mainClass = "software.amazon.smithy.java.server.example.BasicServerExample"
}

// Add generated Java files to the main sourceSet
afterEvaluate {
    val serverPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-codegen").get()
    sourceSets {
        main {
            java {
                srcDir("$serverPath/java")
            }
        }
    }
}

tasks {
    compileJava {
        dependsOn(smithyBuild)
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
