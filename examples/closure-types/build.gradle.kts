
plugins {
    `java-library`
    id("software.amazon.smithy.gradle.smithy-base")
}

dependencies {
    val smithyJavaVersion: String by project

    smithyBuild("software.amazon.smithy.java:codegen-plugin:$smithyJavaVersion")
    // Combined mode generates a server, so server-api must be on the codegen classpath (the plugin
    // validates it) and on the runtime classpath for the generated server.
    smithyBuild("software.amazon.smithy.java:server-api:$smithyJavaVersion")
    api("software.amazon.smithy.java:server-api:$smithyJavaVersion")
    // The RPC v2 CBOR server protocol the generated service is served with at runtime.
    implementation("software.amazon.smithy.java:server-rpcv2-cbor:$smithyJavaVersion")

    testImplementation("org.hamcrest:hamcrest:3.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

// Add the generated Java sources and resources to the main source set so they compile.
afterEvaluate {
    val generatedPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-codegen").get()
    sourceSets {
        main {
            java {
                srcDir("$generatedPath/java")
            }
            resources {
                srcDir("$generatedPath/resources")
            }
        }
    }
}

tasks {
    val smithyBuild by getting
    compileJava {
        dependsOn(smithyBuild)
    }
    processResources {
        dependsOn(smithyBuild)
    }
    withType<Test> {
        useJUnitPlatform()
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}
