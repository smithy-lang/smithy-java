plugins {
    id("software.amazon.smithy.java.gradle.smithy-java")
}

dependencies {
    val smithyJavaVersion: String by project

    annotationProcessor("com.google.auto.service:auto-service:1.1.1")
    compileOnly("com.google.auto.service:auto-service:1.1.1")

    implementation("software.amazon.smithy.java:aws-lambda-endpoint:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:server-api:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:aws-server-restjson:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:server-rpcv2-cbor:$smithyJavaVersion")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
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
