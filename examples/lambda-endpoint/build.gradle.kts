plugins {
    `java-library`
    id("software.amazon.smithy.gradle.smithy-base")
}

dependencies {
    val smithyJavaVersion: String by project

    annotationProcessor("com.google.auto.service:auto-service:1.1.1")
    compileOnly("com.google.auto.service:auto-service:1.1.1")

    smithyBuild("software.amazon.smithy.java.codegen:plugins:$smithyJavaVersion")

    implementation("software.amazon.smithy.java:lambda:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:aws-server-restjson:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:server-rpcv2-cbor:$smithyJavaVersion")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks {
    // Generate a zip that can be uploaded to the Lambda function.
    // It will be created here: `build/distributions/lambda-endpoint-0.0.1.zip`
    register<Zip>("buildZip") {
        into("lib") {
            from(jar)
            from(configurations.runtimeClasspath)
        }
    }
}

// Add generated Java files to the main sourceSet
afterEvaluate {
    val serverPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-server-codegen")
    println(serverPath.get())
    sourceSets {
        main {
            java {
                srcDir(serverPath)
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
