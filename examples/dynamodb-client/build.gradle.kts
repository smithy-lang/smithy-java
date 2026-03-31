plugins {
    `java-library`
    id("software.amazon.smithy.gradle.smithy-base")
    id("me.champeau.jmh") version "0.7.3"
}

dependencies {
    val smithyJavaVersion: String by project

    smithyBuild("software.amazon.smithy.java:codegen-plugin:$smithyJavaVersion")
    smithyBuild("software.amazon.smithy.java:client-core:$smithyJavaVersion")

    implementation("software.amazon.smithy.java:aws-client-awsjson:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:client-core:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:aws-sigv4:$smithyJavaVersion")
}

// Add generated Java sources to the main sourceset
afterEvaluate {
    val clientPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-codegen").get()
    sourceSets {
        main {
            java {
                srcDir("$clientPath/java")
            }
            resources {
                srcDir("$clientPath/resources")
            }
        }
    }
}

tasks {
    compileJava {
        dependsOn(smithyBuild)
    }
}

jmh {
    warmupIterations = 2
    iterations = 5
    fork = 1
    // profilers.add("async:output=flamegraph")
    // profilers.add("gc")
}

// Helps Intellij IDE's discover smithy models
sourceSets {
    main {
        java {
            srcDir("model")
        }
    }
}

tasks.compileJava {
    dependsOn(tasks.smithyBuild)
}

tasks.processResources {
    dependsOn(tasks.compileJava)
}

repositories {
    mavenLocal()
    mavenCentral()
}
